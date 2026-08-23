package com.brukb.zerotier.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class RouteSelectorTest {
    @Test
    fun longestPrefixWins() {
        val dest = InetAddress.getByName("10.1.2.3")
        val net24 = routeEntry("10.1.2.0", 24, 1L, 0)
        val net16 = routeEntry("10.1.0.0", 16, 2L, 0)
        val selected = RouteSelector.select(listOf(net16, net24), dest)
        assertEquals(1L, selected?.networkId)
    }

    @Test
    fun samePrefixLowerPriorityWins() {
        val dest = InetAddress.getByName("10.1.2.3")
        val low = routeEntry("10.1.2.0", 24, 1L, 0)
        val high = routeEntry("10.1.2.0", 24, 2L, 5)
        val selected = RouteSelector.select(listOf(high, low), dest)
        assertEquals(1L, selected?.networkId)
    }

    @Test
    fun samePrefixAndPriorityLowerNetworkIdWins() {
        val dest = InetAddress.getByName("10.1.2.3")
        val a = routeEntry("10.1.2.0", 24, 0x1111L, 0)
        val b = routeEntry("10.1.2.0", 24, 0x2222L, 0)
        val selected = RouteSelector.select(listOf(b, a), dest)
        assertEquals(0x1111L, selected?.networkId)
    }

    @Test
    fun noMatchReturnsNull() {
        val dest = InetAddress.getByName("192.168.1.1")
        val entry = routeEntry("10.0.0.0", 8, 1L, 0)
        assertNull(RouteSelector.select(listOf(entry), dest))
    }

    private fun routeEntry(cidrBase: String, prefix: Int, networkId: Long, priority: Int): RouteEntry {
        val address = InetAddress.getByName(cidrBase)
        return RouteEntry(Route(address, prefix), networkId, priority)
    }
}
