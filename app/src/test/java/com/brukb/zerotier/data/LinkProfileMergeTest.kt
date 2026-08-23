package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.LinkKind
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.LinkProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkProfileMergeTest {
    @Test
    fun newMobileDefaultsToProxy() {
        val merged = LinkProfile.mergeMobile(
            existing = null,
            subscriptionId = 2,
            simSlotIndex = 1,
            label = "T-Mobile",
            iccId = "icc",
        )
        assertEquals("mobile-2", merged.id)
        assertEquals(LinkKind.MOBILE, merged.kind)
        assertEquals(LinkMode.PROXY, merged.mode)
        assertEquals(2, merged.subscriptionId)
        assertEquals(1, merged.simSlotIndex)
        assertEquals("T-Mobile", merged.label)
        assertEquals("icc", merged.iccId)
    }

    @Test
    fun upsertKeepsExistingModeAndUpdatesLabel() {
        val existing = LinkProfile(
            id = "mobile-2",
            kind = LinkKind.MOBILE,
            mode = LinkMode.VPN,
            subscriptionId = 2,
            simSlotIndex = 0,
            label = "Old",
            iccId = "old-icc",
        )
        val merged = LinkProfile.mergeMobile(
            existing = existing,
            subscriptionId = 2,
            simSlotIndex = 1,
            label = "New",
            iccId = null,
        )
        assertEquals(LinkMode.VPN, merged.mode)
        assertEquals("New", merged.label)
        assertEquals(1, merged.simSlotIndex)
        assertEquals("old-icc", merged.iccId)
    }

    @Test
    fun mergeWifi_newDefaultsToProxy() {
        val merged = LinkProfile.mergeWifi(null, "HomeWiFi", LinkMode.PROXY)
        assertEquals("wifi-HomeWiFi", merged.id)
        assertEquals(LinkKind.WIFI, merged.kind)
        assertEquals(LinkMode.PROXY, merged.mode)
        assertEquals("HomeWiFi", merged.ssid)
    }

    @Test
    fun mergeWifi_existingKeepsMode() {
        val existing = LinkProfile(
            id = "wifi-HomeWiFi",
            kind = LinkKind.WIFI,
            mode = LinkMode.VPN,
            ssid = "HomeWiFi",
        )
        val merged = LinkProfile.mergeWifi(existing, "HomeWiFi", LinkMode.PROXY)
        assertEquals(LinkMode.VPN, merged.mode)
        assertEquals(existing, merged)
    }

    @Test
    fun seedOther() {
        val other = LinkProfile.seedOther()
        assertEquals(LinkProfile.OTHER_ID, other.id)
        assertEquals(LinkKind.OTHER, other.kind)
        assertEquals(LinkMode.PROXY, other.mode)
    }
}
