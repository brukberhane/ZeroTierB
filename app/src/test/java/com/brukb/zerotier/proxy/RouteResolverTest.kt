package com.brukb.zerotier.proxy

import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class RouteResolverTest {
    private val resolver = RouteResolver()

    @Before
    fun clear() {
        resolver.clear()
    }

    @Test
    fun assignedAddressMatches() {
        val net = net("aaaa", routePriority = 0)
        addNet(net, assigned = listOf("10.1.0.5/32"))
        val decision = resolver.resolveIpString("10.1.0.5")
        assertTrue(decision.useZeroTier)
        assertEquals(net.networkIdLong(), decision.networkId)
    }

    @Test
    fun longestPrefixWins() {
        val broad = net("aaaa", routePriority = 0)
        val narrow = net("bbbb", routePriority = 0)
        addNet(broad, routes = listOf("10.0.0.0/8"))
        addNet(narrow, routes = listOf("10.1.0.0/16"))
        val decision = resolver.resolveIpString("10.1.2.3")
        assertEquals(narrow.networkIdLong(), decision.networkId)
    }

    @Test
    fun samePrefixLowerRoutePriorityWins() {
        val high = net("aaaa", routePriority = 5)
        val low = net("bbbb", routePriority = 1)
        addNet(high, routes = listOf("10.1.0.0/16"))
        addNet(low, routes = listOf("10.1.0.0/16"))
        val decision = resolver.resolveIpString("10.1.2.3")
        assertEquals(low.networkIdLong(), decision.networkId)
    }

    @Test
    fun allowManagedFalseIgnoresRoutes() {
        val net = net("aaaa", allowManaged = false)
        addNet(net, routes = listOf("10.2.0.0/16"))
        val decision = resolver.resolveIpString("10.2.1.1")
        assertFalse(decision.useZeroTier)
    }

    @Test
    fun defaultRouteRespectsAllowDefault() {
        val net = net("aaaa", allowDefault = false)
        addNet(net, routes = listOf("0.0.0.0/0"))
        assertFalse(resolver.resolveIpString("8.8.8.8").useZeroTier)

        resolver.clear()
        val net2 = net("bbbb", allowDefault = true)
        addNet(net2, routes = listOf("0.0.0.0/0"))
        assertTrue(resolver.resolveIpString("8.8.8.8").useZeroTier)
    }

    @Test
    fun publicRouteRespectsAllowGlobal() {
        val net = net("aaaa", allowGlobal = false)
        addNet(net, routes = listOf("203.0.113.0/24"))
        assertFalse(resolver.resolveIpString("203.0.113.50").useZeroTier)
    }

    @Test
    fun privateRouteIncludedWhenAllowGlobalFalse() {
        val net = net("aaaa", allowGlobal = false)
        addNet(net, routes = listOf("10.50.0.0/16"))
        assertTrue(resolver.resolveIpString("10.50.1.1").useZeroTier)
    }

    @Test
    fun emptyResolverOutside() {
        val decision = resolver.resolveIpString("10.0.0.1")
        assertFalse(decision.useZeroTier)
        assertFalse(decision.block)
    }

    @Test
    fun resolveHostUsesResolvedAddresses() {
        val net = net("aaaa")
        addNet(net, assigned = listOf("192.168.1.10/32"))
        val decision = resolver.resolveHost(
            "host.example",
            listOf(InetAddress.getByName("192.168.1.10")),
        )
        assertTrue(decision.useZeroTier)
    }

    private fun addNet(
        network: ZerotierBNetwork,
        assigned: List<String> = emptyList(),
        routes: List<String> = emptyList(),
    ) {
        val status = ZtNetworkStatus(
            networkId = network.networkIdLong(),
            status = ZtNetworkStatus.Status.OK,
            assignedAddresses = assigned,
            routes = routes,
        )
        resolver.updateNetwork(network, status)
    }

    private fun net(
        id: String,
        allowManaged: Boolean = true,
        allowDefault: Boolean = false,
        allowGlobal: Boolean = false,
        routePriority: Int = 0,
    ) = ZerotierBNetwork(
        networkId = id.padStart(16, '0'),
        name = id,
        allowManaged = allowManaged,
        allowDefault = allowDefault,
        allowGlobal = allowGlobal,
        routePriority = routePriority,
    )
}
