package com.brukb.zerotier.vpn

import com.brukb.zerotier.connection.NetworkRuntimeStatus
import com.brukb.zerotier.connection.NodeLifecycleStatus

data class VpnServiceState(
    val isRunning: Boolean = false,
    val nodeId: String = "",
    val statusMessage: String = "Stopped",
    val nodeLifecycle: NodeLifecycleStatus = NodeLifecycleStatus.STOPPED,
    val networkStatuses: List<NetworkRuntimeStatus> = emptyList(),
    val overlappingRoutes: List<String> = emptyList(),
)
