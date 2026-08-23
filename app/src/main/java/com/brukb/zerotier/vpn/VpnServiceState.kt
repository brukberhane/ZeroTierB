package com.brukb.zerotier.vpn

data class NetworkRuntimeStatus(
    val networkId: String,
    val status: String,
)

data class VpnServiceState(
    val isRunning: Boolean = false,
    val nodeId: String = "",
    val statusMessage: String = "Stopped",
    val networkStatuses: List<NetworkRuntimeStatus> = emptyList(),
    val overlappingRoutes: List<String> = emptyList(),
)
