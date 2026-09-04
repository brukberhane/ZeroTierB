package com.brukb.zerotier.proxy.dns

import com.brukb.zerotier.data.model.UplinkDnsPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UplinkNetworkPickerTest {
    @Test
    fun empty_null() {
        assertNull(UplinkNetworkPicker.pick(emptyList(), UplinkDnsPreference.WIFI_FIRST))
    }

    @Test
    fun vpnOnly_null() {
        assertNull(UplinkNetworkPicker.pick(listOf(vpn(9)), UplinkDnsPreference.WIFI_FIRST))
    }

    @Test
    fun wifiOnly_wifiFirst() {
        assertEquals(1L, UplinkNetworkPicker.pick(listOf(wifi(1)), UplinkDnsPreference.WIFI_FIRST)?.id)
    }

    @Test
    fun wifiOnly_cellFirst_stillWifi() {
        assertEquals(
            1L,
            UplinkNetworkPicker.pick(listOf(wifi(1)), UplinkDnsPreference.CELLULAR_FIRST)?.id,
        )
    }

    @Test
    fun cellOnly_cellFirst() {
        assertEquals(
            2L,
            UplinkNetworkPicker.pick(listOf(cell(2)), UplinkDnsPreference.CELLULAR_FIRST)?.id,
        )
    }

    @Test
    fun cellOnly_wifiFirst_stillCell() {
        assertEquals(2L, UplinkNetworkPicker.pick(listOf(cell(2)), UplinkDnsPreference.WIFI_FIRST)?.id)
    }

    @Test
    fun both_wifiFirst() {
        assertEquals(
            1L,
            UplinkNetworkPicker.pick(listOf(wifi(1), cell(2)), UplinkDnsPreference.WIFI_FIRST)?.id,
        )
    }

    @Test
    fun both_cellFirst() {
        assertEquals(
            2L,
            UplinkNetworkPicker.pick(listOf(wifi(1), cell(2)), UplinkDnsPreference.CELLULAR_FIRST)?.id,
        )
    }

    @Test
    fun otherOnly() {
        assertEquals(3L, UplinkNetworkPicker.pick(listOf(other(3)), UplinkDnsPreference.WIFI_FIRST)?.id)
    }

    @Test
    fun wifiPlusOther_wifiFirst() {
        assertEquals(
            1L,
            UplinkNetworkPicker.pick(listOf(wifi(1), other(3)), UplinkDnsPreference.WIFI_FIRST)?.id,
        )
    }

    @Test
    fun cellPlusOther_wifiFirst() {
        assertEquals(
            2L,
            UplinkNetworkPicker.pick(listOf(cell(2), other(3)), UplinkDnsPreference.WIFI_FIRST)?.id,
        )
    }

    @Test
    fun internetBeatsNoInternetWifi() {
        val picked = UplinkNetworkPicker.pick(
            listOf(wifiNoInet(1), cell(2)),
            UplinkDnsPreference.WIFI_FIRST,
        )
        assertEquals(2L, picked?.id)
    }

    @Test
    fun noInternetPool_picksWifi() {
        val picked = UplinkNetworkPicker.pick(
            listOf(wifiNoInet(1), cellNoInet(2)),
            UplinkDnsPreference.WIFI_FIRST,
        )
        assertEquals(1L, picked?.id)
    }

    @Test
    fun vpnStripped() {
        assertEquals(
            1L,
            UplinkNetworkPicker.pick(listOf(vpn(9), wifi(1)), UplinkDnsPreference.WIFI_FIRST)?.id,
        )
    }

    private fun wifi(id: Long) = UplinkCandidate(
        id = id,
        isVpn = false,
        isWifi = true,
        isCellular = false,
        hasInternet = true,
    )

    private fun cell(id: Long) = UplinkCandidate(
        id = id,
        isVpn = false,
        isWifi = false,
        isCellular = true,
        hasInternet = true,
    )

    private fun other(id: Long) = UplinkCandidate(
        id = id,
        isVpn = false,
        isWifi = false,
        isCellular = false,
        hasInternet = true,
    )

    private fun vpn(id: Long) = UplinkCandidate(
        id = id,
        isVpn = true,
        isWifi = false,
        isCellular = false,
        hasInternet = true,
    )

    private fun wifiNoInet(id: Long) = UplinkCandidate(
        id = id,
        isVpn = false,
        isWifi = true,
        isCellular = false,
        hasInternet = false,
    )

    private fun cellNoInet(id: Long) = UplinkCandidate(
        id = id,
        isVpn = false,
        isWifi = false,
        isCellular = true,
        hasInternet = false,
    )
}
