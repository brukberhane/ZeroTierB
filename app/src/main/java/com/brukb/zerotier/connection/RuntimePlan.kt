package com.brukb.zerotier.connection

enum class Runtime {
    OFF,
    PROXY,
    VPN,
}

data class RuntimePlan(
    val runtime: Runtime,
    val reason: String,
    val vpnNetworkId: String?,
    val joinNetworkIds: List<String>,
    val vpnConsentMissing: Boolean,
)
