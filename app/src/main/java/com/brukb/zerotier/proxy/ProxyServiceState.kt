package com.brukb.zerotier.proxy

import com.brukb.zerotier.connection.NetworkRuntimeStatus
import com.brukb.zerotier.connection.NodeLifecycleStatus

data class ProxyServiceState(
    val isRunning: Boolean = false,
    val httpProxyPort: Int? = null,
    val nodeId: String? = null,
    val statusMessage: String = "Stopped",
    val lastError: String? = null,
    val systemProxyActive: Boolean = false,
    val hasSecureSettingsPermission: Boolean = false,
    val nodeLifecycle: NodeLifecycleStatus = NodeLifecycleStatus.STOPPED,
    val networkStatuses: List<NetworkRuntimeStatus> = emptyList(),
)
