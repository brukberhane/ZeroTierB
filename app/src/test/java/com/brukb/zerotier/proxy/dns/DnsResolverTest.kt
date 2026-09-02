package com.brukb.zerotier.proxy.dns

import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DnsResolverTest {
    private val example = listOf(InetAddress.getByName("1.2.3.4"))
    private val sinkhole = listOf(InetAddress.getByName("0.0.0.0"))
    private val fallbackIp = listOf(InetAddress.getByName("9.9.9.9"))
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
    fun ztDomainMatch_ok_skipsNetd() {
        addZtBackend(domain = "sqrl") { host ->
            if (host == "host.sqrl") DnsLookupResult.Ok(example) else DnsLookupResult.Failure("miss")
        }
        assertEquals(example, resolver.resolve("host.sqrl"))
        assertEquals(0, uplink.netdCalls)
        assertEquals(0, uplink.udpCalls)
    }

    @Test
    fun ztDomainMatch_nxDomain_authoritative_noNetd() {
        addZtBackend(domain = "sqrl") { DnsLookupResult.NxDomain }
        assertTrue(resolver.resolve("gone.sqrl").isEmpty())
        assertEquals(0, uplink.netdCalls)
    }

    @Test
    fun ztDomainMatch_failure_fallsThroughToNetd() {
        addZtBackend(domain = "sqrl") { DnsLookupResult.Failure("timeout") }
        uplink.netdResult = DnsLookupResult.Ok(example)
        assertEquals(example, resolver.resolve("host.sqrl"))
        assertEquals(1, uplink.netdCalls)
    }

    @Test
    fun netdOk_skipsFallback() {
        uplink.netdResult = DnsLookupResult.Ok(example)
        assertEquals(example, resolver.resolve("www.example.com"))
        assertEquals(0, uplink.udpCalls)
    }

    @Test
    fun netdFailure_failOpen_usesFallback() {
        resolver.failOpen = true
        resolver.fallbackServers = listOf(InetAddress.getByName("8.8.8.8"))
        uplink.netdResult = DnsLookupResult.Failure("timeout")
        uplink.udpResults["8.8.8.8:www.example.com"] = DnsLookupResult.Ok(fallbackIp)
        assertEquals(fallbackIp, resolver.resolve("www.example.com"))
        assertEquals(1, uplink.udpCalls)
    }

    @Test
    fun netdFailure_failClosed_skipsFallback() {
        resolver.failOpen = false
        resolver.fallbackServers = listOf(InetAddress.getByName("8.8.8.8"))
        uplink.netdResult = DnsLookupResult.Failure("timeout")
        assertTrue(resolver.resolve("www.example.com").isEmpty())
        assertEquals(0, uplink.udpCalls)
    }

    @Test
    fun netdNxDomain_ztOk_evenWhenFailClosed() {
        resolver.failOpen = false
        addZtBackend(domain = "zt.example") { DnsLookupResult.Ok(example) }
        uplink.netdResult = DnsLookupResult.NxDomain
        assertEquals(example, resolver.resolve("www.example.com"))
    }

    @Test
    fun netdNxDomain_ztOk_secondResolveNotCacheNeg() {
        addZtBackend(domain = "zt.example") { DnsLookupResult.Ok(example) }
        uplink.netdResult = DnsLookupResult.NxDomain
        assertEquals(example, resolver.resolve("www.example.com"))
        now += 1
        uplink.netdResult = DnsLookupResult.Ok(example)
        assertEquals(example, resolver.resolve("www.example.com"))
        assertTrue(uplink.netdCalls >= 2)
    }

    @Test
    fun netdFailure_failOpen_fallbackNx_thenZtOk() {
        resolver.failOpen = true
        resolver.fallbackServers = listOf(InetAddress.getByName("8.8.8.8"))
        addZtBackend(domain = "zt.example") { DnsLookupResult.Ok(example) }
        uplink.netdResult = DnsLookupResult.Failure("timeout")
        uplink.udpResults["8.8.8.8:www.example.com"] = DnsLookupResult.NxDomain
        assertEquals(example, resolver.resolve("www.example.com"))
    }

    @Test
    fun netdNoData_noFallbackNoZt() {
        var ztCalls = 0
        addZtBackend(domain = "zt.example") {
            ztCalls++
            DnsLookupResult.Ok(example)
        }
        uplink.netdResult = DnsLookupResult.NoData
        assertTrue(resolver.resolve("blocked.example.com").isEmpty())
        assertEquals(0, uplink.udpCalls)
        assertEquals(0, ztCalls)
    }

    @Test
    fun netdFailure_doesNotHitZt() {
        addZtBackend(domain = "zt.example") { DnsLookupResult.Ok(example) }
        uplink.netdResult = DnsLookupResult.Failure("timeout")
        assertTrue(resolver.resolve("www.example.com").isEmpty())
    }

    @Test
    fun netdNxDomain_noZt_negativeCached() {
        uplink.netdResult = DnsLookupResult.NxDomain
        assertTrue(resolver.resolve("gone.example.com").isEmpty())
        uplink.netdCalls = 0
        assertTrue(resolver.resolve("gone.example.com").isEmpty())
        assertEquals(0, uplink.netdCalls)
        now = DnsResolver.NEGATIVE_CACHE_MS
        uplink.netdResult = DnsLookupResult.Ok(example)
        assertEquals(example, resolver.resolve("gone.example.com"))
    }

    @Test
    fun timeoutFailure_notNegativeCached() {
        uplink.netdResult = DnsLookupResult.Failure("timeout")
        assertTrue(resolver.resolve("slow.example.com").isEmpty())
        uplink.netdCalls = 0
        uplink.netdResult = DnsLookupResult.Ok(example)
        assertEquals(example, resolver.resolve("slow.example.com"))
        assertEquals(1, uplink.netdCalls)
    }

    @Test
    fun netdOk_sinkhole_noFallback() {
        resolver.failOpen = true
        resolver.fallbackServers = listOf(InetAddress.getByName("8.8.8.8"))
        uplink.netdResult = DnsLookupResult.Ok(sinkhole)
        assertEquals(sinkhole, resolver.resolve("blocked.example.com"))
        assertEquals(0, uplink.udpCalls)
    }

    @Test
    fun pickResolver_lowerRoutePriorityWins() {
        val queried = AtomicLong(-1L)
        fun addFake(id: Long, priority: Int) {
            resolver.replaceBackendForTest(
                id,
                object : ZtDnsBackend {
                    override val networkId = id
                    override val routePriority = priority
                    override val domainLabel = "sqrl"
                    override fun shouldResolve(host: String): Boolean = host.endsWith(".sqrl")
                    override fun resolve(host: String): DnsLookupResult {
                        queried.set(networkId)
                        return DnsLookupResult.Ok(example)
                    }
                },
            )
        }
        addFake(0x100L, 5)
        addFake(0x200L, 0)
        assertEquals(example, resolver.resolve("a.sqrl"))
        assertEquals(0x200L, queried.get())
    }

    @Test
    fun inflightDedup_singleNetdCall() {
        val latch = CountDownLatch(1)
        uplink.netdLatch = latch
        uplink.netdResult = DnsLookupResult.Ok(example)
        var a: List<InetAddress>? = null
        var b: List<InetAddress>? = null
        val t1 = Thread { a = resolver.resolve("dedup.example.com") }
        val t2 = Thread { b = resolver.resolve("dedup.example.com") }
        t1.start()
        t2.start()
        Thread.sleep(50)
        latch.countDown()
        t1.join(2000)
        t2.join(2000)
        assertEquals(example, a)
        assertEquals(example, b)
        assertEquals(1, uplink.netdCalls)
    }

    private fun addZtBackend(domain: String, script: (String) -> DnsLookupResult) {
        val networkId = 0x1234567890abcdefL
        val config = ZerotierBNetwork(
            networkId = java.lang.Long.toUnsignedString(networkId, 16),
            allowDns = true,
        )
        val status = ZtNetworkStatus(
            networkId = networkId,
            status = ZtNetworkStatus.Status.OK,
            dnsServers = listOf("10.1.0.1"),
            dnsDomain = domain,
        )
        resolver.updateNetwork(config, status)
        val inner = NetworkDnsResolver(networkId, domain, listOf("10.1.0.1"))
        resolver.replaceBackendForTest(
            networkId,
            object : ZtDnsBackend {
                override val networkId = networkId
                override val domainLabel = domain
                override fun shouldResolve(host: String): Boolean = inner.shouldResolve(host)
                override fun resolve(host: String): DnsLookupResult = script(host)
            },
        )
    }

    private class FakeUplink : UplinkDnsClient {
        var netdResult: DnsLookupResult = DnsLookupResult.Failure("unset")
        var netdCalls = 0
        var udpCalls = 0
        var netdLatch: CountDownLatch? = null
        val udpResults = mutableMapOf<String, DnsLookupResult>()

        override fun lookupNetd(host: String, timeoutMs: Int): DnsLookupResult {
            netdCalls++
            netdLatch?.await(2, TimeUnit.SECONDS)
            return netdResult
        }

        override fun lookupUdp(server: InetAddress, host: String, timeoutMs: Int): DnsLookupResult {
            udpCalls++
            return udpResults["${server.hostAddress}:$host"] ?: DnsLookupResult.Failure("unset")
        }
    }
}
