package com.brukb.zerotier.connection

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
}
