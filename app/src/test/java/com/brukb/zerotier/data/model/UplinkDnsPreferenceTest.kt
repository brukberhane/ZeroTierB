package com.brukb.zerotier.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UplinkDnsPreferenceTest {
    @Test
    fun parse_null_wifiFirst() {
        assertEquals(UplinkDnsPreference.WIFI_FIRST, UplinkDnsPreference.parse(null))
    }

    @Test
    fun parse_blank_wifiFirst() {
        assertEquals(UplinkDnsPreference.WIFI_FIRST, UplinkDnsPreference.parse(""))
        assertEquals(UplinkDnsPreference.WIFI_FIRST, UplinkDnsPreference.parse("  "))
    }

    @Test
    fun parse_junk_wifiFirst() {
        assertEquals(UplinkDnsPreference.WIFI_FIRST, UplinkDnsPreference.parse("nope"))
    }

    @Test
    fun parse_cellular_caseInsensitive() {
        assertEquals(UplinkDnsPreference.CELLULAR_FIRST, UplinkDnsPreference.parse("cellular_first"))
        assertEquals(UplinkDnsPreference.CELLULAR_FIRST, UplinkDnsPreference.parse("CELLULAR_FIRST"))
    }
}
