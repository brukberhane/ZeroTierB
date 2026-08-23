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

    suspend fun start(timeoutMs: Long = 60_000): Result<Long> = withNode {
        runCatching {
            check(initialized.get()) { "Node not initialized" }
            val result = node.start()
            check(result == ZeroTierNative.ZTS_ERR_OK) { "zts_node_start failed: $result" }

            val online = withTimeoutOrNull(timeoutMs) {
                while (!node.isOnline) {
                    ZeroTierNative.zts_util_delay(50)
                }
                true
            } ?: false

            check(online) { "Node did not come online within ${timeoutMs}ms" }

            val nodeId = node.id
            _state.value = _state.value.copy(isOnline = true, nodeId = nodeId)
            nodeId
        }.onFailure { error ->
            Log.e(TAG, "start failed", error)
            _state.value = _state.value.copy(lastError = error.message)
        }
    }

    suspend fun stop(): Result<Unit> = withNode {
        runCatching {
            node.stop()
            networkStatuses.clear()
            _state.value = ZtNodeState()
        }
    }

    suspend fun join(networkId: Long, config: ZerotierBNetwork? = null): Result<Unit> = withNode {
        runCatching {
            config?.let {
                val settingsResult = ZtNetworkQuery.setNetworkSettings(
                    networkId,
                    it.allowManaged,
                    it.allowGlobal,
                    it.allowDefault,
                )
                check(settingsResult == ZeroTierNative.ZTS_ERR_OK) {
                    "zts_net_set_settings failed: $settingsResult"
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

    suspend fun waitForNetworkReady(networkId: Long, timeoutMs: Long = 120_000): Result<ZtNetworkStatus> = withNode {
        runCatching {
            val ready = withTimeoutOrNull(timeoutMs) {
                while (!node.isNetworkTransportReady(networkId)) {
                    ZeroTierNative.zts_util_delay(100)
                }
                true
            } ?: false
            check(ready) { "Network $networkId not ready within ${timeoutMs}ms" }
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
            1 -> ZtNetworkStatus.Status.OK
            2 -> ZtNetworkStatus.Status.ACCESS_DENIED
            3 -> ZtNetworkStatus.Status.NOT_FOUND
            0 -> ZtNetworkStatus.Status.JOINING
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

    private val eventListener = ZeroTierEventListener { networkId, eventCode ->
        when (eventCode) {
            ZeroTierNative.ZTS_EVENT_NODE_ONLINE -> {
                _state.value = _state.value.copy(isOnline = true, nodeId = node.id)
            }
            ZeroTierNative.ZTS_EVENT_NODE_OFFLINE -> {
                _state.value = _state.value.copy(isOnline = false)
            }
            ZeroTierNative.ZTS_EVENT_NODE_FATAL_ERROR -> {
                _state.value = _state.value.copy(lastError = "Fatal node error")
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_OK,
            ZeroTierNative.ZTS_EVENT_NETWORK_READY_IP4,
            ZeroTierNative.ZTS_EVENT_NETWORK_READY_IP6,
            ZeroTierNative.ZTS_EVENT_NETWORK_UPDATE,
            ZeroTierNative.ZTS_EVENT_ROUTE_ADDED,
            ZeroTierNative.ZTS_EVENT_ROUTE_REMOVED,
            ZeroTierNative.ZTS_EVENT_ADDR_ADDED_IP4,
            ZeroTierNative.ZTS_EVENT_ADDR_ADDED_IP6,
            -> {
                refreshNetworkInfoAsync(networkId)
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_ACCESS_DENIED -> {
                updateNetworkStatus(networkId, ZtNetworkStatus.Status.ACCESS_DENIED)
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_NOT_FOUND -> {
                updateNetworkStatus(networkId, ZtNetworkStatus.Status.NOT_FOUND)
            }
            ZeroTierNative.ZTS_EVENT_NETWORK_DOWN -> {
                updateNetworkStatus(networkId, ZtNetworkStatus.Status.DOWN)
            }
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

        fun formatNodeId(nodeId: Long): String = String.format("%010x", nodeId)
    }
}
