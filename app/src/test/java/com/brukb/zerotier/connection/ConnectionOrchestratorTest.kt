package com.brukb.zerotier.connection

import com.brukb.zerotier.data.LivePlanetSource
import com.brukb.zerotier.data.RootsFingerprint
import com.brukb.zerotier.data.RootsRestart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ConnectionOrchestratorTest {
    @Test
    fun runtimePlan_sameVpnPlan_isEqual() {
        val a = RuntimePlan(Runtime.VPN, "r", "net1", listOf("net1"), false)
        val b = RuntimePlan(Runtime.VPN, "r", "net1", listOf("net1"), false)
        assertEquals(a, b)
    }

    @Test
    fun runtimePlan_differentVpnNetworkId_notEqual() {
        val a = RuntimePlan(Runtime.VPN, "r", "net1", listOf("net1"), false)
        val b = RuntimePlan(Runtime.VPN, "r", "net2", listOf("net2"), false)
        assertNotEquals(a, b)
    }

    @Test
    fun runtimePlan_sameProxyPlan_isEqual() {
        val a = RuntimePlan(Runtime.PROXY, "r", null, listOf("n1", "n2"), false)
        val b = RuntimePlan(Runtime.PROXY, "r", null, listOf("n1", "n2"), false)
        assertEquals(a, b)
    }

    @Test
    fun runtimePlan_differentJoinNetworkIds_notEqual() {
        val a = RuntimePlan(Runtime.PROXY, "r", null, listOf("n1"), false)
        val b = RuntimePlan(Runtime.PROXY, "r", null, listOf("n1", "n2"), false)
        assertNotEquals(a, b)
    }

    @Test
    fun runtimePlan_offVsProxy_notEqual() {
        val off = RuntimePlan(Runtime.OFF, "r", null, emptyList(), false)
        val proxy = RuntimePlan(Runtime.PROXY, "r", null, listOf("n1"), false)
        assertNotEquals(off, proxy)
    }

    @Test
    fun proxyJoinSetRequiresRestart_table() {
        assertEquals(
            false,
            ConnectionOrchestrator.proxyJoinSetRequiresRestart(
                proxyRunning = false,
                lastJoinNetworkIds = listOf("n1"),
                nextJoinNetworkIds = listOf("n1", "n2"),
            ),
        )
        assertEquals(
            false,
            ConnectionOrchestrator.proxyJoinSetRequiresRestart(
                proxyRunning = true,
                lastJoinNetworkIds = listOf("n1"),
                nextJoinNetworkIds = listOf("n1"),
            ),
        )
        assertEquals(
            true,
            ConnectionOrchestrator.proxyJoinSetRequiresRestart(
                proxyRunning = true,
                lastJoinNetworkIds = listOf("n1"),
                nextJoinNetworkIds = listOf("n1", "n2"),
            ),
        )
        assertEquals(
            true,
            ConnectionOrchestrator.proxyJoinSetRequiresRestart(
                proxyRunning = true,
                lastJoinNetworkIds = null,
                nextJoinNetworkIds = listOf("n1"),
            ),
        )
    }

    @Test
    fun rootsRestart_requiresRestart_whenRunningAndFingerprintChanges() {
        val before = RootsFingerprint(LivePlanetSource.EARTH, listOf("n1"), 0L)
        val after = RootsFingerprint(LivePlanetSource.EARTH, listOf("n1", "n2"), 0L)
        assertEquals(false, RootsRestart.requiresRestart(false, before, after))
        assertEquals(false, RootsRestart.requiresRestart(true, before, before))
        assertEquals(true, RootsRestart.requiresRestart(true, before, after))
        assertEquals(
            true,
            RootsRestart.requiresRestart(
                true,
                before,
                RootsFingerprint(LivePlanetSource.DUMMY, listOf("n1"), 0L),
            ),
        )
    }
}
