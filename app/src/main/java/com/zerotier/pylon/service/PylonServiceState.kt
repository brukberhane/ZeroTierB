package com.zerotier.pylon.service

enum class NodeStatus {
    STOPPED,
    STARTING,
    ONLINE,
    ERROR,
}

enum class NetworkJoinStatus {
    NONE,
    JOINING,
    OK,
    ACCESS_DENIED,
    ERROR,
}

data class NetworkRuntimeStatus(
    val networkId: String,
    val joinStatus: NetworkJoinStatus = NetworkJoinStatus.NONE,
    val assignedAddresses: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val dnsDomain: String = "",
)

data class PylonServiceState(
    val isRunning: Boolean = false,
    val nodeStatus: NodeStatus = NodeStatus.STOPPED,
    val nodeId: String? = null,
    val networkJoinStatus: NetworkJoinStatus = NetworkJoinStatus.NONE,
    val activeNetworkId: String? = null,
    val networkStatuses: Map<String, NetworkRuntimeStatus> = emptyMap(),
    val httpProxyPort: Int? = null,
    val socks5ProxyPort: Int? = null,
    val socks5Enabled: Boolean = false,
    val proxyEnabled: Boolean = true,
    val systemProxyActive: Boolean = false,
    val hasSecureSettingsPermission: Boolean = false,
    val statusMessage: String = "Stopped",
    val logs: List<String> = emptyList(),
)
