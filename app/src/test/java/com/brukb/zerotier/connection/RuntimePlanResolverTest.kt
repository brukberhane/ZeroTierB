package com.brukb.zerotier.connection

import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.ZerotierBNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimePlanResolverTest {
    private val twoNets = listOf(
        net("aaaa", createdAt = 100),
        net("bbbb", createdAt = 50, pinned = true),
    )
    private val createdAtSorted = listOf(
        net("cccc", createdAt = 100),
        net("dddd", createdAt = 50),
    )
    private val oneNet = listOf(net("eeee", createdAt = 1))

    @Test
    fun case01_globalOffIgnoresLinkVpn() {
        val plan = resolve(GlobalMode.OFF, PhysicalLink.WifiKnown("Home", LinkMode.VPN), true, twoNets)
        assertEquals(Runtime.OFF, plan.runtime)
        assertTrue(plan.joinNetworkIds.isEmpty())
        assertFalse(plan.vpnConsentMissing)
        assertNull(plan.vpnNetworkId)
        assertTrue(plan.reason.isNotBlank())
    }

    @Test
    fun case02_globalProxyIgnoresNone() {
        val plan = resolve(GlobalMode.PROXY, PhysicalLink.None, false, twoNets)
        assertEquals(Runtime.PROXY, plan.runtime)
        assertEquals(twoNets.map { it.networkId }, plan.joinNetworkIds)
        assertNull(plan.vpnNetworkId)
        assertFalse(plan.vpnConsentMissing)
    }

    @Test
    fun case03_globalProxyEmptyEnabledStillProxy() {
        val plan = resolve(GlobalMode.PROXY, PhysicalLink.Mobile(1, LinkMode.PROXY), false, emptyList())
        assertEquals(Runtime.PROXY, plan.runtime)
        assertTrue(plan.joinNetworkIds.isEmpty())
    }

    @Test
    fun case04_globalVpnPinnedMain() {
        val plan = resolve(GlobalMode.VPN, PhysicalLink.Other(LinkMode.OFF), true, twoNets)
        assertEquals(Runtime.VPN, plan.runtime)
        assertEquals(twoNets[1].networkId, plan.vpnNetworkId)
        assertEquals(listOf(twoNets[1].networkId), plan.joinNetworkIds)
        assertFalse(plan.vpnConsentMissing)
    }

    @Test
    fun case05_globalVpnOldestCreatedAt() {
        val plan = resolve(GlobalMode.VPN, PhysicalLink.WifiUnknown, true, createdAtSorted)
        assertEquals(Runtime.VPN, plan.runtime)
        assertEquals(createdAtSorted[1].networkId, plan.vpnNetworkId)
    }

    @Test
    fun case06_globalVpnConsentMissingFallsToProxy() {
        val plan = resolve(GlobalMode.VPN, PhysicalLink.None, false, twoNets)
        assertEquals(Runtime.PROXY, plan.runtime)
        assertEquals(twoNets.map { it.networkId }, plan.joinNetworkIds)
        assertTrue(plan.vpnConsentMissing)
        assertNull(plan.vpnNetworkId)
    }

    @Test
    fun case07_globalVpnNoNetsNoConsent() {
        val plan = resolve(GlobalMode.VPN, PhysicalLink.None, false, emptyList())
        assertEquals(Runtime.OFF, plan.runtime)
        assertTrue(plan.vpnConsentMissing)
        assertTrue(plan.joinNetworkIds.isEmpty())
    }

    @Test
    fun case08_globalVpnNoNetsWithConsent() {
        val plan = resolve(GlobalMode.VPN, PhysicalLink.None, true, emptyList())
        assertEquals(Runtime.OFF, plan.runtime)
        assertFalse(plan.vpnConsentMissing)
        assertTrue(plan.reason.contains("no enabled", ignoreCase = true))
    }

    @Test
    fun case09_autoNoneIsOff() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.None, true, twoNets)
        assertEquals(Runtime.OFF, plan.runtime)
    }

    @Test
    fun case10_autoWifiUnknownIsProxy() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.WifiUnknown, false, oneNet)
        assertEquals(Runtime.PROXY, plan.runtime)
        assertEquals(oneNet.map { it.networkId }, plan.joinNetworkIds)
    }

    @Test
    fun case10b_autoWifiUnsavedIsProxy() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.WifiUnsaved("Cafe"), false, oneNet)
        assertEquals(Runtime.PROXY, plan.runtime)
        assertTrue(plan.reason.contains("unsaved ssid=Cafe"))
    }

    @Test
    fun case11_autoWifiKnownOff() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.WifiKnown("Home", LinkMode.OFF), true, twoNets)
        assertEquals(Runtime.OFF, plan.runtime)
    }

    @Test
    fun case12_autoWifiKnownVpnPinned() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.WifiKnown("Home", LinkMode.VPN), true, twoNets)
        assertEquals(Runtime.VPN, plan.runtime)
        assertEquals(twoNets[1].networkId, plan.vpnNetworkId)
        assertTrue(plan.reason.contains("ssid=Home"))
    }

    @Test
    fun case13_autoWifiKnownVpnNoConsent() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.WifiKnown("Home", LinkMode.VPN), false, oneNet)
        assertEquals(Runtime.PROXY, plan.runtime)
        assertTrue(plan.vpnConsentMissing)
    }

    @Test
    fun case14_autoMobileProxy() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.Mobile(2, LinkMode.PROXY), false, oneNet)
        assertEquals(Runtime.PROXY, plan.runtime)
        assertTrue(plan.reason.contains("sub=2"))
    }

    @Test
    fun case15_autoMobileVpn() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.Mobile(2, LinkMode.VPN), true, oneNet)
        assertEquals(Runtime.VPN, plan.runtime)
        assertEquals(oneNet[0].networkId, plan.vpnNetworkId)
    }

    @Test
    fun case16_autoOtherVpnNoConsent() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.Other(LinkMode.VPN), false, oneNet)
        assertEquals(Runtime.PROXY, plan.runtime)
        assertTrue(plan.vpnConsentMissing)
    }

    @Test
    fun case17_autoOtherOff() {
        val plan = resolve(GlobalMode.AUTO, PhysicalLink.Other(LinkMode.OFF), true, oneNet)
        assertEquals(Runtime.OFF, plan.runtime)
    }

    private fun resolve(
        global: GlobalMode,
        link: PhysicalLink,
        consent: Boolean,
        enabled: List<ZerotierBNetwork>,
    ) = RuntimePlanResolver.resolve(global, link, consent, enabled)

    private fun net(
        id: String,
        createdAt: Long = 0L,
        pinned: Boolean = false,
    ) = ZerotierBNetwork(
        networkId = id.padStart(16, '0'),
        name = id,
        createdAt = createdAt,
        isPinnedMain = pinned,
    )
}
