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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.brukb.zerotier.R
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.dns.DnsResolver
import com.brukb.zerotier.proxy.http.HttpProxyServer
import com.brukb.zerotier.ui.MainActivity
import com.brukb.zerotier.vpn.ZerotierBVpnService
import com.brukb.zerotier.ztlib.ZeroTierNodeManager
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        routeResolver = RouteResolver()
        dnsResolver = DnsResolver()
        nodeManager = ZeroTierNodeManager(filesDir.absolutePath)
        systemProxyManager = SystemProxyManager(this, (application as ZerotierBApplication).preferences)
        scope.launch {
            updateState { copy(hasSecureSettingsPermission = systemProxyManager.hasPermission()) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> scope.launch {
                val joinIds = intent.getStringArrayExtra(EXTRA_JOIN_NETWORK_IDS)?.toList()
                startProxy(intent.getBooleanExtra(EXTRA_FORCE_DEBUG, false), joinIds)
            }
            ACTION_STOP -> scope.launch { stopProxy() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        scope.launch { stopProxy() }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.launch { stopProxy() }
        serviceJob.cancel()
        super.onDestroy()
    }

    private suspend fun startProxy(forceDebug: Boolean, joinNetworkIds: List<String>? = null) {
        if (_state.value.isRunning) return
        if (ZerotierBVpnService.state.value.isRunning && !forceDebug) {
            updateState {
                copy(
                    lastError = "VPN active — stop VPN before proxy",
                    statusMessage = "Refused: VPN active",
                )
            }
            stopSelf()
            return
        }

        updateState { copy(isRunning = true, statusMessage = "Starting proxy...") }
        startForegroundCompat(buildNotification(0))

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
            joinConfiguredNetwork(network)
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

        httpProxy = HttpProxyServer(0, routeResolver, dnsResolver).also { it.start() }
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

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(boundPort))
    }

    private suspend fun joinConfiguredNetwork(network: ZerotierBNetwork) {
        val networkId = network.networkIdLong()
        networkConfigs[networkId] = network
        val joinResult = nodeManager.join(networkId, network)
        if (joinResult.isFailure) {
            Log.w(TAG, "Join failed for ${network.networkId}: ${joinResult.exceptionOrNull()?.message}")
            return
        }
        val readyResult = nodeManager.waitForNetworkReady(networkId)
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
        updateState { copy(statusMessage = "Stopping...") }
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
        if (nodeStarted) {
            nodeManager.stop()
            nodeStarted = false
        }
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
        scope.launch { stopProxy() }
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
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "ProxyModeService"
        private const val CHANNEL_ID = "zerotierb_proxy"
        private const val NOTIFICATION_ID = 5919814

        const val ACTION_START = "com.brukb.zerotier.proxy.START"
        const val ACTION_STOP = "com.brukb.zerotier.proxy.STOP"
        const val EXTRA_FORCE_DEBUG = "force_debug"
        const val EXTRA_JOIN_NETWORK_IDS = "join_network_ids"

        private val _state = MutableStateFlow(ProxyServiceState())
        val state: StateFlow<ProxyServiceState> = _state.asStateFlow()

        fun start(context: Context, forceDebug: Boolean = false, joinNetworkIds: List<String>? = null) {
            val intent = Intent(context, ProxyModeService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FORCE_DEBUG, forceDebug)
                joinNetworkIds?.let { putExtra(EXTRA_JOIN_NETWORK_IDS, it.toTypedArray()) }
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ProxyModeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        suspend fun stopAndAwait(context: Context, timeoutMs: Long = 10_000) {
            if (!state.value.isRunning) return
            stop(context)
            val stopped = withTimeoutOrNull(timeoutMs) {
                state.first { !it.isRunning }
            }
            if (stopped == null) {
                Log.w(TAG, "Proxy stop timed out after ${timeoutMs}ms")
            }
        }
    }
}
