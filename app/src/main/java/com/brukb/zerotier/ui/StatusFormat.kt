package com.brukb.zerotier.ui

import com.brukb.zerotier.connection.PhysicalLink
import com.brukb.zerotier.proxy.ProxyServiceState

fun canSaveSsid(link: PhysicalLink?): Boolean = link is PhysicalLink.WifiUnsaved

fun unsavedWifiSsid(link: PhysicalLink?): String? =
    (link as? PhysicalLink.WifiUnsaved)?.ssid

fun formatLinkLine(link: PhysicalLink?): String = when (link) {
    is PhysicalLink.WifiKnown -> "WiFi ${link.ssid} (${link.mode.name})"
    is PhysicalLink.WifiUnsaved -> "WiFi ${link.ssid} (unsaved, PROXY)"
    PhysicalLink.WifiUnknown -> "Unknown WiFi (PROXY)"
    is PhysicalLink.Mobile -> "SIM ${link.subscriptionId} (${link.mode.name})"
    is PhysicalLink.Other -> "Other (${link.mode.name})"
    PhysicalLink.None, null -> "No link"
}

fun proxyStatusText(proxy: ProxyServiceState): String? {
    val port = proxy.httpProxyPort
    return when {
        proxy.systemProxyActive && port != null && port > 0 ->
            "System proxy 127.0.0.1:$port"
        proxy.isRunning && !proxy.hasSecureSettingsPermission ->
            "System proxy not granted"
        proxy.isRunning && !proxy.systemProxyActive ->
            "System proxy inactive"
        else -> null
    }
}
