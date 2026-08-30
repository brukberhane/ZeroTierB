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
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.dns.DnsResolver
import com.brukb.zerotier.proxy.http.HttpProxyServer
import com.brukb.zerotier.system.IdleGate
import com.brukb.zerotier.system.ProxyWatchdog
import com.brukb.zerotier.ui.MainActivity
import com.brukb.zerotier.vpn.ZerotierBVpnService
import com.brukb.zerotier.ztlib.ZeroTierNodeManager
import com.brukb.zerotier.ztlib.ZtNetworkStatus
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
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
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
    private var nodePausedForDoze = false
    private val pausedNetworks = mutableListOf<ZerotierBNetwork>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        routeResolver = RouteResolver()
        dnsResolver = DnsResolver()
        nodeManager = ZeroTierNodeManager(filesDir.absolutePath)
        systemProxyManager = SystemProxyManager(this, (application as ZerotierBApplication).preferences)
        idleGate = IdleGate(this) { allow, deviceIdle ->
            if (allow) {
                scope.launch { onBecameInteractive() }
            } else {
                onBecameIdle(deviceIdle)
            }
        }
        idleGate.register()
        scope.launch {
            updateState { copy(hasSecureSettingsPermission = systemProxyManager.hasPermission()) }
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
        when (intent?.action) {
            ACTION_START -> {
                if (!_state.value.isRunning) {
                    updateState { copy(isRunning = true, statusMessage = "Starting proxy...") }
                    scope.launch {
                        startStopMutex.withLock {
                            startProxy(
                                forceDebug = intent.getBooleanExtra(EXTRA_FORCE_DEBUG, false),
                                joinNetworkIds = joinIds(intent),
                                startToken = intent.getLongExtra(EXTRA_START_TOKEN, 0L),
                            )
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
        failClosedAndStop("foreground service timeout")
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        failClosedAndStop("foreground service timeout")
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
            updateState { copy(isRunning = false, statusMessage = "Stopped") }
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
        nodeManager.start().onFailure {
            fail(it.message ?: "Node start failed")
            return
        }
        nodeStarted = true

        val nodeId = ZeroTierNodeManager.formatNodeId(nodeManager.state.value.nodeId ?: 0L)
        updateState {
            copy(
                nodeId = nodeId,
                statusMessage = "Node online: $nodeId",
            )
        }
        Log.i(TAG, "Node online: $nodeId")

        networkConfigs.clear()
        routeResolver.clear()
        dnsResolver.clear()

        for (network in enabledNetworks) {
            if (isStartSuperseded(startToken)) {
                Log.i(TAG, "Start superseded by stop during join — aborting")
                return
            }
            joinConfiguredNetwork(network, startToken)
        }
        if (isStartSuperseded(startToken)) {
            Log.i(TAG, "Start superseded by stop before bind — aborting")
            return
        }

        networkStateJob = scope.launch {
            nodeManager.state.collect { nodeState ->
                for ((networkId, status) in nodeState.networks) {
                    val config = networkConfigs[networkId] ?: continue
                    if (status.status == ZtNetworkStatus.Status.OK) {
                        applyNetworkRuntime(config, status)
                    }
                }
            }
        }

        httpProxy = HttpProxyServer(0, routeResolver, dnsResolver, onDied = { onListenDied() }).also { it.start() }
        val boundPort = httpProxy?.boundPort ?: -1
        if (boundPort <= 0) {
            fail("HTTP proxy bind failed")
            return
        }

        app.preferences.setLastHttpProxyPort(boundPort)
        updateState {
            copy(
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
        routeResolver.clear()
        dnsResolver.clear()
        networkConfigs.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        updateState { ProxyServiceState(hasSecureSettingsPermission = systemProxyManager.hasPermission()) }
        stopSelf()
    }

    private fun fail(message: String) {
        Log.e(TAG, message)
        updateState {
            copy(
                lastError = message,
                statusMessage = message,
                isRunning = false,
            )
        }
        scope.launch { startStopMutex.withLock { stopProxy() } }
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

    private fun probeTcp(port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), 400)
                true
            }
        }.getOrDefault(false)
    }

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

    private suspend fun onBecameInteractive() {
        oneShotListenCheck()
        if (_state.value.isRunning) {
            startHealthLoop()
        }
        startWatchdogIfEnabled()
        if (nodePausedForDoze) {
            startStopMutex.withLock { resumeNodeFromDoze() }
        }
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
        nodeManager.stop()
        nodeStarted = false
        nodePausedForDoze = true
        updateState { copy(statusMessage = "ZeroTier paused (Doze)") }
    }

    private suspend fun resumeNodeFromDoze() {
        if (!nodePausedForDoze) return
        Log.i(TAG, "Resuming ZeroTier node after Doze")
        nodeManager.start().onFailure {
            Log.w(TAG, "Node resume failed: ${it.message}")
            return
        }
        nodeStarted = true
        nodePausedForDoze = false
        for (network in pausedNetworks.toList()) {
            joinConfiguredNetwork(network, startToken = 0L)
        }
        pausedNetworks.clear()
        updateState { copy(statusMessage = "Proxy on 127.0.0.1:${httpProxy?.boundPort ?: 0}") }
    }

    private fun failClosedAndStop(reason: String) {
        Log.w(TAG, reason)
        stopHealthLoop()
        ProxyWatchdog.stop(this)
        systemProxyManager.disableBlocking()
        httpProxy?.stop()
        httpProxy = null
        runCatching { idleGate.unregister() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        markStopped()
        updateState { ProxyServiceState(hasSecureSettingsPermission = systemProxyManager.hasPermission()) }
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

        @Volatile
        private var stoppedToken = 0L

        /** True when a start was requested and no stop has superseded it yet. */
        val startRequested: Boolean
            get() = startCounter.get() > stoppedToken

        private fun isStartSuperseded(token: Long): Boolean =
            token != 0L && token <= stoppedToken

        private fun markStopped() {
            stoppedToken = startCounter.get()
        }

        fun start(context: Context, forceDebug: Boolean = false, joinNetworkIds: List<String>? = null) {
            val token = startCounter.incrementAndGet()
            val intent = Intent(context, ProxyModeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FORCE_DEBUG, forceDebug)
                putExtra(EXTRA_START_TOKEN, token)
                joinNetworkIds?.let { putExtra(EXTRA_JOIN_NETWORK_IDS, it.toTypedArray()) }
            }
            context.startForegroundService(intent)
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
                state.first { !it.isRunning && !startRequested }
            }
            if (stopped == null) {
                Log.w(TAG, "Proxy stop timed out after ${timeoutMs}ms")
            }
            return stopped != null
        }
    }
}
