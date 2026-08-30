package com.brukb.zerotier.proxy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpPrefixTest {
    @Test
    fun v4ContainsInSubnet() {
        val prefix = IpPrefix.parse("10.1.2.0/24")
        assertTrue(prefix.contains("10.1.2.9"))
        assertFalse(prefix.contains("10.1.3.1"))
    }

    @Test
    fun v6ContainsInSubnet() {
        val prefix = IpPrefix.parse("2001:db8::/32")
        assertTrue(prefix.contains("2001:db8:1::1"))
        assertFalse(prefix.contains("2001:db9::1"))
    }

    @Test
    fun mismatchedFamilyDoesNotContain() {
        val v4 = IpPrefix.parse("10.0.0.0/8")
        assertFalse(v4.contains("2001:db8::1"))
    }

    @Test
    fun isIpLiteral_v4() {
        assertTrue(IpPrefix.isIpLiteral("10.1.2.3"))
        assertFalse(IpPrefix.isIpLiteral("calibre.local"))
        assertFalse(IpPrefix.isIpLiteral("10.1.2"))
        assertFalse(IpPrefix.isIpLiteral("10.1.2.256"))
    }

    @Test
    fun isIpLiteral_v6() {
        assertTrue(IpPrefix.isIpLiteral("2001:db8::1"))
        assertTrue(IpPrefix.isIpLiteral("[2001:db8::1]"))
    }

    @Test
    fun hostnameDoesNotContain() {
        val prefix = IpPrefix.parse("10.0.0.0/8")
        assertFalse(prefix.contains("example.com"))
    }

    @Test
    fun hostBitsSetStillMatchesNetwork() {
        val prefix = IpPrefix.parse("10.1.2.3/24")
        assertTrue(prefix.contains("10.1.2.9"))
    }
}
