package com.zerotier.pylon.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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
import com.zerotier.pylon.ui.MainActivity
import com.zerotier.pylon.zt.ZeroTierNodeManager
import com.zerotier.pylon.zt.ZtNetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PylonService : LifecycleService() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)

    private lateinit var nodeManager: ZeroTierNodeManager
    private lateinit var routeResolver: RouteResolver
    private lateinit var dnsResolver: DnsResolver
    private lateinit var rulesEngine: ProxyRulesEngine
    private lateinit var systemProxyManager: SystemProxyManager
    private lateinit var preferences: AppPreferences

    private var httpProxy: HttpProxyServer? = null
    private var socks5Proxy: Socks5ProxyServer? = null
    private val networkConfigs = mutableMapOf<Long, PylonNetwork>()
    private var nodeStarted = false

    override fun onCreate() {
        super.onCreate()
        val app = application as PylonApplication
        preferences = app.preferences
        systemProxyManager = SystemProxyManager(this, preferences)
        routeResolver = RouteResolver()
        dnsResolver = DnsResolver()
        rulesEngine = ProxyRulesEngine()
        nodeManager = ZeroTierNodeManager(filesDir.absolutePath)
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
        when (intent?.action) {
            ACTION_START -> scope.launch { startPylon() }
            ACTION_STOP -> scope.launch { stopPylon() }
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
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        scope.launch { stopPylon() }
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun startPylon() {
        if (_state.value.isRunning) return
        updateState {
            copy(
                isRunning = true,
                nodeStatus = NodeStatus.STARTING,
                statusMessage = "Starting ZeroTier node...",
            )
        }
        startForeground(NOTIFICATION_ID, buildNotification(AppPreferences.DEFAULT_HTTP_PORT))

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

        httpProxy = HttpProxyServer(httpPort, routeResolver, dnsResolver, rulesEngine, lookup).also {
            it.start()
        }
        appendLog("HTTP proxy on 127.0.0.1:$httpPort")

        if (socksEnabled) {
            socks5Proxy = Socks5ProxyServer(socksPort, routeResolver, dnsResolver, rulesEngine, lookup).also {
                it.start()
            }
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
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(httpPort))
    }

    private suspend fun stopProxies() {
        systemProxyManager.disable().onFailure {
            appendLog("Failed to restore system proxy: ${it.message}")
        }
        httpProxy?.stop()
        httpProxy = null
        socks5Proxy?.stop()
        socks5Proxy = null
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
        } else {
            stopProxies()
        }
        updateState { copy(proxyEnabled = enabled) }
    }

    private suspend fun stopPylon() {
        updateState { copy(statusMessage = "Stopping...") }
        stopProxies()
        for (network in networkConfigs.values.toList()) {
            nodeManager.leave(network.networkIdLong())
        }
        if (nodeStarted) {
            nodeManager.stop()
            nodeStarted = false
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

    private fun buildNotification(port: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text, port))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "PylonService"
        private const val CHANNEL_ID = "pylon_service"
        private const val NOTIFICATION_ID = 1

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
