package com.brukb.zerotier.proxy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class SystemProxyEnablePolicyTest {
    @Test
    fun probeSucceeded_emptyFalse() {
        assertFalse(SystemProxyEnablePolicy.probeSucceeded(emptyList()))
    }

    @Test
    fun probeSucceeded_oneIpv4True() {
        val addr = InetAddress.getByAddress(
            byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34.toByte()),
        )
        assertTrue(SystemProxyEnablePolicy.probeSucceeded(listOf(addr)))
    }

    @Test
    fun probeHosts_notCaptivePortalNames() {
        assertEquals(2, SystemProxyEnablePolicy.PROBE_HOSTS.size)
        for (host in SystemProxyEnablePolicy.PROBE_HOSTS) {
            assertFalse(host.contains("captive", ignoreCase = true))
            assertFalse(host.contains("generate_204", ignoreCase = true))
            assertFalse(host.contains("gstatic", ignoreCase = true))
        }
    }
}
