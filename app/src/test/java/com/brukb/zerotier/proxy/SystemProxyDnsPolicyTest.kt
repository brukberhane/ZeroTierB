package com.brukb.zerotier.proxy

import com.brukb.zerotier.connection.PhysicalLink
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.LinkKind
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.LinkProfile
import com.brukb.zerotier.data.model.UplinkDnsPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemProxyDnsPolicyTest {
    private val skipHealOff = SystemProxyDnsPolicy(
        skipUplinkDnsProbe = true,
        healEnabled = false,
        preference = UplinkDnsPreference.CELLULAR_FIRST,
    )

    @Test
    fun normalized_skipOff_wanStandard() {
        val stored = SystemProxyDnsPolicy(
            skipUplinkDnsProbe = false,
            healEnabled = false,
            preference = UplinkDnsPreference.CELLULAR_FIRST,
        )
        assertEquals(SystemProxyDnsPolicy.WAN_STANDARD, stored.normalized())
    }

    @Test
    fun normalized_skipOn_keepsHealOff() {
        assertEquals(skipHealOff, skipHealOff.normalized())
    }

    @Test
    fun proxyMode_usesGlobalNormalized() {
        val policy = resolveSystemProxyDnsPolicy(
            globalMode = GlobalMode.PROXY,
            link = PhysicalLink.None,
            globalPrefs = skipHealOff,
            profileForLink = wifiProxyRow(),
        )
        assertEquals(skipHealOff, policy)
    }

    @Test
    fun vpnMode_wanStandard() {
        val policy = resolveSystemProxyDnsPolicy(
            globalMode = GlobalMode.VPN,
            link = PhysicalLink.WifiKnown("Home", LinkMode.PROXY),
            globalPrefs = skipHealOff,
            profileForLink = wifiProxyRow(),
        )
        assertEquals(SystemProxyDnsPolicy.WAN_STANDARD, policy)
    }

    @Test
    fun auto_wifiKnownProxy_usesRow() {
        val row = wifiProxyRow(
            skip = true,
            heal = false,
            pref = UplinkDnsPreference.CELLULAR_FIRST,
        )
        val policy = resolveSystemProxyDnsPolicy(
            globalMode = GlobalMode.AUTO,
            link = PhysicalLink.WifiKnown("Home", LinkMode.PROXY),
            globalPrefs = SystemProxyDnsPolicy.WAN_STANDARD,
            profileForLink = row,
        )
        assertTrue(policy.skipUplinkDnsProbe)
        assertFalse(policy.healEnabled)
        assertEquals(UplinkDnsPreference.CELLULAR_FIRST, policy.preference)
    }

    @Test
    fun auto_wifiKnownVpn_usesGlobal() {
        val row = wifiProxyRow(mode = LinkMode.VPN, skip = true, heal = false)
        val policy = resolveSystemProxyDnsPolicy(
            globalMode = GlobalMode.AUTO,
            link = PhysicalLink.WifiKnown("Home", LinkMode.VPN),
            globalPrefs = skipHealOff,
            profileForLink = row,
        )
        assertEquals(skipHealOff, policy)
    }

    @Test
    fun auto_unsaved_usesGlobal() {
        val policy = resolveSystemProxyDnsPolicy(
            globalMode = GlobalMode.AUTO,
            link = PhysicalLink.WifiUnsaved("Cafe"),
            globalPrefs = skipHealOff,
            profileForLink = wifiProxyRow(),
        )
        assertEquals(skipHealOff, policy)
    }

    @Test
    fun auto_unknown_usesGlobal() {
        val policy = resolveSystemProxyDnsPolicy(
            globalMode = GlobalMode.AUTO,
            link = PhysicalLink.WifiUnknown,
            globalPrefs = skipHealOff,
            profileForLink = null,
        )
        assertEquals(skipHealOff, policy)
    }

    @Test
    fun auto_wifiKnownProxy_skipOff_normalizesHeal() {
        val row = wifiProxyRow(
            skip = false,
            heal = false,
            pref = UplinkDnsPreference.CELLULAR_FIRST,
        )
        val policy = resolveSystemProxyDnsPolicy(
            globalMode = GlobalMode.AUTO,
            link = PhysicalLink.WifiKnown("Home", LinkMode.PROXY),
            globalPrefs = skipHealOff,
            profileForLink = row,
        )
        assertEquals(SystemProxyDnsPolicy.WAN_STANDARD, policy)
    }

    private fun wifiProxyRow(
        mode: LinkMode = LinkMode.PROXY,
        skip: Boolean = true,
        heal: Boolean = false,
        pref: UplinkDnsPreference = UplinkDnsPreference.CELLULAR_FIRST,
    ) = LinkProfile(
        id = LinkProfile.wifiId("Home"),
        kind = LinkKind.WIFI,
        mode = mode,
        ssid = "Home",
        skipUplinkDnsProbe = skip,
        uplinkDnsHealEnabled = heal,
        uplinkDnsPreference = pref,
    )
}
