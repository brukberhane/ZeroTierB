package com.brukb.zerotier.ui

import com.brukb.zerotier.connection.PhysicalLink
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.proxy.ProxyServiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatusFormatTest {
    @Test
    fun formatLinkLine_allVariants() {
        assertEquals(
            "WiFi Home (VPN)",
            formatLinkLine(PhysicalLink.WifiKnown("Home", LinkMode.VPN)),
        )
        assertEquals("Unknown WiFi (PROXY)", formatLinkLine(PhysicalLink.WifiUnknown))
        assertEquals(
            "WiFi Cafe (unsaved, PROXY)",
            formatLinkLine(PhysicalLink.WifiUnsaved("Cafe")),
        )
        assertEquals("SIM 2 (PROXY)", formatLinkLine(PhysicalLink.Mobile(2, LinkMode.PROXY)))
        assertEquals("Other (OFF)", formatLinkLine(PhysicalLink.Other(LinkMode.OFF)))
        assertEquals("No link", formatLinkLine(PhysicalLink.None))
        assertEquals("No link", formatLinkLine(null))
    }

    @Test
    fun proxyStatusText_cases() {
        assertEquals(
            "System proxy 127.0.0.1:8080",
            proxyStatusText(
                ProxyServiceState(
                    isRunning = true,
                    httpProxyPort = 8080,
                    systemProxyActive = true,
                    hasSecureSettingsPermission = true,
                ),
            ),
        )
        assertEquals(
            "System proxy not granted",
            proxyStatusText(
                ProxyServiceState(
                    isRunning = true,
                    httpProxyPort = 8080,
                    systemProxyActive = false,
                    hasSecureSettingsPermission = false,
                ),
            ),
        )
        assertEquals(
            "System proxy inactive",
            proxyStatusText(
                ProxyServiceState(
                    isRunning = true,
                    httpProxyPort = 8080,
                    systemProxyActive = false,
                    hasSecureSettingsPermission = true,
                ),
            ),
        )
        assertNull(proxyStatusText(ProxyServiceState()))
    }
}
