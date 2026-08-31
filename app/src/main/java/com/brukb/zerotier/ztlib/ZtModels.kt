package com.brukb.zerotier.ztlib

data class ZtNetworkStatus(
    val networkId: Long,
    val status: Status,
    val name: String = "",
    val assignedAddresses: List<String> = emptyList(),
    val routes: List<String> = emptyList(),
    val dnsServers: List<String> = emptyList(),
    val dnsDomain: String = "",
) {
    enum class Status {
        UNKNOWN,
        OK,
        ACCESS_DENIED,
        NOT_FOUND,
        DOWN,
        JOINING,
        REQUESTING_CONFIG,
        PORT_ERROR,
        CLIENT_TOO_OLD,
    }
}

data class ZtNodeState(
    val isOnline: Boolean = false,
    val nodeId: Long? = null,
    val networks: Map<Long, ZtNetworkStatus> = emptyMap(),
    val lastError: String? = null,
)
