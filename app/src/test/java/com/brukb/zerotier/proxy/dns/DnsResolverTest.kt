package com.brukb.zerotier.proxy.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class DnsResolverTest {
    private val example = listOf(InetAddress.getByName("1.2.3.4"))
    private val linkIp = listOf(InetAddress.getByName("9.9.9.9"))
    private lateinit var uplink: FakeUplink
    private var now = 0L
    private lateinit var resolver: DnsResolver

    @Before
    fun setup() {
        now = 0L
        uplink = FakeUplink()
        resolver = DnsResolver(uplink, elapsedRealtime = { now })
    }

    @Test
    fun failOpen_usesLinkWhenPrivateEmpty() {
        resolver.failOpen = true
        uplink.privateDns = true
        uplink.privateResult = emptyList()
        uplink.linkResult = linkIp
        assertEquals(linkIp, resolver.resolve("www.example.com"))
        assertEquals(1, uplink.privateCalls)
        assertEquals(1, uplink.linkCalls)
    }

    @Test
    fun failClosed_doesNotUseLinkWhenPrivateEmpty() {
        resolver.failOpen = false
        uplink.privateDns = true
        uplink.privateResult = emptyList()
        uplink.linkResult = linkIp
        assertTrue(resolver.resolve("www.example.com").isEmpty())
        assertEquals(1, uplink.privateCalls)
        assertEquals(0, uplink.linkCalls)
    }

    @Test
    fun privateSuccess_skipsLink() {
        resolver.failOpen = true
        uplink.privateDns = true
        uplink.privateResult = example
        uplink.linkResult = linkIp
        assertEquals(example, resolver.resolve("www.example.com"))
        assertEquals(0, uplink.linkCalls)
    }

    @Test
    fun noPrivateDns_failClosed_usesLinkAsPrimary() {
        resolver.failOpen = false
        uplink.privateDns = false
        uplink.linkResult = linkIp
        assertEquals(linkIp, resolver.resolve("www.example.com"))
        assertEquals(0, uplink.privateCalls)
        assertEquals(1, uplink.linkCalls)
    }

    @Test
    fun failOpen_circuitBreaker_skipsPrivateAfterTwoFails() {
        resolver.failOpen = true
        uplink.privateDns = true
        uplink.privateResult = emptyList()
        uplink.linkResult = linkIp
        resolver.resolve("a.example.com")
        resolver.resolve("b.example.com")
        assertEquals(2, uplink.privateCalls)
        uplink.privateCalls = 0
        uplink.linkCalls = 0
        assertEquals(linkIp, resolver.resolve("c.example.com"))
        assertEquals(0, uplink.privateCalls)
        assertEquals(1, uplink.linkCalls)
    }

    @Test
    fun negativeCache_skipsRepeatLookup() {
        resolver.failOpen = false
        uplink.privateDns = true
        uplink.privateResult = emptyList()
        resolver.resolve("gone.example.com")
        assertEquals(1, uplink.privateCalls)
        uplink.privateCalls = 0
        assertTrue(resolver.resolve("gone.example.com").isEmpty())
        assertEquals(0, uplink.privateCalls)
        now = DnsResolver.NEGATIVE_CACHE_MS
        resolver.resolve("gone.example.com")
        assertEquals(1, uplink.privateCalls)
    }

    private class FakeUplink : UplinkDnsClient {
        var privateDns = false
        var privateResult: List<InetAddress> = emptyList()
        var linkResult: List<InetAddress> = emptyList()
        var privateCalls = 0
        var linkCalls = 0

        override fun hasPrivateDns(): Boolean = privateDns

        override fun lookupPrivate(host: String, timeoutMs: Int): List<InetAddress> {
            privateCalls++
            return privateResult
        }

        override fun lookupLink(host: String, timeoutMs: Int): List<InetAddress> {
            linkCalls++
            return linkResult
        }
    }
}
