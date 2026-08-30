package com.brukb.zerotier.proxy.dns

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDnsResolverTest {
    @Test
    fun blankDomain_onlyLocalNames() {
        val resolver = NetworkDnsResolver(1L, "", listOf("10.1.0.1"))
        assertTrue(resolver.shouldResolve("nas"))
        assertTrue(resolver.shouldResolve("printer.local"))
        assertTrue(resolver.shouldResolve("box.home.arpa"))
        assertFalse(resolver.shouldResolve("example.com"))
        assertFalse(resolver.shouldResolve("calibre.example.org"))
    }

    @Test
    fun namedDomain_suffixMatch() {
        val resolver = NetworkDnsResolver(1L, "zt.example", listOf("10.1.0.1"))
        assertTrue(resolver.shouldResolve("host.zt.example"))
        assertTrue(resolver.shouldResolve("zt.example"))
        assertFalse(resolver.shouldResolve("example.com"))
    }
}
