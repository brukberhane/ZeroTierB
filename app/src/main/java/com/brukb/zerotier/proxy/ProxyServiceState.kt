package com.brukb.zerotier.proxy

data class ProxyServiceState(
    val isRunning: Boolean = false,
    val httpProxyPort: Int? = null,
    val nodeId: String? = null,
    val statusMessage: String = "Stopped",
    val lastError: String? = null,
    val systemProxyActive: Boolean = false,
    val hasSecureSettingsPermission: Boolean = false,
)
