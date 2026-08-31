package com.brukb.zerotier.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.brukb.zerotier.R
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.connection.NodeLifecycleStatus
import com.brukb.zerotier.connection.NetworkRuntimeStatus
import com.brukb.zerotier.connection.formatAssignedCidr
import com.brukb.zerotier.connection.formatRouteLine
import com.brukb.zerotier.connection.vpnVirtualStatusToJoinStatus
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.ui.MainActivity
import com.brukb.zerotier.vpn.scheduling.PacketScheduler
import com.zerotier.sdk.Event
import com.zerotier.sdk.EventListener
import com.zerotier.sdk.Node
import com.zerotier.sdk.PacketSender
import com.zerotier.sdk.ResultCode
import com.zerotier.sdk.VirtualNetworkConfig
import com.zerotier.sdk.VirtualNetworkConfigListener
import com.zerotier.sdk.VirtualNetworkConfigOperation
import com.zerotier.sdk.VirtualNetworkStatus
import com.zerotier.sdk.util.StringUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ZerotierBVpnService :
    VpnService(),
    Runnable,
    EventListener,
    VirtualNetworkConfigListener,
    TunTapHost,
    PacketSender {

    private val dataStore by lazy { ZeroTierDataStore(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val virtualNetworkConfigs = ConcurrentHashMap<Long, VirtualNetworkConfig>()
    private val networkSettings = ConcurrentHashMap<Long, ZerotierBNetwork>()
    private var node: Node? = null
    private var tunTapAdapter: TunTapAdapter? = null
    private var packetScheduler: PacketScheduler? = null
    private var udpCom: UdpCom? = null
    private var udpThread: Thread? = null
    private var vpnThread: Thread? = null
    private var datagramSocket: DatagramSocket? = null
    private var vpnSocket: ParcelFileDescriptor? = null
    private var inStream: FileInputStream? = null
    private var outStream: FileOutputStream? = null
    private var nextBackgroundTaskDeadline = 0L
    private val nodeLock = Any()
    @Volatile
    private var drainingBackground = false
    private var startId = -1
    private val rebuildMutex = Mutex()
    @Volatile
    private var allowedVpnNetworkId: String? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        this.startId = startId
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_JOIN -> {
                intent.getStringExtra(EXTRA_NETWORK_ID)?.let { joinNetwork(it) }
                return START_STICKY
            }
            ACTION_LEAVE -> {
                intent.getStringExtra(EXTRA_NETWORK_ID)?.let { leaveNetwork(it) }
                return START_STICKY
            }
        }

        intent?.getStringExtra(EXTRA_SINGLE_NETWORK_ID)?.let { allowedVpnNetworkId = it }

        startForegroundCompat(buildNotification("Starting VPN"))
        updateState { copy(nodeLifecycle = NodeLifecycleStatus.STARTING) }
        synchronized(this) {
            if (node != null) {
                refreshJoinedNetworks()
                return START_STICKY
            }
            val startToken = intent?.getLongExtra(EXTRA_START_TOKEN, 0L) ?: 0L
            if (isStartSuperseded(startToken)) {
                Log.i(TAG, "Start superseded by stop — not starting node")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 1000
                    bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), 9994))
                }
                Log.i(TAG, "UDP bound localPort=${socket.localPort} ipv4=0.0.0.0")
                if (!protect(socket)) {
                    markStopped()
                    updateState {
                        copy(
                            statusMessage = "Failed to protect UDP socket",
                            nodeLifecycle = NodeLifecycleStatus.ERROR,
                        )
                    }
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                datagramSocket = socket
                val ztNode = Node(System.currentTimeMillis())
                val scheduler = PacketScheduler(this)
                packetScheduler = scheduler
                val adapter = TunTapAdapter(this, scheduler)
                tunTapAdapter = adapter
                val udp = UdpCom(scheduler, socket)
                udpCom = udp
                val initResult = ztNode.init(
                    dataStore,
                    dataStore,
                    this,
                    this,
                    adapter,
                    this,
                    null,
                )
                if (initResult != ResultCode.RESULT_OK) {
                    updateState {
                        copy(
                            statusMessage = "Node init failed: $initResult",
                            nodeLifecycle = NodeLifecycleStatus.ERROR,
                        )
                    }
                    shutdown()
                    return START_NOT_STICKY
                }
                node = ztNode
                scheduler.start()
                vpnThread = Thread(this, "ZeroTier Service Thread").also { it.start() }
                updateState {
                    copy(
                        isRunning = true,
                        nodeId = StringUtils.addressToString(ztNode.address()),
                        statusMessage = "Waiting for roots",
                        nodeLifecycle = NodeLifecycleStatus.STARTING,
                    )
                }
                udpThread = Thread(udp, "UDP Listen Thread").also { it.start() }
                refreshJoinedNetworks()
            } catch (e: Exception) {
                Log.e(TAG, "Start failed", e)
                updateState {
                    copy(
                        statusMessage = e.message ?: "Start failed",
                        nodeLifecycle = NodeLifecycleStatus.ERROR,
                    )
                }
                shutdown()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    override fun onSendPacketRequested(
        localSocket: Long,
        remoteAddr: InetSocketAddress,
        packetData: ByteArray,
        ttl: Int,
    ): Int {
        val socket = datagramSocket ?: return -1
        return try {
            socket.send(java.net.DatagramPacket(packetData, packetData.size, remoteAddr))
            0
        } catch (e: Exception) {
            Log.e(TAG, "UDP send failed to $remoteAddr: ${e.message}")
            -1
        }
    }

    override fun onEvent(event: Event) {
        Log.i(TAG, "ZT event: $event")
        when (event) {
            Event.EVENT_ONLINE -> {
                updateState {
                    copy(
                        nodeLifecycle = NodeLifecycleStatus.ONLINE,
                        statusMessage = if (statusMessage.startsWith("VPN active")) {
                            statusMessage
                        } else {
                            "Node online"
                        },
                    )
                }
            }
            Event.EVENT_OFFLINE -> {
                updateState {
                    copy(
                        nodeLifecycle = NodeLifecycleStatus.STARTING,
                        statusMessage = "Node offline — waiting for roots",
                    )
                }
            }
            Event.EVENT_FATAL_ERROR_IDENTITY_COLLISION -> {
                updateState {
                    copy(
                        nodeLifecycle = NodeLifecycleStatus.ERROR,
                        statusMessage = "Identity collision",
                    )
                }
            }
            else -> {}
        }
    }

    override fun onTrace(message: String) {
        Log.v(TAG, message)
    }

    override fun onNetworkConfigurationUpdated(
        networkId: Long,
        operation: VirtualNetworkConfigOperation,
        config: VirtualNetworkConfig,
    ): Int {
        Log.i(
            TAG,
            "Network config op=$operation id=${StringUtils.networkIdToString(networkId)} " +
                "status=${config.status} name=${config.name}",
        )
        when (operation) {
            VirtualNetworkConfigOperation.VIRTUAL_NETWORK_CONFIG_OPERATION_UP -> {
                val previous = virtualNetworkConfigs[networkId]
                virtualNetworkConfigs[networkId] = config
                scope.launch {
                    if (config.status == VirtualNetworkStatus.NETWORK_STATUS_OK &&
                        (previous == null || previous != config)
                    ) {
                        rebuildVpn()
                    }
                    publishNetworkStatuses()
                }
            }
            VirtualNetworkConfigOperation.VIRTUAL_NETWORK_CONFIG_OPERATION_CONFIG_UPDATE -> {
                val previous = virtualNetworkConfigs[networkId]
                virtualNetworkConfigs[networkId] = config
                scope.launch {
                    if (previous == null || previous != config) {
                        Log.i(TAG, "Network config changed for ${StringUtils.networkIdToString(networkId)}, rebuilding VPN")
                        rebuildVpn()
                    }
                    publishNetworkStatuses()
                }
            }
            VirtualNetworkConfigOperation.VIRTUAL_NETWORK_CONFIG_OPERATION_DOWN,
            VirtualNetworkConfigOperation.VIRTUAL_NETWORK_CONFIG_OPERATION_DESTROY,
            -> {
                virtualNetworkConfigs.remove(networkId)
                scope.launch {
                    rebuildVpn()
                    publishNetworkStatuses()
                }
            }
        }
        return 0
    }

    override fun getVirtualNetworkConfig(networkId: Long): VirtualNetworkConfig? =
        virtualNetworkConfigs[networkId]

    override fun processVirtualNetworkFrame(
        now: Long,
        networkId: Long,
        sourceMac: Long,
        destMac: Long,
        etherType: Int,
        vlanId: Int,
        frameData: ByteArray,
    ): ResultCode = synchronized(nodeLock) {
        val ztNode = node ?: return ResultCode.RESULT_FATAL_ERROR_INTERNAL
        val deadline = longArrayOf(0)
        val result = ztNode.processVirtualNetworkFrame(
            now,
            networkId,
            sourceMac,
            destMac,
            etherType,
            vlanId,
            frameData,
            deadline,
        )
        applyDeadline(deadline[0])
        result
    }

    override fun processWirePacket(
        now: Long,
        localSocket: Long,
        remote: InetSocketAddress,
        packet: ByteArray,
    ): ResultCode = synchronized(nodeLock) {
        val ztNode = node ?: return ResultCode.RESULT_FATAL_ERROR_INTERNAL
        val deadline = longArrayOf(0)
        val result = ztNode.processWirePacket(now, localSocket, remote, packet, deadline)
        applyDeadline(deadline[0])
        result
    }

    override fun multicastSubscribe(networkId: Long, mac: Long, adi: Long): ResultCode =
        synchronized(nodeLock) {
            val ztNode = node ?: return ResultCode.RESULT_FATAL_ERROR_INTERNAL
            val result = if (adi != 0L) {
                ztNode.multicastSubscribe(networkId, mac, adi)
            } else {
                ztNode.multicastSubscribe(networkId, mac)
            }
            result
        }

    private fun applyDeadline(deadline: Long) {
        if (deadline != 0L) {
            synchronized(this) {
                nextBackgroundTaskDeadline = deadline
            }
        }
    }

    private fun drainBackgroundTasksLocked(maxRounds: Int = 8) {
        if (drainingBackground) return
        val ztNode = node ?: return
        drainingBackground = true
        try {
            repeat(maxRounds) {
                val now = System.currentTimeMillis()
                val due = synchronized(this) { nextBackgroundTaskDeadline }
                if (due > now && due != 0L) return
                val newDeadline = longArrayOf(0)
                val result = ztNode.processBackgroundTasks(now, newDeadline)
                applyDeadline(newDeadline[0])
                if (result != ResultCode.RESULT_OK) {
                    Log.e(TAG, "processBackgroundTasks failed: $result")
                    return
                }
            }
        } finally {
            drainingBackground = false
        }
    }

    override fun run() {
        Log.d(TAG, "ZeroTier background thread started")
        while (!Thread.currentThread().isInterrupted) {
            try {
                val taskDeadline = synchronized(this) { nextBackgroundTaskDeadline }
                val currentTime = System.currentTimeMillis()
                val cmp = taskDeadline.compareTo(currentTime)
                if (cmp <= 0) {
                    synchronized(nodeLock) {
                        drainBackgroundTasksLocked(maxRounds = 16)
                    }
                }
                Thread.sleep(if (cmp > 0) taskDeadline - currentTime else 100)
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Background thread error", e)
            }
        }
        Log.d(TAG, "ZeroTier background thread ended")
    }

    fun shutdown() {
        synchronized(this) {
            markStopped()
            udpThread?.interrupt()
            try {
                udpThread?.join(1000)
            } catch (_: InterruptedException) {
            }
            udpThread = null
            vpnThread?.interrupt()
            try {
                vpnThread?.join(1000)
            } catch (_: InterruptedException) {
            }
            vpnThread = null
            packetScheduler?.stop()
            packetScheduler = null
            datagramSocket?.close()
            datagramSocket = null
            tunTapAdapter?.interrupt()
            tunTapAdapter = null
            closeVpnSocket()
            node?.close()
            node = null
            virtualNetworkConfigs.clear()
            updateState {
                VpnServiceState(statusMessage = "Stopped")
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun refreshJoinedNetworks() {
        scope.launch {
            val enabled = app().database.networkDao().getAll().filter { it.isEnabled }
            val singleId = allowedVpnNetworkId
            if (singleId != null) {
                val normalized = ZerotierBNetwork.normalizeNetworkId(singleId)
                val mainIdLong = ZerotierBNetwork.parseNetworkIdLong(normalized)
                enabled.forEach { network ->
                    val id = network.networkIdLong()
                    if (id != mainIdLong) {
                        node?.leave(id)
                        packetScheduler?.unregisterNetwork(id)
                        virtualNetworkConfigs.remove(id)
                    }
                }
                networkSettings.clear()
                enabled
                    .filter { ZerotierBNetwork.normalizeNetworkId(it.networkId) == normalized }
                    .forEach { network ->
                        networkSettings[network.networkIdLong()] = network
                        writeNetworkLocalSettings(network)
                        joinNetworkInternal(network.networkIdLong())
                    }
            } else {
                networkSettings.clear()
                enabled.forEach { network ->
                    networkSettings[network.networkIdLong()] = network
                    writeNetworkLocalSettings(network)
                    joinNetworkInternal(network.networkIdLong())
                }
            }
        }
    }

    private fun joinNetwork(networkIdHex: String) {
        val normalized = ZerotierBNetwork.normalizeNetworkId(networkIdHex)
        allowedVpnNetworkId?.let { allowed ->
            if (ZerotierBNetwork.normalizeNetworkId(allowed) != normalized) {
                Log.i(TAG, "skip join $normalized — single-net VPN is ${ZerotierBNetwork.normalizeNetworkId(allowed)}")
                return
            }
        }
        scope.launch {
            val network = app().networkRepository.getById(
                ZerotierBNetwork.normalizeNetworkId(networkIdHex),
            ) ?: return@launch
            networkSettings[network.networkIdLong()] = network
            writeNetworkLocalSettings(network)
            joinNetworkInternal(network.networkIdLong())
        }
    }

    private fun joinNetworkInternal(networkId: Long) {
        Log.i(TAG, "Joining network ${StringUtils.networkIdToString(networkId)}")
        synchronized(nodeLock) {
            val ztNode = node
            if (ztNode == null) {
                Log.e(TAG, "join failed: node not running")
                return
            }
            val result = ztNode.join(networkId)
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "join failed for ${StringUtils.networkIdToString(networkId)}: $result")
            } else {
                packetScheduler?.registerNetwork(networkId)
            }
        }
    }

    private fun leaveNetwork(networkIdHex: String) {
        val networkId = ZerotierBNetwork.parseNetworkIdLong(networkIdHex)
        node?.leave(networkId)
        packetScheduler?.unregisterNetwork(networkId)
        virtualNetworkConfigs.remove(networkId)
        networkSettings.remove(networkId)
        scope.launch { rebuildVpn() }
    }

    private suspend fun rebuildVpn() = rebuildMutex.withLock {
        val adapter = tunTapAdapter ?: return@withLock
        val ztNode = node ?: return@withLock
        val scheduler = packetScheduler
        scheduler?.pause()
        try {
        if (virtualNetworkConfigs.isEmpty()) {
            closeVpnSocket()
            return@withLock
        }
        adapter.clearRouteMap()

        val allowedIdLong = allowedVpnNetworkId?.let { ZerotierBNetwork.parseNetworkIdLong(it) }
        val enabledIds = networkSettings.keys.toSet()
            .filter { allowedIdLong == null || it == allowedIdLong }
            .toSet()
        val activeConfigs = virtualNetworkConfigs.filterKeys { it in enabledIds }
        if (activeConfigs.isEmpty()) return@withLock

        val builder = Builder()
        var mtu = Int.MAX_VALUE
        var addressCount = 0
        val dnsServers = linkedSetOf<InetAddress>()
        val overlapWarnings = mutableListOf<String>()
        val addedRoutes = mutableMapOf<String, Pair<Int, Long>>()

        for ((networkId, config) in activeConfigs) {
            val settings = networkSettings[networkId] ?: continue
            if (config.status != VirtualNetworkStatus.NETWORK_STATUS_OK) continue

            for (address in config.assignedAddresses) {
                val ip = address.address
                val prefix = address.port
                val route = InetAddressUtils.addressToRoute(ip, prefix) ?: continue
                builder.addAddress(ip, prefix)
                builder.addRoute(route, prefix)
                addressCount++
                adapter.addRouteEntry(
                    RouteEntry(Route(route, prefix), networkId, settings.routePriority),
                )
                subscribeMulticast(ztNode, networkId, ip)
            }

            for (routeConfig in config.routes) {
                val target = routeConfig.target
                val via = routeConfig.via
                val targetAddress = target.address
                val targetPrefix = target.port
                val viaRoute = InetAddressUtils.addressToRoute(targetAddress, targetPrefix) ?: continue
                val v4Loopback = InetAddress.getByName("0.0.0.0")
                val v6Loopback = InetAddress.getByName("::")
                val routeViaZt = settings.allowDefault ||
                    (viaRoute != v4Loopback && viaRoute != v6Loopback)
                if (routeViaZt) {
                    val routeKey = "${viaRoute.hostAddress}/$targetPrefix"
                    val existing = addedRoutes[routeKey]
                    if (existing != null &&
                        existing.first == settings.routePriority &&
                        existing.second != networkId
                    ) {
                        overlapWarnings += routeKey
                    }
                    val shouldAdd = existing == null ||
                        targetPrefix > routePrefixFromKey(routeKey) ||
                        settings.routePriority < (networkSettings[existing.second]?.routePriority ?: Int.MAX_VALUE)
                    if (shouldAdd) {
                        builder.addRoute(viaRoute, targetPrefix)
                        val route = Route(viaRoute, targetPrefix).apply {
                            gateway = via?.address
                        }
                        adapter.addRouteEntry(
                            RouteEntry(route, networkId, settings.routePriority),
                        )
                        addedRoutes[routeKey] = settings.routePriority to networkId
                    }
                }
            }

            if (settings.allowDns) {
                config.dns?.servers?.forEach { dnsServers.add(it.address) }
            }
            if (config.mtu > 0) {
                mtu = minOf(mtu, config.mtu)
            }
        }

        if (addressCount == 0) {
            val pending = activeConfigs.values.count {
                it.status == VirtualNetworkStatus.NETWORK_STATUS_REQUESTING_CONFIGURATION
            }
            val denied = activeConfigs.values.count {
                it.status == VirtualNetworkStatus.NETWORK_STATUS_ACCESS_DENIED
            }
            val statusMessage = when {
                pending > 0 -> "Waiting for network config ($pending pending)"
                denied > 0 -> "Network access denied ($denied networks)"
                else -> "No active network addresses"
            }
            updateState { copy(statusMessage = statusMessage, overlappingRoutes = emptyList()) }
            Log.i(TAG, "Skipping VPN establish: no addresses ($statusMessage)")
            return@withLock
        }

        try {
            builder.addRoute(InetAddress.getByName("224.0.0.0"), 4)
        } catch (e: Exception) {
            Log.e(TAG, "Multicast route failed", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        dnsServers.forEach { builder.addDnsServer(it) }
        builder.setMtu(if (mtu == Int.MAX_VALUE) 2800 else mtu)
        builder.setSession("ZerotierB")
        builder.addDisallowedApplication(packageName)

        val okNetworkCount = activeConfigs.count { it.value.status == VirtualNetworkStatus.NETWORK_STATUS_OK }
        val newSocket = try {
            builder.establish()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "VPN establish failed: ${e.message}", e)
            updateState { copy(statusMessage = "VPN setup failed: ${e.message}") }
            return@withLock
        }
        if (newSocket == null) {
            updateState { copy(statusMessage = "VPN establish failed — grant VPN consent") }
            return@withLock
        }
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val underlying = cm?.allNetworks?.filter { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@filter false
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }?.toTypedArray()
            if (!underlying.isNullOrEmpty()) {
                setUnderlyingNetworks(underlying)
                lastUnderlyingNetworkHandle = underlying.first().networkHandle
            }
        } catch (e: Exception) {
            Log.w(TAG, "setUnderlyingNetworks failed: ${e.message}")
        }
        val newIn = FileInputStream(newSocket.fileDescriptor)
        val newOut = FileOutputStream(newSocket.fileDescriptor)

        val oldSocket = vpnSocket
        val oldIn = inStream
        val oldOut = outStream
        adapter.stopReceiveThread()
        vpnSocket = newSocket
        inStream = newIn
        outStream = newOut
        adapter.setFileStreams(newIn, newOut)
        try {
            oldOut?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing old TUN out: ${e.message}")
        }
        try {
            oldIn?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing old TUN in: ${e.message}")
        }
        try {
            oldSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing old VPN socket: ${e.message}")
        }
        adapter.startThreads()
        updateState {
            copy(
                statusMessage = "VPN active ($okNetworkCount networks)",
                overlappingRoutes = overlapWarnings.distinct(),
            )
        }
        startForegroundCompat(buildNotification("VPN active"))
        } finally {
            scheduler?.resume()
        }
    }

    private fun routePrefixFromKey(key: String): Int =
        key.substringAfter('/').toIntOrNull() ?: 0

    private fun subscribeMulticast(node: Node, networkId: Long, ip: InetAddress) {
        val raw = ip.address
        val (group, adi) = if (raw.size == 4) {
            InetAddressUtils.BROADCAST_MAC_ADDRESS to ByteBuffer.wrap(raw).int.toLong()
        } else {
            ByteBuffer.wrap(
                byteArrayOf(0, 0, 0x33, 0x33, 0xFF.toByte(), raw[13], raw[14], raw[15]),
            ).long to 0L
        }
        synchronized(nodeLock) {
            val result = if (adi != 0L) {
                node.multicastSubscribe(networkId, group, adi)
            } else {
                node.multicastSubscribe(networkId, group)
            }
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "multicastSubscribe failed: $result")
            }
        }
    }

    private fun closeVpnSocket() {
        try {
            inStream?.close()
            outStream?.close()
            vpnSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN socket", e)
        }
        inStream = null
        outStream = null
        vpnSocket = null
    }

    private fun writeNetworkLocalSettings(network: ZerotierBNetwork) {
        val dir = File(filesDir, "networks.d")
        dir.mkdirs()
        val file = File(dir, "${network.networkId}.local.conf")
        file.writeText(
            """
            allowManaged=${if (network.allowManaged) 1 else 0}
            allowGlobal=${if (network.allowGlobal) 1 else 0}
            allowDefault=${if (network.allowDefault) 1 else 0}
            allowDNS=${if (network.allowDns) 1 else 0}
            """.trimIndent(),
        )
    }

    private suspend fun publishNetworkStatuses() {
        val statuses = virtualNetworkConfigs.map { (id, config) ->
            NetworkRuntimeStatus(
                networkId = StringUtils.networkIdToString(id),
                joinStatus = vpnVirtualStatusToJoinStatus(config.status),
                assignedAddresses = config.assignedAddresses.mapNotNull {
                    formatAssignedCidr(it.address.hostAddress, it.port)
                },
                routes = config.routes.mapNotNull { routeConfig ->
                    val target = InetAddressUtils.addressToRoute(
                        routeConfig.target.address,
                        routeConfig.target.port,
                    ) ?: return@mapNotNull null
                    val cidr = "${target.hostAddress}/${routeConfig.target.port}"
                    formatRouteLine(cidr, routeConfig.via?.address?.hostAddress)
                },
                dnsServers = config.dns?.servers?.mapNotNull { it.address.hostAddress }.orEmpty(),
            )
        }
        updateState { copy(networkStatuses = statuses) }
    }

    private fun updateState(block: VpnServiceState.() -> VpnServiceState) {
        _state.update { it.block() }
    }

    private fun app(): ZerotierBApplication = application as ZerotierBApplication

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "ZerotierBVpnService"
        private const val CHANNEL_ID = "zerotierb_vpn"
        private const val NOTIFICATION_ID = 5919813

        const val ACTION_STOP = "com.brukb.zerotier.vpn.STOP"
        const val ACTION_JOIN = "com.brukb.zerotier.vpn.JOIN"
        const val ACTION_LEAVE = "com.brukb.zerotier.vpn.LEAVE"
        const val EXTRA_NETWORK_ID = "network_id"
        const val EXTRA_SINGLE_NETWORK_ID = "single_network_id"
        const val EXTRA_START_TOKEN = "start_token"

        private val _state = MutableStateFlow(VpnServiceState())
        val state: StateFlow<VpnServiceState> = _state.asStateFlow()

        @Volatile
        var lastUnderlyingNetworkHandle: Long? = null
            private set

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

        fun start(context: Context, singleNetworkId: String? = null) {
            val token = startCounter.incrementAndGet()
            val intent = Intent(context, ZerotierBVpnService::class.java).apply {
                putExtra(EXTRA_START_TOKEN, token)
                singleNetworkId?.let { putExtra(EXTRA_SINGLE_NETWORK_ID, it) }
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            markStopped()
            val intent = Intent(context, ZerotierBVpnService::class.java).apply {
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
                Log.w(TAG, "VPN stop timed out after ${timeoutMs}ms")
            }
            return stopped != null
        }

        fun joinNetwork(context: Context, networkId: String) {
            val intent = Intent(context, ZerotierBVpnService::class.java).apply {
                action = ACTION_JOIN
                putExtra(EXTRA_NETWORK_ID, networkId)
            }
            context.startService(intent)
        }

        fun leaveNetwork(context: Context, networkId: String) {
            val intent = Intent(context, ZerotierBVpnService::class.java).apply {
                action = ACTION_LEAVE
                putExtra(EXTRA_NETWORK_ID, networkId)
            }
            context.startService(intent)
        }
    }
}
