package com.brukb.zerotier.proxy

import com.brukb.zerotier.connection.PhysicalLink
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.LinkProfile
import com.brukb.zerotier.data.model.UplinkDnsPreference

data class SystemProxyDnsPolicy(
    val skipUplinkDnsProbe: Boolean,
    val healEnabled: Boolean,
    val preference: UplinkDnsPreference,
) {
    fun normalized(): SystemProxyDnsPolicy =
        if (!skipUplinkDnsProbe) WAN_STANDARD else this

    companion object {
        val WAN_STANDARD = SystemProxyDnsPolicy(
            skipUplinkDnsProbe = false,
            healEnabled = true,
            preference = UplinkDnsPreference.WIFI_FIRST,
        )
    }
}

fun LinkProfile.toSystemProxyDnsPolicy(): SystemProxyDnsPolicy =
    SystemProxyDnsPolicy(
        skipUplinkDnsProbe = skipUplinkDnsProbe,
        healEnabled = uplinkDnsHealEnabled,
        preference = uplinkDnsPreference,
    )

fun profileForPhysicalLink(link: PhysicalLink, profiles: List<LinkProfile>): LinkProfile? {
    val id = when (link) {
        is PhysicalLink.WifiKnown -> LinkProfile.wifiId(link.ssid)
        is PhysicalLink.Mobile -> LinkProfile.mobileId(link.subscriptionId)
        is PhysicalLink.Other -> LinkProfile.OTHER_ID
        is PhysicalLink.WifiUnsaved,
        is PhysicalLink.WifiUnknown,
        is PhysicalLink.None,
        -> null
    } ?: return null
    return profiles.firstOrNull { it.id == id }
}

fun resolveSystemProxyDnsPolicy(
    globalMode: GlobalMode,
    link: PhysicalLink,
    globalPrefs: SystemProxyDnsPolicy,
    profileForLink: LinkProfile?,
): SystemProxyDnsPolicy {
    if (globalMode == GlobalMode.PROXY) return globalPrefs.normalized()
    if (globalMode != GlobalMode.AUTO) return SystemProxyDnsPolicy.WAN_STANDARD
    return when (link) {
        is PhysicalLink.WifiKnown,
        is PhysicalLink.Mobile,
        is PhysicalLink.Other,
        -> {
            if (profileForLink != null && profileForLink.mode == LinkMode.PROXY) {
                profileForLink.toSystemProxyDnsPolicy().normalized()
            } else {
                globalPrefs.normalized()
            }
        }
        is PhysicalLink.WifiUnsaved,
        is PhysicalLink.WifiUnknown,
        is PhysicalLink.None,
        -> globalPrefs.normalized()
    }
}
