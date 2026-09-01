package com.brukb.zerotier.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.brukb.zerotier.R
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.connection.NodeLifecycleStatus
import com.brukb.zerotier.connection.ztNetworkToRuntime
import com.brukb.zerotier.connection.ztNodeStateToLifecycle
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.dns.AndroidUplinkDnsClient
import com.brukb.zerotier.proxy.dns.DnsResolver
import com.brukb.zerotier.proxy.http.HttpProxyServer
import com.brukb.zerotier.system.IdleGate
import com.brukb.zerotier.system.ProxyHealthJob
import com.brukb.zerotier.system.ProxyWatchdog
import com.brukb.zerotier.ui.MainActivity
import com.brukb.zerotier.vpn.ZerotierBVpnService
import com.brukb.zerotier.ztlib.ZeroTierNodeManager
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import com.brukb.zerotier.ztlib.ZtNodeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ProxyModeService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)

    private lateinit var nodeManager: ZeroTierNodeManager
    private lateinit var routeResolver: RouteResolver
    private lateinit var dnsResolver: DnsResolver
    private lateinit var systemProxyManager: SystemProxyManager
    private val networkConfigs = mutableMapOf<Long, ZerotierBNetwork>()
    private var httpProxy: HttpProxyServer? = null
    private var nodeStarted = false
    private var networkStateJob: kotlinx.coroutines.Job? = null
    private var healthJob: kotlinx.coroutines.Job? = null
    private val startStopMutex = Mutex()
    private lateinit var idleGate: IdleGate
    private val recovering = AtomicBoolean(false)
    private val fgsTimeoutHandled = AtomicBoolean(false)
    private var nodePausedForDoze = false
    private val pausedNetworks = mutableListOf<ZerotierBNetwork>()
    private val lastAppliedRuntime = mutableMapOf<Long, ZtNetworkStatus>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        routeResolver = RouteResolver()
        dnsResolver = DnsResolver(AndroidUplinkDnsClient(this))
        nodeManager = ZeroTierNodeManager(filesDir.absolutePath)
        systemProxyManager = SystemProxyManager(this, (application as ZerotierBApplication).preferences)
        idleGate = IdleGate(this) { _, deviceIdle ->
            // Resume on screen-on even if isDeviceIdleMode still true for a beat.
            // allowPeriodicWork requires !idle, so SCREEN_ON-during-Doze used to
            // call onBecameIdle again and never resume.
            if (idleGate.isInteractive) {
                scope.launch { onBecameInteractive() }
            } else {
                onBecameIdle(deviceIdle)
            }
        }
        idleGate.register()
        scope.launch {
            updateState { copy(hasSecureSettingsPermission = systemProxyManager.hasPermission()) }
        }
        scope.launch {
            (application as ZerotierBApplication).preferences.dnsFailOpen.collect { open ->
                dnsResolver.failOpen = open
                Log.i(TAG, "dns failOpen=$open")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                startStopMutex.withLock { stopProxy() }
            }
            return START_NOT_STICKY
        }
        // FGS 5s rule: sticky/null restarts also come in as startForegroundService.
        startForegroundCompat(buildNotification(_state.value.httpProxyPort ?: 0))
        fgsTimeoutHandled.set(false)
        when (intent?.action) {
            ACTION_START -> {
                if (_state.value.isRunning) {
                    scope.launch { resumeFromIdleIfNeeded() }
                } else {
                    updateState {
                        copy(
                            statusMessage = "Starting proxy...",
                            nodeLifecycle = NodeLifecycleStatus.STARTING,
                            networkStatuses = emptyList(),
                        )
                    }
                    scope.launch {
                        try {
                            startStopMutex.withLock {
                                startProxy(
                                    forceDebug = intent.getBooleanExtra(EXTRA_FORCE_DEBUG, false),
                                    joinNetworkIds = joinIds(intent),
                                    startToken = intent.getLongExtra(EXTRA_START_TOKEN, 0L),
                                )
                            }
                        } finally {
                            markStartFinished()
                        }
                    }
                }
            }
            else -> {
                val app = application as ZerotierBApplication
                app.applicationScope.launch { app.orchestrator.refresh() }
            }
        }
        return START_STICKY
    }

    private fun joinIds(intent: Intent): List<String>? =
        intent.getStringArrayExtra(EXTRA_JOIN_NETWORK_IDS)?.toList()

    override fun onBind(intent: Intent?): IBinder? = null

    @Deprecated("Deprecated in Java")
    override fun onTimeout(startId: Int) {
        handleFgsTimeout("foreground service timeout")
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleFgsTimeout("foreground service timeout type=$fgsType")
    }

    /**
     * AOSP caps only dataSync/mediaProcessing at 6h/24h. specialUse has no
     * published cap, but OEMs still call this. Must stopSelf within seconds.
     * Restart goes through [ProxyHealthJob.scheduleRestart], not a same-call
     * startForegroundService.
     */
    private fun handleFgsTimeout(reason: String) {
        if (!fgsTimeoutHandled.compareAndSet(false, true)) return
        Log.w(TAG, reason)
        failClosedAndStop(reason)
        val app = application as ZerotierBApplication
        app.applicationScope.launch {
            app.orchestrator.invalidateAppliedPlan()
            ProxyHealthJob.scheduleRestart(app)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (httpProxy?.isListening != true) {
            systemProxyManager.disableBlocking()
        }
    }

    override fun onDestroy() {
        runCatching { idleGate.unregister() }
        stopHealthLoop()
        ProxyWatchdog.stop(this)
        systemProxyManager.disableBlocking()
        // serviceJob is cancelled right after; run the final cleanup on a
        // detached scope so the node stop still runs.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            startStopMutex.withLock { stopProxy() }
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun startProxy(forceDebug: Boolean, joinNetworkIds: List<String>? = null, startToken: Long = 0L) {
        if (isStartSuperseded(startToken)) {
            Log.i(TAG, "Start superseded by stop — not starting proxy")
            updateState { copy(isRunning = false, statusMessage = "Stopped", nodeLifecycle = NodeLifecycleStatus.STOPPED) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (ZerotierBVpnService.state.value.isRunning && !forceDebug) {
            markStopped()
            updateState {
                copy(
                    isRunning = false,
                    lastError = "VPN active — stop VPN before proxy",
                    statusMessage = "Refused: VPN active",
                    nodeLifecycle = NodeLifecycleStatus.STOPPED,
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val app = application as ZerotierBApplication
        var enabledNetworks = app.networkRepository.getAll().filter { it.isEnabled }
        if (joinNetworkIds != null) {
            val allowed = joinNetworkIds.map { ZerotierBNetwork.normalizeNetworkId(it) }.toSet()
            enabledNetworks = enabledNetworks.filter { it.networkId in allowed }
        }
        if (enabledNetworks.isEmpty()) {
            fail("No enabled networks configured")
            return
        }

        nodeManager.initialize().onFailure {
            fail(it.message ?: "Node init failed")
            return
        }
        startNetworkStatusPump()
        nodeManager.start(shouldAbort = { isStartSuperseded(startToken) }).onFailure {
            if (isStartSuperseded(startToken)) {
                Log.i(TAG, "Node start aborted by stop")
                runCatching { nodeManager.stop() }
                finishSupersededStart()
                return
            }
            fail(it.message ?: "Node start failed")
            return
        }
        nodeStarted = true
        if (isStartSuperseded(startToken)) {
            Log.i(TAG, "Start superseded after node start — stopping")
            stopProxy()
            return
        }

        val nodeId = ZeroTierNodeManager.formatNodeId(nodeManager.state.value.nodeId ?: 0L)
        updateState {
            copy(
                nodeId = nodeId,
                statusMessage = "Waiting for roots",
                nodeLifecycle = ztNodeStateToLifecycle(nodeManager.state.value, pausedDoze = false),
            )
        }
        Log.i(TAG, "Node started: $nodeId online=${nodeManager.state.value.isOnline}")

        networkConfigs.clear()
        routeResolver.clear()
        dnsResolver.clear()
        lastAppliedRuntime.clear()

        httpProxy = HttpProxyServer(0, routeResolver, dnsResolver, onDied = { onListenDied() }).also { it.start() }
        val boundPort = httpProxy?.boundPort ?: -1
        if (boundPort <= 0) {
            fail("HTTP proxy bind failed")
            return
        }

        app.preferences.setLastHttpProxyPort(boundPort)
        updateState {
            copy(
                isRunning = true,
                httpProxyPort = boundPort,
                statusMessage = "Proxy on 127.0.0.1:$boundPort",
            )
        }
        Log.i(TAG, "HTTP proxy on 127.0.0.1:$boundPort")

        systemProxyManager.enable(boundPort)
            .onSuccess {
                updateState { copy(systemProxyActive = true, hasSecureSettingsPermission = true) }
                Log.i(TAG, "System proxy set to 127.0.0.1:$boundPort")
            }
            .onFailure {
                updateState { copy(systemProxyActive = false) }
                Log.w(TAG, "System proxy not set: ${it.message}")
            }

        startHealthLoop()
        startWatchdogIfEnabled()
        startForegroundCompat(buildNotification(boundPort))

        for (network in enabledNetworks) {
            if (isStartSuperseded(startToken)) {
                Log.i(TAG, "Start superseded by stop during join — aborting")
                stopProxy()
                return
            }
            joinConfiguredNetwork(network, startToken)
        }
    }

    private suspend fun joinConfiguredNetwork(network: ZerotierBNetwork, startToken: Long) {
        val networkId = network.networkIdLong()
        networkConfigs[networkId] = network
        val joinResult = nodeManager.join(networkId, network)
        if (joinResult.isFailure) {
            Log.w(TAG, "Join failed for ${network.networkId}: ${joinResult.exceptionOrNull()?.message}")
            return
        }
        val readyResult = nodeManager.waitForNetworkReady(
            networkId,
            timeoutMs = JOIN_READY_TIMEOUT_MS,
            shouldAbort = { isStartSuperseded(startToken) },
        )
        if (isStartSuperseded(startToken)) return
        if (readyResult.isFailure) {
            Log.w(TAG, "Network not ready ${network.networkId}: ${readyResult.exceptionOrNull()?.message}")
            return
        }
        applyNetworkRuntime(network, readyResult.getOrThrow())
        Log.i(TAG, "Joined ${network.networkId}")
    }

    private fun startNetworkStatusPump() {
        if (networkStateJob?.isActive == true) return
        networkStateJob = scope.launch {
            launch {
                nodeManager.state.collect { nodeState ->
                    publishFromNodeState(nodeState)
                    applyOkRoutes(nodeState)
                }
            }
            while (isActive) {
                delay(NETWORK_STATUS_POLL_MS)
                if (nodePausedForDoze || !nodeStarted) continue
                for (id in networkConfigs.keys.toList()) {
                    runCatching { nodeManager.refreshNetworkInfo(id) }
                }
            }
        }
    }

    private fun publishFromNodeState(nodeState: ZtNodeState) {
        if (nodePausedForDoze) return
        val lifecycle = ztNodeStateToLifecycle(nodeState, pausedDoze = false)
        val formattedId = nodeState.nodeId
            ?.takeIf { it != 0L }
            ?.let { ZeroTierNodeManager.formatNodeId(it) }
        val statuses = nodeState.networks.map { (id, zt) -> ztNetworkToRuntime(id, zt) }
        updateState {
            val wentOffline = nodeLifecycle == NodeLifecycleStatus.ONLINE &&
                lifecycle == NodeLifecycleStatus.STARTING
            val cameOnline = nodeLifecycle != NodeLifecycleStatus.ONLINE &&
                lifecycle == NodeLifecycleStatus.ONLINE
            val nextMessage = when {
                lifecycle == NodeLifecycleStatus.ERROR && !nodeState.lastError.isNullOrBlank() ->
                    nodeState.lastError
                wentOffline -> "Node offline — waiting for roots"
                cameOnline && (httpProxyPort == null || httpProxyPort <= 0) && formattedId != null ->
                    "Node online: $formattedId"
                else -> statusMessage
            }
            copy(
                nodeLifecycle = lifecycle,
                nodeId = formattedId ?: nodeId,
                networkStatuses = statuses,
                statusMessage = nextMessage,
            )
        }
    }

    private fun applyOkRoutes(nodeState: ZtNodeState) {
        for ((networkId, status) in nodeState.networks) {
            if (status.status != ZtNetworkStatus.Status.OK) {
                lastAppliedRuntime.remove(networkId)
                continue
            }
            val config = networkConfigs[networkId] ?: continue
            if (lastAppliedRuntime[networkId] == status) continue
            lastAppliedRuntime[networkId] = status
            applyNetworkRuntime(config, status)
        }
    }

    private fun finishSupersededStart() {
        nodeStarted = false
        networkStateJob?.cancel()
        networkStateJob = null
        updateState {
            copy(
                isRunning = false,
                statusMessage = "Stopped",
                nodeLifecycle = NodeLifecycleStatus.STOPPED,
                networkStatuses = emptyList(),
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun applyNetworkRuntime(network: ZerotierBNetwork, status: ZtNetworkStatus) {
        Log.i(
            TAG,
            "routes ${network.networkId}: assigned=${status.assignedAddresses} managed=${status.routes}",
        )
        routeResolver.updateNetwork(network, status)
        if (network.allowDns) {
            dnsResolver.updateNetwork(network, status)
        } else {
            dnsResolver.removeNetwork(network.networkIdLong())
        }
    }

    private suspend fun stopProxy() {
        markStopped()
        updateState { copy(statusMessage = "Stopping...") }
        stopHealthLoop()
        ProxyWatchdog.stop(this)
        systemProxyManager.disable().onFailure {
            Log.w(TAG, "Failed to restore system proxy: ${it.message}")
        }
        networkStateJob?.cancel()
        networkStateJob = null
        httpProxy?.stop()
        httpProxy = null
        for (network in networkConfigs.values.toList()) {
            nodeManager.leave(network.networkIdLong())
        }
        if (nodeStarted || nodePausedForDoze) {
            nodeManager.stop()
            nodeStarted = false
            nodePausedForDoze = false
        }
        pausedNetworks.clear()
        lastAppliedRuntime.clear()
        routeResolver.clear()
        dnsResolver.clear()
        networkConfigs.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        updateState { ProxyServiceState(hasSecureSettingsPermission = systemProxyManager.hasPermission()) }
        stopSelf()
    }

    private suspend fun fail(message: String) {
        Log.e(TAG, message)
        updateState {
            copy(
                lastError = message,
                statusMessage = message,
                isRunning = false,
                nodeLifecycle = NodeLifecycleStatus.ERROR,
                networkStatuses = emptyList(),
            )
        }
        stopProxy()
    }

    private fun updateState(block: ProxyServiceState.() -> ProxyServiceState) {
        _state.value = _state.value.block()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_proxy_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(port: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (port > 0) {
            getString(R.string.notification_proxy_text, port)
        } else {
            getString(R.string.notification_text)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun onListenDied() {
        if (!recovering.compareAndSet(false, true)) return
        scope.launch {
            try {
                recoverProxy()
            } finally {
                recovering.set(false)
            }
        }
    }

    private suspend fun recoverProxy() {
        val port = httpProxy?.boundPort ?: 0
        val alive = httpProxy != null && httpProxy?.isListening == true && port > 0 && probeTcp(port)
        if (alive) return
        Log.w(TAG, "Proxy listen dead; clearing system proxy")
        systemProxyManager.disableBlocking()
        httpProxy?.stop()
        httpProxy = null
        updateState { copy(httpProxyPort = null, systemProxyActive = false) }
        if (!_state.value.isRunning) {
            notifyProxyDown()
            return
        }
        runCatching {
            httpProxy = HttpProxyServer(0, routeResolver, dnsResolver, onDied = { onListenDied() }).also { it.start() }
            val boundPort = httpProxy?.boundPort ?: -1
            if (boundPort <= 0) error("HTTP proxy rebind failed")
            val app = application as ZerotierBApplication
            app.preferences.setLastHttpProxyPort(boundPort)
            updateState { copy(httpProxyPort = boundPort, statusMessage = "Proxy on 127.0.0.1:$boundPort") }
            systemProxyManager.enable(boundPort).onSuccess {
                updateState { copy(systemProxyActive = true) }
            }
            startForegroundCompat(buildNotification(boundPort))
            startHealthLoop()
            startWatchdogIfEnabled()
        }.onFailure { error ->
            Log.w(TAG, "Proxy rebind failed: ${error.message}")
            notifyProxyDown()
        }
    }

    private fun probeTcp(port: Int): Boolean = SystemProxyManager.probeListen(port)

    private fun startHealthLoop() {
        if (healthJob?.isActive == true) return
        healthJob = scope.launch {
            var backoff = HEALTH_MIN_BACKOFF_MS
            while (isActive) {
                delay(backoff)
                if (!idleGate.allowPeriodicWork) continue
                if (!_state.value.isRunning) continue
                val http = httpProxy
                if (http == null) {
                    recoverProxy()
                    backoff = HEALTH_MIN_BACKOFF_MS
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                if (http.lastAcceptAtElapsed > 0 && now - http.lastAcceptAtElapsed < TRAFFIC_HEARTBEAT_MS) {
                    backoff = HEALTH_MIN_BACKOFF_MS
                    continue
                }
                val httpAlive = http.isListening && probeTcp(http.boundPort)
                if (httpAlive) {
                    backoff = (backoff * 2).coerceAtMost(HEALTH_MAX_BACKOFF_MS)
                    continue
                }
                recoverProxy()
                backoff = HEALTH_MIN_BACKOFF_MS
            }
        }
    }

    private fun stopHealthLoop() {
        healthJob?.cancel()
        healthJob = null
    }

    private fun startWatchdogIfEnabled() {
        val app = application as ZerotierBApplication
        scope.launch {
            if (app.preferences.privilegedWatchdogEnabled.first()) {
                ProxyWatchdog.startIfNeeded(this@ProxyModeService)
            }
        }
    }

    private fun onBecameIdle(deviceIdle: Boolean) {
        stopHealthLoop()
        ProxyWatchdog.stop(this)
        if (deviceIdle && nodeStarted && !nodePausedForDoze) {
            scope.launch {
                val pause = (application as ZerotierBApplication).preferences.pauseNodeInDoze.first()
                if (pause) startStopMutex.withLock { pauseNodeForDoze() }
            }
        }
    }

    private suspend fun resumeFromIdleIfNeeded() {
        oneShotListenCheck()
        if (_state.value.isRunning) {
            startHealthLoop()
        }
        startWatchdogIfEnabled()
        if (nodePausedForDoze) {
            startStopMutex.withLock { resumeNodeFromDoze() }
        }
    }

    private suspend fun onBecameInteractive() {
        resumeFromIdleIfNeeded()
    }

    private fun oneShotListenCheck() {
        if (!_state.value.isRunning) return
        val http = httpProxy
        val port = http?.boundPort ?: 0
        if (http == null || !http.isListening || port <= 0 || !probeTcp(port)) {
            onListenDied()
        }
    }

    private suspend fun pauseNodeForDoze() {
        if (!nodeStarted || nodePausedForDoze) return
        Log.i(TAG, "Pausing ZeroTier node for Doze")
        pausedNetworks.clear()
        pausedNetworks.addAll(networkConfigs.values)
        for (network in pausedNetworks) {
            nodeManager.leave(network.networkIdLong())
        }
        networkStateJob?.cancel()
        networkStateJob = null
        nodeManager.stop()
        nodeStarted = false
        nodePausedForDoze = true
        updateState {
            copy(
                statusMessage = "ZeroTier paused (Doze)",
                nodeLifecycle = NodeLifecycleStatus.PAUSED_DOZE,
                networkStatuses = emptyList(),
            )
        }
    }

    private suspend fun resumeNodeFromDoze() {
        if (!nodePausedForDoze) return
        Log.i(TAG, "Resuming ZeroTier node after Doze")
        nodeManager.initialize().onFailure {
            Log.w(TAG, "Node re-init failed: ${it.message}")
        }
        startNetworkStatusPump()
        nodeManager.start(shouldAbort = { !_state.value.isRunning && !nodePausedForDoze }).onFailure {
            Log.w(TAG, "Node resume failed: ${it.message}")
            updateState {
                copy(
                    statusMessage = "ZeroTier paused (Doze) — resume failed",
                    nodeLifecycle = NodeLifecycleStatus.PAUSED_DOZE,
                )
            }
            return
        }
        nodeStarted = true
        nodePausedForDoze = false
        updateState {
            copy(nodeLifecycle = ztNodeStateToLifecycle(nodeManager.state.value, pausedDoze = false))
        }
        for (network in pausedNetworks.toList()) {
            joinConfiguredNetwork(network, startToken = 0L)
        }
        pausedNetworks.clear()
        val port = httpProxy?.boundPort ?: 0
        updateState {
            copy(statusMessage = if (port > 0) "Proxy on 127.0.0.1:$port" else "Running")
        }
    }

    private fun failClosedAndStop(reason: String) {
        Log.w(TAG, reason)
        markStopped()
        stopHealthLoop()
        ProxyWatchdog.stop(this)
        systemProxyManager.disableBlocking()
        httpProxy?.stop()
        httpProxy = null
        runCatching { idleGate.unregister() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        updateState { ProxyServiceState(hasSecureSettingsPermission = systemProxyManager.hasPermission()) }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            startStopMutex.withLock {
                if (nodeStarted || nodePausedForDoze) {
                    runCatching { nodeManager.stop() }
                    nodeStarted = false
                    nodePausedForDoze = false
                }
            }
        }
        stopSelf()
    }

    private fun notifyProxyDown() {
        if (!idleGate.allowPeriodicWork) return
        startForegroundCompat(
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_proxy_down))
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(false)
                .build(),
        )
    }

    companion object {
        private const val TAG = "ProxyModeService"
        private const val CHANNEL_ID = "zerotierb_proxy"
        private const val NOTIFICATION_ID = 5919814
        private const val JOIN_READY_TIMEOUT_MS = 30_000L
        private const val NETWORK_STATUS_POLL_MS = 2_000L
        private const val HEALTH_MIN_BACKOFF_MS = 60_000L
        private const val HEALTH_MAX_BACKOFF_MS = 300_000L
        private const val TRAFFIC_HEARTBEAT_MS = 90_000L

        const val ACTION_START = "com.brukb.zerotier.proxy.START"
        const val ACTION_STOP = "com.brukb.zerotier.proxy.STOP"
        const val EXTRA_FORCE_DEBUG = "force_debug"
        const val EXTRA_JOIN_NETWORK_IDS = "join_network_ids"
        const val EXTRA_START_TOKEN = "start_token"

        private val _state = MutableStateFlow(ProxyServiceState())
        val state: StateFlow<ProxyServiceState> = _state.asStateFlow()

        private val startCounter = AtomicLong()
        private val inFlightStarts = AtomicInteger(0)

        @Volatile
        private var stoppedToken = 0L

        /** True when a start was requested and no stop has superseded it yet. */
        val startRequested: Boolean
            get() = startCounter.get() > stoppedToken || inFlightStarts.get() > 0

        private fun isStartSuperseded(token: Long): Boolean =
            token != 0L && token <= stoppedToken

        private fun markStopped() {
            stoppedToken = startCounter.get()
        }

        fun markStartFinished() {
            inFlightStarts.updateAndGet { (it - 1).coerceAtLeast(0) }
        }

        fun start(context: Context, forceDebug: Boolean = false, joinNetworkIds: List<String>? = null) {
            val alreadyRunning = state.value.isRunning
            val token = if (alreadyRunning) 0L else startCounter.incrementAndGet()
            if (!alreadyRunning) inFlightStarts.incrementAndGet()
            val intent = Intent(context, ProxyModeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FORCE_DEBUG, forceDebug)
                putExtra(EXTRA_START_TOKEN, token)
                joinNetworkIds?.let { putExtra(EXTRA_JOIN_NETWORK_IDS, it.toTypedArray()) }
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                if (!alreadyRunning) markStartFinished()
                throw e
            }
        }

        fun stop(context: Context) {
            markStopped()
            val intent = Intent(context, ProxyModeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        suspend fun stopAndAwait(context: Context, timeoutMs: Long = 15_000): Boolean {
            if (!state.value.isRunning && !startRequested) return true
            stop(context)
            val stopped = withTimeoutOrNull(timeoutMs) {
                while (state.value.isRunning || inFlightStarts.get() > 0) {
                    delay(50)
                }
                true
            }
            if (stopped == null) {
                Log.w(TAG, "Proxy stop timed out after ${timeoutMs}ms")
            }
            return stopped != null
        }
    }
}
