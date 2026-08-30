package com.zerotier.pylon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import com.zerotier.pylon.PylonApplication
import com.zerotier.pylon.R
import com.zerotier.pylon.data.AppPreferences
import com.zerotier.pylon.data.model.PylonNetwork
import com.zerotier.pylon.proxy.ProxyRulesEngine
import com.zerotier.pylon.proxy.RouteResolver
import com.zerotier.pylon.proxy.SystemProxyManager
import com.zerotier.pylon.proxy.dns.DnsResolver
import com.zerotier.pylon.proxy.http.HttpProxyServer
import com.zerotier.pylon.proxy.socks5.Socks5ProxyServer
import com.zerotier.pylon.system.IdleGate
import com.zerotier.pylon.system.ProxyHealthJob
import com.zerotier.pylon.system.ProxyWatchdog
import com.zerotier.pylon.ui.MainActivity
import com.zerotier.pylon.zt.ZeroTierNodeManager
import com.zerotier.pylon.zt.ZtNetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class PylonService : LifecycleService() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)

    private lateinit var nodeManager: ZeroTierNodeManager
    private lateinit var routeResolver: RouteResolver
    private lateinit var dnsResolver: DnsResolver
    private lateinit var rulesEngine: ProxyRulesEngine
    private lateinit var systemProxyManager: SystemProxyManager
    private lateinit var preferences: AppPreferences
    private lateinit var idleGate: IdleGate

    private var httpProxy: HttpProxyServer? = null
    private var socks5Proxy: Socks5ProxyServer? = null
    private val networkConfigs = mutableMapOf<Long, PylonNetwork>()
    private var nodeStarted = false
    private var healthJob: Job? = null
    private val recovering = AtomicBoolean(false)
    private var nodePausedForDoze = false
    private val pausedNetworks = mutableListOf<PylonNetwork>()

    override fun onCreate() {
        super.onCreate()
        val app = application as PylonApplication
        preferences = app.preferences
        systemProxyManager = SystemProxyManager(this, preferences)
        routeResolver = RouteResolver()
        dnsResolver = DnsResolver()
        rulesEngine = ProxyRulesEngine()
        nodeManager = ZeroTierNodeManager(filesDir.absolutePath)
        idleGate = IdleGate(this) { allow, deviceIdle ->
            if (allow) {
                scope.launch { onBecameInteractive() }
            } else {
                onBecameIdle(deviceIdle)
            }
        }
        idleGate.register()
        createNotificationChannel()
        scope.launch {
            val proxyEnabled = preferences.proxyEnabled.first()
            updateState {
                copy(
                    hasSecureSettingsPermission = systemProxyManager.hasPermission(),
                    proxyEnabled = proxyEnabled,
                )
            }
        }
        scope.launch {
            nodeManager.state.collect { nodeState ->
                if (!nodeStarted) return@collect
                nodeState.networks.forEach { (id, status) ->
                    val config = networkConfigs[id] ?: return@forEach
                    applyNetworkRuntime(config, status)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        promoteForeground(proxyDown = false)
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    preferences.setServiceWanted(false)
                    stopPylon()
                }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                scope.launch {
                    preferences.setServiceWanted(true)
                    startPylon()
                }
            }
            ACTION_JOIN_NETWORK -> {
                val networkId = intent.getStringExtra(EXTRA_NETWORK_ID) ?: return START_STICKY
                scope.launch { joinNetworkRuntime(networkId) }
            }
            ACTION_LEAVE_NETWORK -> {
                val networkId = intent.getStringExtra(EXTRA_NETWORK_ID) ?: return START_STICKY
                scope.launch { leaveNetworkRuntime(networkId) }
            }
            ACTION_SET_PROXY_ENABLED -> {
                val enabled = intent.getBooleanExtra(EXTRA_PROXY_ENABLED, true)
                scope.launch { setProxyEnabled(enabled) }
            }
            else -> {
                scope.launch {
                    if (preferences.serviceWanted.first()) startPylon()
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

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
        idleGate.unregister()
        stopHealthLoop()
        ProxyWatchdog.stop(this)
        systemProxyManager.disableBlocking()
        httpProxy?.stop()
        httpProxy = null
        socks5Proxy?.stop()
        socks5Proxy = null
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun promoteForeground(proxyDown: Boolean) {
        val port = _state.value.httpProxyPort ?: AppPreferences.DEFAULT_HTTP_PORT
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(port, proxyDown),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private suspend fun startPylon() {
        val alreadyRunning = _state.value.isRunning
        if (alreadyRunning) {
            if (idleGate.allowPeriodicWork) {
                startHealthLoop()
                startWatchdogIfEnabled()
            }
            return
        }
        updateState {
            copy(
                isRunning = true,
                nodeStatus = NodeStatus.STARTING,
                statusMessage = "Starting ZeroTier node...",
            )
        }
        promoteForeground(proxyDown = false)

        val repository = (application as PylonApplication).networkRepository
        repository.migrateStoredNetworkIds()

        val enabledNetworks = repository
            .observeEnabled()
            .first()
            .filter { PylonNetwork.isValidNetworkId(it.networkId) }

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
        nodePausedForDoze = false

        val nodeId = ZeroTierNodeManager.formatNodeId(nodeManager.state.value.nodeId ?: 0L)
        updateState {
            copy(
                nodeStatus = NodeStatus.ONLINE,
                nodeId = nodeId,
                statusMessage = "Node online: $nodeId",
            )
        }
        appendLog("Node online: $nodeId")

        networkConfigs.clear()
        routeResolver.clear()
        dnsResolver.clear()

        for (network in enabledNetworks) {
            joinConfiguredNetwork(network)
        }

        if (preferences.proxyEnabled.first()) {
            startProxies()
        } else {
            appendLog("Proxy disabled; node running without local proxy")
        }

        if (idleGate.allowPeriodicWork) {
            startHealthLoop()
            startWatchdogIfEnabled()
        }

        ProxyHealthJob.schedule(this)
        updateState { copy(statusMessage = "Running") }
    }

    private suspend fun joinConfiguredNetwork(network: PylonNetwork) {
        val networkId = network.networkIdLong()
        networkConfigs[networkId] = network
        updateNetworkJoinState(network.networkId, NetworkJoinStatus.JOINING)
        val joinResult = nodeManager.join(networkId, network)
        if (joinResult.isFailure) {
            appendLog("Join failed for ${network.networkId}: ${joinResult.exceptionOrNull()?.message}")
            updateNetworkJoinState(network.networkId, NetworkJoinStatus.ERROR)
            return
        }
        val readyResult = nodeManager.waitForNetworkReady(networkId)
        if (readyResult.isFailure) {
            appendLog("Network not ready ${network.networkId}: ${readyResult.exceptionOrNull()?.message}")
            updateNetworkJoinState(network.networkId, NetworkJoinStatus.ERROR)
            return
        }
        val status = readyResult.getOrThrow()
        applyNetworkRuntime(network, status)
        appendLog("Joined ${network.networkId}")
    }

    private suspend fun joinNetworkRuntime(networkIdHex: String) {
        if (!nodeStarted) {
            appendLog("Cannot join $networkIdHex: node not running")
            return
        }
        val network = (application as PylonApplication).networkRepository.getById(networkIdHex)
            ?: return
        joinConfiguredNetwork(network)
        if (_state.value.proxyEnabled && httpProxy == null) {
            startProxies()
        }
    }

    private suspend fun leaveNetworkRuntime(networkIdHex: String) {
        if (!nodeStarted) return
        val networkId = PylonNetwork.parseNetworkIdLong(networkIdHex)
        nodeManager.leave(networkId)
        networkConfigs.remove(networkId)
        routeResolver.removeNetwork(networkId)
        dnsResolver.removeNetwork(networkId)
        networkConfigs.values.forEach { config ->
            nodeManager.getNetworkStatus(config.networkIdLong())?.let { status ->
                dnsResolver.updateNetwork(config, status)
            }
        }
        updateState {
            copy(
                networkStatuses = networkStatuses - networkIdHex,
                activeNetworkId = networkStatuses.keys.firstOrNull { it != networkIdHex },
            )
        }
        appendLog("Left $networkIdHex")
    }

    private fun applyNetworkRuntime(network: PylonNetwork, status: ZtNetworkStatus) {
        val joinStatus = when (status.status) {
            ZtNetworkStatus.Status.OK -> NetworkJoinStatus.OK
            ZtNetworkStatus.Status.ACCESS_DENIED -> NetworkJoinStatus.ACCESS_DENIED
            ZtNetworkStatus.Status.JOINING -> NetworkJoinStatus.JOINING
            else -> NetworkJoinStatus.ERROR
        }
        routeResolver.updateNetwork(network, status)
        if (network.allowDns) {
            dnsResolver.updateNetwork(network, status)
        } else {
            dnsResolver.removeNetwork(network.networkIdLong())
        }
        updateState {
            copy(
                networkJoinStatus = joinStatus,
                activeNetworkId = network.networkId,
                networkStatuses = networkStatuses + (
                    network.networkId to NetworkRuntimeStatus(
                        networkId = network.networkId,
                        joinStatus = joinStatus,
                        assignedAddresses = status.assignedAddresses,
                        routes = status.routes,
                        dnsServers = status.dnsServers,
                        dnsDomain = status.dnsDomain,
                    )
                    ),
            )
        }
    }

    private suspend fun startProxies() {
        if (httpProxy != null) return
        val httpPort = preferences.httpProxyPort.first()
        val socksPort = preferences.socks5ProxyPort.first()
        val socksEnabled = preferences.socks5Enabled.first()
        val lookup: (Long?) -> PylonNetwork? = { id -> id?.let(networkConfigs::get) }

        httpProxy = HttpProxyServer(
            httpPort,
            routeResolver,
            dnsResolver,
            rulesEngine,
            lookup,
            onDied = { onListenDied() },
        ).also { it.start() }
        appendLog("HTTP proxy on 127.0.0.1:$httpPort")

        if (socksEnabled) {
            socks5Proxy = Socks5ProxyServer(
                socksPort,
                routeResolver,
                dnsResolver,
                rulesEngine,
                lookup,
                onDied = { onListenDied() },
            ).also { it.start() }
            appendLog("SOCKS5 proxy on 127.0.0.1:$socksPort")
        }

        systemProxyManager.enable(httpPort).onFailure {
            appendLog("System proxy not set: ${it.message}")
        }.onSuccess {
            appendLog("System proxy set to 127.0.0.1:$httpPort")
        }

        updateState {
            copy(
                proxyEnabled = true,
                httpProxyPort = httpPort,
                socks5ProxyPort = if (socksEnabled) socksPort else null,
                socks5Enabled = socksEnabled,
                systemProxyActive = systemProxyManager.currentProxy()?.contains(httpPort.toString()) == true,
            )
        }
        preferences.setProxyEnabled(true)
        if (idleGate.allowPeriodicWork) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(httpPort, proxyDown = false))
        }
    }

    private fun stopProxySockets() {
        httpProxy?.stop()
        httpProxy = null
        socks5Proxy?.stop()
        socks5Proxy = null
    }

    private suspend fun stopProxies() {
        systemProxyManager.disableBlocking().onFailure {
            appendLog("Failed to restore system proxy: ${it.message}")
        }
        stopProxySockets()
        updateState {
            copy(
                httpProxyPort = null,
                socks5ProxyPort = null,
                systemProxyActive = false,
            )
        }
    }

    private suspend fun setProxyEnabled(enabled: Boolean) {
        preferences.setProxyEnabled(enabled)
        if (enabled) {
            if (nodeStarted) startProxies()
            if (idleGate.allowPeriodicWork) startHealthLoop()
        } else {
            stopHealthLoop()
            stopProxies()
        }
        updateState { copy(proxyEnabled = enabled) }
    }

    private fun onListenDied() {
        if (!recovering.compareAndSet(false, true)) return
        scope.launch {
            try {
                recoverProxies()
            } finally {
                recovering.set(false)
            }
        }
    }

    private suspend fun recoverProxies() {
        if (!_state.value.proxyEnabled) return
        val port = httpProxy?.listenPort ?: preferences.httpProxyPort.first()
        val httpAlive = httpProxy != null && httpProxy?.isListening == true && probeTcp(port)
        val socks = socks5Proxy
        val socksAlive = socks == null || (socks.isListening && probeTcp(socks.listenPort))
        if (httpAlive && socksAlive) return

        appendLog("Proxy listen dead; clearing system proxy")
        systemProxyManager.disableBlocking()
        stopProxySockets()
        updateState {
            copy(
                httpProxyPort = null,
                socks5ProxyPort = null,
                systemProxyActive = false,
            )
        }

        if (!_state.value.isRunning || !preferences.proxyEnabled.first()) {
            notifyProxyDown()
            return
        }

        runCatching { startProxies() }
            .onFailure { error ->
                appendLog("Proxy rebind failed: ${error.message}")
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
                if (!_state.value.isRunning || !_state.value.proxyEnabled) continue
                val http = httpProxy
                if (http == null) {
                    recoverProxies()
                    backoff = HEALTH_MIN_BACKOFF_MS
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                if (http.lastAcceptAtElapsed > 0 && now - http.lastAcceptAtElapsed < TRAFFIC_HEARTBEAT_MS) {
                    backoff = HEALTH_MIN_BACKOFF_MS
                    continue
                }
                val socks = socks5Proxy
                val httpAlive = http.isListening && probeTcp(http.listenPort)
                val socksAlive = socks == null || (socks.isListening && probeTcp(socks.listenPort))
                if (httpAlive && socksAlive) {
                    backoff = (backoff * 2).coerceAtMost(HEALTH_MAX_BACKOFF_MS)
                    continue
                }
                recoverProxies()
                backoff = HEALTH_MIN_BACKOFF_MS
            }
        }
    }

    private fun stopHealthLoop() {
        healthJob?.cancel()
        healthJob = null
    }

    private fun startWatchdogIfEnabled() {
        if (preferences.privilegedWatchdogEnabledBlocking()) {
            ProxyWatchdog.startIfNeeded(this)
        }
    }

    private fun onBecameIdle(deviceIdle: Boolean) {
        stopHealthLoop()
        ProxyWatchdog.stop(this)
        if (deviceIdle && preferences.pauseNodeInDozeBlocking() && nodeStarted && !nodePausedForDoze) {
            scope.launch { pauseNodeForDoze() }
        }
    }

    private suspend fun onBecameInteractive() {
        oneShotListenCheck()
        if (_state.value.isRunning && _state.value.proxyEnabled) {
            startHealthLoop()
        }
        startWatchdogIfEnabled()
        if (nodePausedForDoze) {
            resumeNodeFromDoze()
        }
    }

    private fun oneShotListenCheck() {
        if (!_state.value.isRunning || !_state.value.proxyEnabled) return
        val http = httpProxy
        if (http == null || !http.isListening || !probeTcp(http.listenPort)) {
            onListenDied()
        }
    }

    private suspend fun pauseNodeForDoze() {
        appendLog("Pausing ZeroTier node for Doze")
        pausedNetworks.clear()
        pausedNetworks.addAll(networkConfigs.values)
        for (network in pausedNetworks) {
            nodeManager.leave(network.networkIdLong())
        }
        nodeManager.stop()
        nodeStarted = false
        nodePausedForDoze = true
        updateState { copy(nodeStatus = NodeStatus.STOPPED, statusMessage = "ZeroTier paused (Doze)") }
    }

    private suspend fun resumeNodeFromDoze() {
        if (!nodePausedForDoze) return
        appendLog("Resuming ZeroTier node after Doze")
        nodeManager.start().onFailure {
            appendLog("Node resume failed: ${it.message}")
            return
        }
        nodeStarted = true
        nodePausedForDoze = false
        for (network in pausedNetworks.toList()) {
            joinConfiguredNetwork(network)
        }
        pausedNetworks.clear()
        updateState { copy(nodeStatus = NodeStatus.ONLINE, statusMessage = "Running") }
    }

    private suspend fun stopPylon() {
        updateState { copy(statusMessage = "Stopping...") }
        stopHealthLoop()
        ProxyWatchdog.stop(this)
        ProxyHealthJob.cancel(this)
        stopProxies()
        for (network in networkConfigs.values.toList()) {
            nodeManager.leave(network.networkIdLong())
        }
        if (nodeStarted || nodePausedForDoze) {
            nodeManager.stop()
            nodeStarted = false
            nodePausedForDoze = false
        }
        routeResolver.clear()
        dnsResolver.clear()
        networkConfigs.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        updateState {
            PylonServiceState(hasSecureSettingsPermission = systemProxyManager.hasPermission())
        }
        stopSelf()
    }

    private fun failClosedAndStop(reason: String) {
        appendLog(reason)
        stopHealthLoop()
        ProxyWatchdog.stop(this)
        systemProxyManager.disableBlocking()
        stopProxySockets()
        runCatching { idleGate.unregister() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        updateState {
            PylonServiceState(hasSecureSettingsPermission = systemProxyManager.hasPermission())
        }
        stopSelf()
    }

    private fun notifyProxyDown() {
        if (!idleGate.allowPeriodicWork) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(AppPreferences.DEFAULT_HTTP_PORT, proxyDown = true))
    }

    private fun updateNetworkJoinState(networkId: String, status: NetworkJoinStatus) {
        updateState {
            copy(
                networkJoinStatus = status,
                activeNetworkId = networkId,
                networkStatuses = networkStatuses + (
                    networkId to (networkStatuses[networkId]?.copy(joinStatus = status)
                        ?: NetworkRuntimeStatus(networkId, status))
                    ),
            )
        }
    }

    private fun fail(message: String) {
        appendLog(message)
        updateState {
            copy(
                nodeStatus = NodeStatus.ERROR,
                statusMessage = message,
                isRunning = false,
            )
        }
        scope.launch { stopPylon() }
    }

    private fun appendLog(message: String) {
        Log.i(TAG, message)
        updateState { copy(logs = (logs + message).takeLast(100)) }
    }

    private fun updateState(block: PylonServiceState.() -> PylonServiceState) {
        _state.value = _state.value.block()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(port: Int, proxyDown: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (proxyDown) {
            getString(R.string.notification_proxy_down)
        } else {
            getString(R.string.notification_text, port)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "PylonService"
        private const val CHANNEL_ID = "pylon_service"
        private const val NOTIFICATION_ID = 1
        private const val HEALTH_MIN_BACKOFF_MS = 60_000L
        private const val HEALTH_MAX_BACKOFF_MS = 300_000L
        private const val TRAFFIC_HEARTBEAT_MS = 90_000L

        const val ACTION_START = "com.zerotier.pylon.action.START"
        const val ACTION_STOP = "com.zerotier.pylon.action.STOP"
        const val ACTION_JOIN_NETWORK = "com.zerotier.pylon.action.JOIN_NETWORK"
        const val ACTION_LEAVE_NETWORK = "com.zerotier.pylon.action.LEAVE_NETWORK"
        const val ACTION_SET_PROXY_ENABLED = "com.zerotier.pylon.action.SET_PROXY_ENABLED"
        const val EXTRA_NETWORK_ID = "network_id"
        const val EXTRA_PROXY_ENABLED = "proxy_enabled"

        private val _state = MutableStateFlow(PylonServiceState())
        val state: StateFlow<PylonServiceState> = _state.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, PylonService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PylonService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun joinNetwork(context: Context, networkId: String) {
            val intent = Intent(context, PylonService::class.java).apply {
                action = ACTION_JOIN_NETWORK
                putExtra(EXTRA_NETWORK_ID, networkId)
            }
            context.startService(intent)
        }

        fun leaveNetwork(context: Context, networkId: String) {
            val intent = Intent(context, PylonService::class.java).apply {
                action = ACTION_LEAVE_NETWORK
                putExtra(EXTRA_NETWORK_ID, networkId)
            }
            context.startService(intent)
        }

        fun setProxyEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, PylonService::class.java).apply {
                action = ACTION_SET_PROXY_ENABLED
                putExtra(EXTRA_PROXY_ENABLED, enabled)
            }
            context.startService(intent)
        }
    }
}
