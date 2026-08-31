package com.brukb.zerotier.connection

enum class JoinStatus {
    JOINING,
    REQUESTING_CONFIG,
    OK,
    ACCESS_DENIED,
    NOT_FOUND,
    DOWN,
    UNKNOWN,
    ERROR,
}

enum class NodeLifecycleStatus {
    STOPPED,
    STARTING,
    ONLINE,
    PAUSED_DOZE,
    ERROR,
}

data class NetworkRuntimeStatus(
    val networkId: String,
    val joinStatus: JoinStatus,
    val assignedAddresses: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
)
