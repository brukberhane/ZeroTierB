package com.brukb.zerotier.proxy.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DnsAThenAaaaTest {
    private val example = listOf(InetAddress.getByName("1.2.3.4"))
    private val v6 = listOf(InetAddress.getByName("::1"))

    @Test
    fun aOk_skipsAaaa() {
        val result = combineAThenAaaa(DnsLookupResult.Ok(example)) {
            error("should not run")
        }
        assertEquals(DnsLookupResult.Ok(example), result)
    }

    @Test
    fun aNx_aaaaOk() {
        val result = combineAThenAaaa(DnsLookupResult.NxDomain) {
            DnsLookupResult.Ok(v6)
        }
        assertEquals(DnsLookupResult.Ok(v6), result)
    }

    @Test
    fun aFailure_aaaaNx() {
        val result = combineAThenAaaa(DnsLookupResult.Failure("timeout")) {
            DnsLookupResult.NxDomain
        }
        assertEquals(DnsLookupResult.NxDomain, result)
    }

    @Test
    fun bothFailure_prefersFirst() {
        val result = combineAThenAaaa(DnsLookupResult.Failure("first")) {
            DnsLookupResult.Failure("second")
        }
        assertTrue(result is DnsLookupResult.Failure)
        assertEquals("first", (result as DnsLookupResult.Failure).reason)
    }
}
