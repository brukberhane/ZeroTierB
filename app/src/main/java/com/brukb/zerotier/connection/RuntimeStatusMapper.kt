package com.brukb.zerotier.connection

import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.ProxyServiceState
import com.brukb.zerotier.vpn.VpnServiceState
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import com.brukb.zerotier.ztlib.ZtNodeState
import com.zerotier.sdk.VirtualNetworkStatus
import com.zerotier.sdk.util.StringUtils

fun ztStatusToJoinStatus(
    status: ZtNetworkStatus.Status,
    everOnline: Boolean = true,
): JoinStatus = when (status) {
    ZtNetworkStatus.Status.JOINING -> JoinStatus.JOINING
    ZtNetworkStatus.Status.REQUESTING_CONFIG -> JoinStatus.REQUESTING_CONFIG
    ZtNetworkStatus.Status.OK -> JoinStatus.OK
    ZtNetworkStatus.Status.ACCESS_DENIED ->
        if (everOnline) JoinStatus.ACCESS_DENIED else JoinStatus.JOINING
    ZtNetworkStatus.Status.NOT_FOUND -> JoinStatus.NOT_FOUND
    ZtNetworkStatus.Status.DOWN -> JoinStatus.DOWN
    ZtNetworkStatus.Status.PORT_ERROR,
    ZtNetworkStatus.Status.CLIENT_TOO_OLD,
    -> JoinStatus.ERROR
    ZtNetworkStatus.Status.UNKNOWN -> JoinStatus.UNKNOWN
}

fun ztNetworkToRuntime(
    networkId: Long,
    zt: ZtNetworkStatus,
    everOnline: Boolean = true,
): NetworkRuntimeStatus {
    val hexId = ZerotierBNetwork.normalizeNetworkId(StringUtils.networkIdToString(networkId))
    return NetworkRuntimeStatus(
        networkId = hexId,
        joinStatus = ztStatusToJoinStatus(zt.status, everOnline),
        assignedAddresses = zt.assignedAddresses,
        routes = zt.routes,
        dnsServers = zt.dnsServers,
    )
}

fun vpnVirtualStatusToJoinStatus(status: VirtualNetworkStatus): JoinStatus = when (status) {
    VirtualNetworkStatus.NETWORK_STATUS_REQUESTING_CONFIGURATION -> JoinStatus.REQUESTING_CONFIG
    VirtualNetworkStatus.NETWORK_STATUS_OK -> JoinStatus.OK
    VirtualNetworkStatus.NETWORK_STATUS_ACCESS_DENIED -> JoinStatus.ACCESS_DENIED
    VirtualNetworkStatus.NETWORK_STATUS_NOT_FOUND -> JoinStatus.NOT_FOUND
    VirtualNetworkStatus.NETWORK_STATUS_PORT_ERROR,
    VirtualNetworkStatus.NETWORK_STATUS_CLIENT_TOO_OLD,
    VirtualNetworkStatus.NETWORK_STATUS_AUTHENTICATION_REQUIRED,
    -> JoinStatus.ERROR
    else -> JoinStatus.UNKNOWN
}

fun resolveNetworkRuntime(
    runtime: Runtime?,
    proxy: ProxyServiceState,
    vpn: VpnServiceState,
    networkId: String,
): NetworkRuntimeStatus? {
    val normalized = ZerotierBNetwork.normalizeNetworkId(networkId)
    val statuses = when (runtime) {
        Runtime.PROXY -> proxy.networkStatuses
        Runtime.VPN -> vpn.networkStatuses
        Runtime.OFF, null -> return null
    }
    return statuses.firstOrNull {
        ZerotierBNetwork.normalizeNetworkId(it.networkId) == normalized
    }
}

fun resolveNodeLifecycle(
    runtime: Runtime?,
    proxy: ProxyServiceState,
    vpn: VpnServiceState,
): NodeLifecycleStatus = when (runtime) {
    Runtime.PROXY -> proxy.nodeLifecycle
    Runtime.VPN -> vpn.nodeLifecycle
    Runtime.OFF, null -> NodeLifecycleStatus.STOPPED
}

/** Map libzt node snapshot onto the same lifecycle enum VPN uses. */
fun ztNodeStateToLifecycle(
    nodeState: ZtNodeState,
    pausedDoze: Boolean,
): NodeLifecycleStatus {
    if (pausedDoze) return NodeLifecycleStatus.PAUSED_DOZE
    if (!nodeState.lastError.isNullOrBlank()) return NodeLifecycleStatus.ERROR
    if (nodeState.isOnline) return NodeLifecycleStatus.ONLINE
    return NodeLifecycleStatus.STARTING
}
