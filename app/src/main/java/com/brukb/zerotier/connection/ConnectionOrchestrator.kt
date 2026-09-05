package com.brukb.zerotier.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.VpnService
import com.brukb.zerotier.log.AppLog
import com.brukb.zerotier.data.AppPreferences
import com.brukb.zerotier.data.LinkProfileRepository
import com.brukb.zerotier.data.LivePlanetResolver
import com.brukb.zerotier.data.NetworkRepository
import com.brukb.zerotier.data.RootsFingerprint
import com.brukb.zerotier.data.RootsRepository
import com.brukb.zerotier.data.RootsRestart
import com.brukb.zerotier.data.buildRootsFingerprint
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.LinkProfile
import com.brukb.zerotier.proxy.ProxyModeService
import com.brukb.zerotier.proxy.SystemProxyManager
import com.brukb.zerotier.system.ProxyHealthJob
import com.brukb.zerotier.system.ProxyHealthPolicy
import com.brukb.zerotier.vpn.ZerotierBVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramSocket
import java.net.InetSocketAddress

data class OrchestratorState(
    val plan: RuntimePlan? = null,
    val lastLink: PhysicalLink? = null,
    val isApplying: Boolean = false,
    val lastError: String? = null,
)

class ConnectionOrchestrator(
    private val context: Context,
    private val preferences: AppPreferences,
    private val networkRepository: NetworkRepository,
    private val linkProfileRepository: LinkProfileRepository,
    private val rootsRepository: RootsRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var lastApplied: RuntimePlan? = null
    private var lastRootsFp: RootsFingerprint? = null

    @Volatile
    var startAllowed: Boolean = false
        private set

    private val _state = MutableStateFlow(OrchestratorState())
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    suspend fun refresh(syncJob: Boolean = true) {
        startAllowed = true
        if (syncJob) syncHealthJob()
        val globalMode = preferences.globalMode.first()
        val enabled = networkRepository.getAll().filter { it.isEnabled }
        val link = classifyLink()
        _state.value = _state.value.copy(lastLink = link)
        val vpnConsentGranted = VpnService.prepare(context) == null
        val plan = RuntimePlanResolver.resolve(globalMode, link, vpnConsentGranted, enabled)
        applyPlan(plan)
    }

    suspend fun applyGlobalMode(mode: GlobalMode) {
        preferences.setGlobalMode(mode)
        refresh()
    }

    suspend fun applyPlan(plan: RuntimePlan) = mutex.withLock {
        applyLocked(plan)
    }

    suspend fun stopAll() = mutex.withLock {
        applyLocked(manualOffPlan())
    }

    private suspend fun syncHealthJob() {
        val mode = preferences.globalMode.first()
        if (ProxyHealthPolicy.shouldSchedule(mode)) {
            ProxyHealthJob.schedule(context)
        } else {
            ProxyHealthJob.cancel(context)
        }
    }

    suspend fun invalidateAppliedPlan() {
        mutex.withLock { lastApplied = null }
    }

    private suspend fun classifyLink(): PhysicalLink {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lookup = object : LinkModeLookup {
            override suspend fun modeForSsid(ssid: String): LinkMode? =
                linkProfileRepository.getBySsid(ssid)?.mode

            override suspend fun modeForSubscription(subscriptionId: Int): LinkMode =
                linkProfileRepository.getBySubscriptionId(subscriptionId)?.mode ?: LinkMode.PROXY

            override suspend fun modeForOther(): LinkMode =
                linkProfileRepository.getById(LinkProfile.OTHER_ID)?.mode ?: LinkMode.PROXY
        }
        return LinkClassifier(context, connectivityManager, lookup)
            .classify(dataSubscriptionId = activeDataSubscriptionId())
    }

    private fun activeDataSubscriptionId(): Int? = DataSubscriptionIds.activeOrNull()

    private suspend fun applyLocked(plan: RuntimePlan) {
        val fp = currentRootsFingerprint()
        if (plan == lastApplied && runtimeMatches(plan) && fp == lastRootsFp) {
            AppLog.i(TAG, "plan unchanged: ${plan.reason}")
            return
        }
        AppLog.i(TAG, "apply ${plan.runtime}: ${plan.reason}")
        _state.value = _state.value.copy(isApplying = true, plan = plan)
        try {
            when (plan.runtime) {
                Runtime.OFF -> applyOff()
                Runtime.PROXY -> applyProxy(plan, fp)
                Runtime.VPN -> applyVpn(plan, fp)
            }
            lastApplied = plan
            lastRootsFp = if (plan.runtime == Runtime.OFF) null else fp
            _state.value = _state.value.copy(isApplying = false, lastError = null)
        } catch (e: Exception) {
            // lastApplied intentionally left stale: the next refresh retries
            // instead of no-oping on a plan that never became reality.
            AppLog.e(TAG, "apply failed: ${plan.reason}", e)
            _state.value = _state.value.copy(
                isApplying = false,
                lastError = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    /**
     * True when the live stack state (including in-flight starts) already
     * matches what the plan wants. Guards the [lastApplied] short-circuit so
     * a crashed or refused service does not make a plan sticky.
     */
    private fun runtimeMatches(plan: RuntimePlan): Boolean {
        val vpnActive = ZerotierBVpnService.state.value.isRunning || ZerotierBVpnService.startRequested
        val proxyActive = ProxyModeService.state.value.isRunning || ProxyModeService.startRequested
        return when (plan.runtime) {
            Runtime.OFF -> !vpnActive && !proxyActive
            Runtime.PROXY -> proxyActive && !vpnActive
            Runtime.VPN -> vpnActive && !proxyActive
        }
    }

    private suspend fun applyOff() {
        SystemProxyManager(context, preferences).disable()
        stopProxyStack()
        stopVpnStack()
    }

    private suspend fun applyProxy(plan: RuntimePlan, fp: RootsFingerprint) {
        stopVpnStack()
        awaitUdpPortReleased(VPN_UDP_PORT)
        val proxyActive = ProxyModeService.state.value.isRunning || ProxyModeService.startRequested
        if (proxyJoinSetRequiresRestart(proxyActive, lastApplied?.joinNetworkIds, plan.joinNetworkIds)) {
            AppLog.i(TAG, "proxy join set changed — restarting")
            stopProxyStack()
            awaitUdpPortReleased(LIBZT_UDP_PORT)
        } else if (RootsRestart.requiresRestart(proxyActive, lastRootsFp, fp)) {
            AppLog.i(TAG, "roots config changed — restarting proxy")
            stopProxyStack()
            awaitUdpPortReleased(LIBZT_UDP_PORT)
        }
        if (!ProxyModeService.state.value.isRunning) {
            ProxyModeService.start(context, joinNetworkIds = plan.joinNetworkIds)
            if (!awaitProxyStarted()) {
                throw IllegalStateException(
                    ProxyModeService.state.value.lastError ?: "Proxy service did not start",
                )
            }
        } else {
            // Already up (including Doze-paused). Poke START so the service
            // resumes the node; do not increment the start token.
            ProxyModeService.start(context, joinNetworkIds = plan.joinNetworkIds)
        }
    }

    private suspend fun applyVpn(plan: RuntimePlan, fp: RootsFingerprint) {
        SystemProxyManager(context, preferences).disable()
        stopProxyStack()
        awaitUdpPortReleased(LIBZT_UDP_PORT)
        val vpnId = plan.vpnNetworkId ?: return
        val vpnActive = ZerotierBVpnService.state.value.isRunning || ZerotierBVpnService.startRequested
        if (vpnActive && lastApplied?.vpnNetworkId != vpnId) {
            stopVpnStack()
        } else if (RootsRestart.requiresRestart(vpnActive, lastRootsFp, fp)) {
            AppLog.i(TAG, "roots config changed — restarting VPN")
            stopVpnStack()
        }
        if (!ZerotierBVpnService.state.value.isRunning) {
            ZerotierBVpnService.start(context, singleNetworkId = vpnId)
            if (!awaitVpnStarted()) {
                throw IllegalStateException(
                    ZerotierBVpnService.state.value.statusMessage
                        .ifBlank { "VPN service did not start" },
                )
            }
        }
    }

    private suspend fun stopProxyStack() {
        SystemProxyManager(context, preferences).disable()
        if (!ProxyModeService.stopAndAwait(context)) {
            val st = ProxyModeService.state.value
            AppLog.e(
                TAG,
                "Proxy did not stop in time running=${st.isRunning} startRequested=" +
                    "${ProxyModeService.startRequested} status=${st.statusMessage}",
            )
            throw IllegalStateException("Proxy did not stop in time — aborting swap")
        }
    }

    private suspend fun stopVpnStack() {
        if (!ZerotierBVpnService.stopAndAwait(context)) {
            throw IllegalStateException("VPN did not stop in time — aborting swap")
        }
    }

    private suspend fun awaitProxyStarted(timeoutMs: Long = 15_000): Boolean {
        val outcome = withTimeoutOrNull(timeoutMs) {
            ProxyModeService.state.first { it.isRunning || !ProxyModeService.startRequested }
        } ?: return false
        return outcome.isRunning
    }

    private suspend fun awaitVpnStarted(timeoutMs: Long = 15_000): Boolean {
        val outcome = withTimeoutOrNull(timeoutMs) {
            ZerotierBVpnService.state.first { it.isRunning || !ZerotierBVpnService.startRequested }
        } ?: return false
        return outcome.isRunning
    }

    /**
     * Spec §7.2: never start the other stack while the previous one may still
     * hold its UDP port (libzt: 9993, JNI VPN: 9994). Two live sockets with
     * the same identity split the node's paths and the VPN join never leaves
     * REQUESTING_CONFIGURATION.
     */
    private suspend fun awaitUdpPortReleased(port: Int, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isUdpPortFree(port)) {
                AppLog.i(TAG, "UDP port $port free")
                return
            }
            delay(200)
        }
        throw IllegalStateException("UDP port $port still busy — previous stack not fully stopped")
    }

    private fun isUdpPortFree(port: Int): Boolean {
        val socket = DatagramSocket(null)
        return try {
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(port))
            true
        } catch (e: Exception) {
            false
        } finally {
            runCatching { socket.close() }
        }
    }

    private suspend fun currentRootsFingerprint(): RootsFingerprint {
        val airgap = preferences.airgap.first()
        val latch = preferences.airgapWithoutMoons.first()
        val planetSource = preferences.planetSource.first()
        val moons = rootsRepository.getMoons()
        val customPresent = rootsRepository.customPlanetPresent()
        val decision = LivePlanetResolver.resolve(
            airgap = airgap,
            airgapWithoutMoons = latch,
            planetSource = planetSource,
            moonCount = moons.size,
            customPlanetPresent = customPresent,
        )
        return buildRootsFingerprint(
            source = decision.source,
            moons = moons,
            customStamp = rootsRepository.customPlanetLastModified(),
        )
    }

    private fun manualOffPlan(): RuntimePlan =
        RuntimePlan(
            runtime = Runtime.OFF,
            reason = "manual stop",
            vpnNetworkId = null,
            joinNetworkIds = emptyList(),
            vpnConsentMissing = false,
        )

    companion object {
        private const val TAG = "ConnectionOrchestrator"
        private const val LIBZT_UDP_PORT = 9993
        private const val VPN_UDP_PORT = 9994

        /**
         * PROXY joins every enabled net. A live proxy with a different join
         * set must restart (same pattern as VPN main-net change). `lastJoin`
         * null (stale lastApplied) also restarts so grant/self-heal re-applies.
         */
        fun proxyJoinSetRequiresRestart(
            proxyRunning: Boolean,
            lastJoinNetworkIds: List<String>?,
            nextJoinNetworkIds: List<String>,
        ): Boolean = proxyRunning && lastJoinNetworkIds != nextJoinNetworkIds
    }
}
