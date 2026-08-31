package com.brukb.zerotier.ztlib

import android.util.Log
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.zerotier.sockets.ZeroTierEventListener
import com.zerotier.sockets.ZeroTierNative
import com.zerotier.sockets.ZeroTierNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ZeroTierNodeManager(
    private val storagePath: String,
) {
    private val dispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "libzt-node").apply { isDaemon = true }
        }.asCoroutineDispatcher()

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val node = ZeroTierNode()
    private val initialized = AtomicBoolean(false)

    private val _state = MutableStateFlow(ZtNodeState())
    val state: StateFlow<ZtNodeState> = _state.asStateFlow()

    private val networkStatuses = mutableMapOf<Long, ZtNetworkStatus>()

    suspend fun <T> withNode(block: suspend () -> T): T = withContext(dispatcher) { block() }

    suspend fun initialize(): Result<Unit> = withNode {
        runCatching {
            if (initialized.compareAndSet(false, true)) {
                node.initFromStorage(storagePath)
                node.initSetEventHandler(eventListener)
            }
        }
    }

    suspend fun start(
        shouldAbort: () -> Boolean = { false },
    ): Result<Long> = withNode {
        runCatching {
            check(initialized.get()) { "Node not initialized" }
            val result = node.start()
            Log.i(TAG, "zts_node_start result=$result")
            if (result == ZeroTierNative.ZTS_ERR_SERVICE) {
                // Native node state is process-global; a previous service instance
                // may have left it running. Reuse instead of tearing down.
                Log.i(TAG, "node already running — reusing")
            } else {
                check(result == ZeroTierNative.ZTS_ERR_OK) { "zts_node_start failed: $result" }
            }
            if (shouldAbort()) {
                error("Node start aborted")
            }

            // zts_node_start returns before native `_node` exists. join() on a
            // null Node SIGSEGVs in pthread_mutex_lock. Wait for NODE_UP
            // (node.id != 0), not ONLINE (roots) — that can take >15s on cell.
            val nodeId = withTimeoutOrNull(NODE_UP_TIMEOUT_MS) {
                while (node.id == 0L) {
                    if (shouldAbort()) return@withTimeoutOrNull 0L
                    ZeroTierNative.zts_util_delay(50)
                }
                node.id
            } ?: 0L
            check(nodeId != 0L) {
                if (shouldAbort()) {
                    "Node start aborted"
                } else {
                    "Node did not come up within ${NODE_UP_TIMEOUT_MS}ms"
                }
            }

            _state.value = _state.value.copy(
                isOnline = node.isOnline,
                nodeId = nodeId,
            )
            nodeId
        }.onFailure { error ->
            Log.e(TAG, "start failed", error)
            _state.value = _state.value.copy(lastError = error.message)
        }
    }

    suspend fun stop(): Result<Unit> = withNode {
        runCatching {
            val result = node.stop()
            Log.i(TAG, "zts_node_stop result=$result")
            networkStatuses.clear()
            _state.value = ZtNodeState()
        }
    }

    suspend fun join(networkId: Long, config: ZerotierBNetwork? = null): Result<Unit> = withNode {
        runCatching {
            check(node.id != 0L) { "Node not up — cannot join" }
            config?.let {
                val settingsResult = ZtNetworkQuery.setNetworkSettings(
                    networkId,
                    it.allowManaged,
                    it.allowGlobal,
                    it.allowDefault,
                )
                if (settingsResult != ZeroTierNative.ZTS_ERR_OK) {
                    Log.w(TAG, "zts_net_set_settings failed: $settingsResult — joining anyway")
                }
            }
            updateNetworkStatus(networkId, ZtNetworkStatus.Status.JOINING)
            val result = node.join(networkId)
            check(result == ZeroTierNative.ZTS_ERR_OK) { "zts_net_join failed: $result" }
        }
    }

    suspend fun leave(networkId: Long): Result<Unit> = withNode {
        runCatching {
            node.leave(networkId)
            networkStatuses.remove(networkId)
            publishNetworkState()
        }
    }

    suspend fun waitForNetworkReady(
        networkId: Long,
        timeoutMs: Long = 120_000,
        shouldAbort: () -> Boolean = { false },
    ): Result<ZtNetworkStatus> = withNode {
        runCatching {
            val ready = withTimeoutOrNull(timeoutMs) {
                while (!node.isNetworkTransportReady(networkId)) {
                    if (shouldAbort()) return@withTimeoutOrNull false
                    ZeroTierNative.zts_util_delay(100)
                }
                true
            } ?: false
            check(ready) {
                if (shouldAbort()) {
                    "Network $networkId join aborted"
                } else {
                    "Network $networkId not ready within ${timeoutMs}ms"
                }
            }
            refreshNetworkInfo(networkId)
            networkStatuses[networkId] ?: error("Network info missing after ready")
        }
    }

    suspend fun refreshNetworkInfo(networkId: Long): ZtNetworkStatus = withNode {
        val statusCode = ZeroTierNative.zts_net_get_status(networkId)
        val status = mapNetworkStatus(statusCode)
        val addresses = queryAddresses(networkId)
        val routes = queryRoutes(networkId)
        val dnsServers = ZtNetworkQuery.queryDnsServers(networkId)
        val dnsDomain = ZtNetworkQuery.queryDnsDomain(networkId)
        val info = ZtNetworkStatus(
            networkId = networkId,
            status = status,
            assignedAddresses = addresses,
            routes = routes,
            dnsServers = dnsServers,
            dnsDomain = dnsDomain,
        )
        networkStatuses[networkId] = info
        publishNetworkState()
        info
    }

    fun getNetworkStatus(networkId: Long): ZtNetworkStatus? = networkStatuses[networkId]

    private fun queryAddresses(networkId: Long): List<String> {
        val fromCore = ZtNetworkQuery.queryAssignedCidrs(networkId)
        if (fromCore.isNotEmpty()) return fromCore
        val ipv4 = node.getIPv4Address(networkId)?.hostAddress
        val ipv6 = node.getIPv6Address(networkId)?.hostAddress
        return listOfNotNull(
            ipv4?.let { "$it/32" },
            ipv6?.let { "$it/128" },
        )
    }

    private fun queryRoutes(networkId: Long): List<String> =
        ZtNetworkQuery.queryManagedRouteCidrs(networkId).distinct()

    private fun mapNetworkStatus(code: Int): ZtNetworkStatus.Status {
        return when (code) {
            0 -> ZtNetworkStatus.Status.REQUESTING_CONFIG
            1 -> ZtNetworkStatus.Status.OK
            2 -> ZtNetworkStatus.Status.ACCESS_DENIED
            3 -> ZtNetworkStatus.Status.NOT_FOUND
            4 -> ZtNetworkStatus.Status.PORT_ERROR
            5 -> ZtNetworkStatus.Status.CLIENT_TOO_OLD
            else -> ZtNetworkStatus.Status.UNKNOWN
        }
    }

    private fun updateNetworkStatus(networkId: Long, status: ZtNetworkStatus.Status) {
        val existing = networkStatuses[networkId]
        networkStatuses[networkId] = (existing ?: ZtNetworkStatus(networkId, status)).copy(status = status)
        publishNetworkState()
    }

    private fun publishNetworkState() {
        _state.value = _state.value.copy(networks = networkStatuses.toMap())
    }

    private val eventListener = ZeroTierEventListener { id, eventCode ->
        when (eventCode) {
            ZeroTierNative.ZTS_EVENT_NODE_UP -> {
                val nodeId = node.id
                _state.value = _state.value.copy(nodeId = nodeId.takeIf { it != 0L } ?: _state.value.nodeId)
                Log.i(TAG, "node UP id=${formatNodeId(nodeId)}")
            }
            ZeroTierNative.ZTS_EVENT_NODE_ONLINE -> {
                _state.value = _state.value.copy(isOnline = true, nodeId = node.id)
                Log.i(TAG, "node ONLINE id=${formatNodeId(node.id)}")
            }
            ZeroTierNative.ZTS_EVENT_NODE_OFFLINE -> {
                _state.value = _state.value.copy(isOnline = false)
                Log.i(TAG, "node OFFLINE")
            }
            ZeroTierNative.ZTS_EVENT_NODE_DOWN -> {
                _state.value = _state.value.copy(isOnline = false)
                Log.i(TAG, "node DOWN")
            }
            ZeroTierNative.ZTS_EVENT_NODE_FATAL_ERROR -> {
                _state.value = _state.value.copy(lastError = "Fatal node error")
                Log.e(TAG, "node FATAL")
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_OK,
            ZeroTierNative.ZTS_EVENT_NETWORK_READY_IP4,
            ZeroTierNative.ZTS_EVENT_NETWORK_READY_IP6,
            ZeroTierNative.ZTS_EVENT_NETWORK_READY_IP4_IP6,
            ZeroTierNative.ZTS_EVENT_NETWORK_UPDATE,
            ZeroTierNative.ZTS_EVENT_ROUTE_ADDED,
            ZeroTierNative.ZTS_EVENT_ROUTE_REMOVED,
            ZeroTierNative.ZTS_EVENT_ADDR_ADDED_IP4,
            ZeroTierNative.ZTS_EVENT_ADDR_ADDED_IP6,
            ZeroTierNative.ZTS_EVENT_ADDR_REMOVED_IP4,
            ZeroTierNative.ZTS_EVENT_ADDR_REMOVED_IP6,
            -> {
                Log.i(TAG, "net event=$eventCode id=${formatNodeId(id)}")
                refreshNetworkInfoAsync(id)
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_REQ_CONFIG -> {
                Log.i(TAG, "net REQ_CONFIG ${formatNodeId(id)}")
                updateNetworkStatus(id, ZtNetworkStatus.Status.REQUESTING_CONFIG)
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_ACCESS_DENIED -> {
                Log.i(TAG, "net ACCESS_DENIED ${formatNodeId(id)}")
                updateNetworkStatus(id, ZtNetworkStatus.Status.ACCESS_DENIED)
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_NOT_FOUND -> {
                Log.i(TAG, "net NOT_FOUND ${formatNodeId(id)}")
                updateNetworkStatus(id, ZtNetworkStatus.Status.NOT_FOUND)
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_CLIENT_TOO_OLD -> {
                Log.i(TAG, "net CLIENT_TOO_OLD ${formatNodeId(id)}")
                updateNetworkStatus(id, ZtNetworkStatus.Status.CLIENT_TOO_OLD)
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_DOWN -> {
                Log.i(TAG, "net DOWN ${formatNodeId(id)}")
                updateNetworkStatus(id, ZtNetworkStatus.Status.DOWN)
            }
            ZeroTierNative.ZTS_EVENT_PEER_DIRECT,
            ZeroTierNative.ZTS_EVENT_PEER_RELAY,
            ZeroTierNative.ZTS_EVENT_PEER_UNREACHABLE,
            ZeroTierNative.ZTS_EVENT_PEER_PATH_DISCOVERED,
            ZeroTierNative.ZTS_EVENT_PEER_PATH_DEAD,
            -> {
                logPeerEvent(id, eventCode)
            }
        }
    }

    private fun logPeerEvent(peerId: Long, eventCode: Int) {
        val kind = when (eventCode) {
            ZeroTierNative.ZTS_EVENT_PEER_DIRECT -> "DIRECT"
            ZeroTierNative.ZTS_EVENT_PEER_RELAY -> "RELAY"
            ZeroTierNative.ZTS_EVENT_PEER_UNREACHABLE -> "UNREACHABLE"
            ZeroTierNative.ZTS_EVENT_PEER_PATH_DISCOVERED -> "PATH_DISCOVERED"
            ZeroTierNative.ZTS_EVENT_PEER_PATH_DEAD -> "PATH_DEAD"
            else -> eventCode.toString()
        }
        Log.i(TAG, "peer $kind ${formatNodeId(peerId)}")
        if (eventCode == ZeroTierNative.ZTS_EVENT_PEER_UNREACHABLE ||
            eventCode == ZeroTierNative.ZTS_EVENT_PEER_PATH_DEAD
        ) {
            return
        }
        scope.launch {
            runCatching {
                val paths = ZtNetworkQuery.queryPathCount(peerId)
                Log.i(TAG, "peer ${formatNodeId(peerId)} paths=$paths")
            }.onFailure { Log.w(TAG, "path count failed for ${formatNodeId(peerId)}", it) }
        }
    }

    private fun refreshNetworkInfoAsync(networkId: Long) {
        scope.launch {
            runCatching { refreshNetworkInfo(networkId) }
                .onFailure { Log.w(TAG, "refreshNetworkInfo failed for $networkId", it) }
        }
    }

    companion object {
        private const val TAG = "ZeroTierNodeManager"
        private const val NODE_UP_TIMEOUT_MS = 10_000L

        fun formatNodeId(nodeId: Long): String = String.format("%010x", nodeId)
    }
}
