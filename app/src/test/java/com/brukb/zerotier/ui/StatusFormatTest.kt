package com.brukb.zerotier.ui

import com.brukb.zerotier.connection.JoinStatus
import com.brukb.zerotier.connection.NetworkRuntimeStatus
import com.brukb.zerotier.connection.NodeLifecycleStatus
import com.brukb.zerotier.connection.PhysicalLink
import com.brukb.zerotier.connection.Runtime
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.proxy.ProxyServiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun joinStatusLabel_allValuesNonEmpty() {
        JoinStatus.entries.forEach { status ->
            assert(joinStatusLabel(status).isNotBlank())
        }
    }

    @Test
    fun nodeLifecycleLabel_allValuesNonEmpty() {
        NodeLifecycleStatus.entries.forEach { status ->
            assert(nodeLifecycleLabel(status).isNotBlank())
        }
    }

    @Test
    fun nodeLifecycleLabel_pausedDozeDiffersFromStopped() {
        assertNotEquals(
            nodeLifecycleLabel(NodeLifecycleStatus.STOPPED),
            nodeLifecycleLabel(NodeLifecycleStatus.PAUSED_DOZE),
        )
    }

    @Test
    fun joinStatusChipRole_okDiffersFromJoining() {
        assertNotEquals(
            joinStatusChipRole(JoinStatus.OK),
            joinStatusChipRole(JoinStatus.JOINING),
        )
    }

    @Test
    fun joinStatusChipRole_allValues() {
        assertEquals(JoinStatusChipRole.SUCCESS, joinStatusChipRole(JoinStatus.OK))
        assertEquals(JoinStatusChipRole.NEUTRAL, joinStatusChipRole(JoinStatus.JOINING))
        assertEquals(JoinStatusChipRole.NEUTRAL, joinStatusChipRole(JoinStatus.REQUESTING_CONFIG))
        JoinStatus.entries.filter {
            it != JoinStatus.OK && it != JoinStatus.JOINING && it != JoinStatus.REQUESTING_CONFIG
        }.forEach { status ->
            assertEquals(JoinStatusChipRole.ERROR, joinStatusChipRole(status))
        }
    }

    @Test
    fun joinChipStatus_table() {
        fun rt(status: JoinStatus?) = status?.let {
            NetworkRuntimeStatus(networkId = "abc", joinStatus = it)
        }

        assertEquals(
            JoinStatus.OK,
            joinChipStatus(NodeLifecycleStatus.ONLINE, Runtime.PROXY, true, rt(JoinStatus.OK)),
        )
        assertEquals(
            JoinStatus.JOINING,
            joinChipStatus(NodeLifecycleStatus.ONLINE, Runtime.PROXY, true, null),
        )
        assertEquals(
            JoinStatus.ACCESS_DENIED,
            joinChipStatus(
                NodeLifecycleStatus.ONLINE,
                Runtime.VPN,
                true,
                rt(JoinStatus.ACCESS_DENIED),
            ),
        )
        assertNull(
            joinChipStatus(NodeLifecycleStatus.ONLINE, Runtime.PROXY, false, rt(JoinStatus.OK)),
        )
        assertNull(
            joinChipStatus(NodeLifecycleStatus.ONLINE, Runtime.OFF, true, rt(JoinStatus.OK)),
        )
        assertNull(
            joinChipStatus(NodeLifecycleStatus.ONLINE, null, true, rt(JoinStatus.OK)),
        )
        assertNull(
            joinChipStatus(NodeLifecycleStatus.PAUSED_DOZE, Runtime.PROXY, true, rt(JoinStatus.OK)),
        )
        assertNull(
            joinChipStatus(NodeLifecycleStatus.PAUSED_DOZE, Runtime.PROXY, true, null),
        )
        assertNull(
            joinChipStatus(NodeLifecycleStatus.STOPPED, Runtime.PROXY, true, rt(JoinStatus.JOINING)),
        )
        assertEquals(
            JoinStatus.JOINING,
            joinChipStatus(NodeLifecycleStatus.STARTING, Runtime.PROXY, true, null),
        )
        assertEquals(
            JoinStatus.ERROR,
            joinChipStatus(
                NodeLifecycleStatus.ERROR,
                Runtime.VPN,
                true,
                rt(JoinStatus.ERROR),
            ),
        )
    }

    @Test
    fun runtimeHeadline_autoShowsResolved() {
        assertEquals("AUTO (PROXY)", runtimeHeadline(GlobalMode.AUTO, Runtime.PROXY))
        assertEquals("VPN", runtimeHeadline(GlobalMode.VPN, Runtime.VPN))
        assertEquals("OFF", runtimeHeadline(GlobalMode.OFF, null))
    }
}
