package com.brukb.zerotier.ui

import androidx.annotation.StringRes
import com.brukb.zerotier.R
import com.brukb.zerotier.connection.JoinStatus
import com.brukb.zerotier.connection.NetworkRuntimeStatus
import com.brukb.zerotier.connection.NodeLifecycleStatus
import com.brukb.zerotier.connection.PhysicalLink
import com.brukb.zerotier.connection.Runtime
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.proxy.ProxyServiceState

enum class JoinStatusChipRole {
    NEUTRAL,
    SUCCESS,
    ERROR,
}

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

fun runtimeHeadline(globalMode: GlobalMode, runtime: Runtime?): String =
    if (globalMode == GlobalMode.AUTO) {
        "AUTO (${runtime?.name ?: "OFF"})"
    } else {
        runtime?.name ?: globalMode.name
    }

/** null = hide chip. */
fun joinChipStatus(
    lifecycle: NodeLifecycleStatus,
    runtime: Runtime?,
    networkEnabled: Boolean,
    networkRuntime: NetworkRuntimeStatus?,
): JoinStatus? {
    if (!networkEnabled) return null
    if (runtime == null || runtime == Runtime.OFF) return null
    if (lifecycle == NodeLifecycleStatus.PAUSED_DOZE ||
        lifecycle == NodeLifecycleStatus.STOPPED
    ) {
        return null
    }
    return networkRuntime?.joinStatus ?: JoinStatus.JOINING
}

fun joinStatusLabel(status: JoinStatus): String = when (status) {
    JoinStatus.JOINING -> "Joining"
    JoinStatus.REQUESTING_CONFIG -> "Requesting config"
    JoinStatus.OK -> "Connected"
    JoinStatus.ACCESS_DENIED -> "Access denied"
    JoinStatus.NOT_FOUND -> "Not found"
    JoinStatus.DOWN -> "Down"
    JoinStatus.UNKNOWN -> "Unknown"
    JoinStatus.ERROR -> "Error"
}

fun nodeLifecycleLabel(status: NodeLifecycleStatus): String = when (status) {
    NodeLifecycleStatus.STOPPED -> "Stopped"
    NodeLifecycleStatus.STARTING -> "Starting"
    NodeLifecycleStatus.ONLINE -> "Online"
    NodeLifecycleStatus.PAUSED_DOZE -> "Paused (Doze)"
    NodeLifecycleStatus.ERROR -> "Error"
}

@StringRes
fun joinStatusLabelRes(status: JoinStatus): Int = when (status) {
    JoinStatus.JOINING -> R.string.join_status_joining
    JoinStatus.REQUESTING_CONFIG -> R.string.join_status_requesting_config
    JoinStatus.OK -> R.string.join_status_ok
    JoinStatus.ACCESS_DENIED -> R.string.join_status_access_denied
    JoinStatus.NOT_FOUND -> R.string.join_status_not_found
    JoinStatus.DOWN -> R.string.join_status_down
    JoinStatus.UNKNOWN -> R.string.join_status_unknown
    JoinStatus.ERROR -> R.string.join_status_error
}

@StringRes
fun nodeLifecycleLabelRes(status: NodeLifecycleStatus): Int = when (status) {
    NodeLifecycleStatus.STOPPED -> R.string.lifecycle_stopped
    NodeLifecycleStatus.STARTING -> R.string.lifecycle_starting
    NodeLifecycleStatus.ONLINE -> R.string.lifecycle_online
    NodeLifecycleStatus.PAUSED_DOZE -> R.string.lifecycle_paused_doze
    NodeLifecycleStatus.ERROR -> R.string.lifecycle_error
}

fun joinStatusChipRole(status: JoinStatus): JoinStatusChipRole = when (status) {
    JoinStatus.OK -> JoinStatusChipRole.SUCCESS
    JoinStatus.JOINING,
    JoinStatus.REQUESTING_CONFIG,
    -> JoinStatusChipRole.NEUTRAL
    JoinStatus.ACCESS_DENIED,
    JoinStatus.NOT_FOUND,
    JoinStatus.DOWN,
    JoinStatus.UNKNOWN,
    JoinStatus.ERROR,
    -> JoinStatusChipRole.ERROR
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
