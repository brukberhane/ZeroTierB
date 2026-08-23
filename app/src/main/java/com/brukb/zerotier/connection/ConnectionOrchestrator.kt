package com.brukb.zerotier.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.VpnService
import android.util.Log
import com.brukb.zerotier.data.AppPreferences
import com.brukb.zerotier.data.LinkProfileRepository
import com.brukb.zerotier.data.NetworkRepository
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.LinkProfile
import com.brukb.zerotier.proxy.ProxyModeService
import com.brukb.zerotier.proxy.SystemProxyManager
import com.brukb.zerotier.vpn.ZerotierBVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var lastApplied: RuntimePlan? = null

    private val _state = MutableStateFlow(OrchestratorState())
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    suspend fun refresh() {
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
        if (plan == lastApplied) {
            Log.i(TAG, "plan unchanged: ${plan.reason}")
            return
        }
        Log.i(TAG, "apply ${plan.runtime}: ${plan.reason}")
        _state.value = _state.value.copy(isApplying = true, plan = plan)
        try {
            when (plan.runtime) {
                Runtime.OFF -> applyOff()
                Runtime.PROXY -> applyProxy(plan)
                Runtime.VPN -> applyVpn(plan)
            }
            lastApplied = plan
            _state.value = _state.value.copy(isApplying = false, lastError = null)
        } catch (e: Exception) {
            Log.e(TAG, "apply failed: ${plan.reason}", e)
            _state.value = _state.value.copy(
                isApplying = false,
                lastError = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    private suspend fun applyOff() {
        if (ZerotierBVpnService.state.value.isRunning) {
            stopVpnLocked()
        }
        if (ProxyModeService.state.value.isRunning) {
            stopProxyLocked()
        }
    }

    private suspend fun applyProxy(plan: RuntimePlan) {
        if (ZerotierBVpnService.state.value.isRunning) {
            stopVpnLocked()
        }
        if (!ProxyModeService.state.value.isRunning) {
            ProxyModeService.start(context, joinNetworkIds = plan.joinNetworkIds)
        }
    }

    private suspend fun applyVpn(plan: RuntimePlan) {
        SystemProxyManager(context, preferences).disable()
        if (ProxyModeService.state.value.isRunning) {
            stopProxyLocked()
        }
        val vpnId = plan.vpnNetworkId ?: return
        val vpnRunning = ZerotierBVpnService.state.value.isRunning
        val appliedId = lastApplied?.vpnNetworkId
        if (vpnRunning && appliedId != vpnId) {
            stopVpnLocked()
        }
        if (!ZerotierBVpnService.state.value.isRunning) {
            ZerotierBVpnService.start(context, singleNetworkId = vpnId)
        }
    }

    private suspend fun stopProxyLocked() {
        ProxyModeService.stopAndAwait(context)
    }

    private suspend fun stopVpnLocked() {
        ZerotierBVpnService.stopAndAwait(context)
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
    }
}
