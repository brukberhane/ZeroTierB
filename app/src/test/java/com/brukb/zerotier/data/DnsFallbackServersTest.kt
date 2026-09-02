package com.brukb.zerotier.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DnsFallbackServersTest {
    @Test
    fun sanitize_dropsHostnames() {
        assertEquals(
            listOf("1.1.1.1"),
            AppPreferences.sanitizeDnsFallbackServers(listOf("dns.google", "1.1.1.1")),
        )
    }

    @Test
    fun sanitize_capsAtFive() {
        val servers = (1..8).map { "1.0.0.$it" }
        assertEquals(5, AppPreferences.sanitizeDnsFallbackServers(servers).size)
    }

    @Test
    fun sanitize_distinct() {
        assertEquals(
            listOf("8.8.8.8"),
            AppPreferences.sanitizeDnsFallbackServers(listOf("8.8.8.8", "8.8.8.8")),
        )
    }

    @Test
    fun parse_splitsNewlinesAndCommas() {
        assertEquals(
            listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"),
            AppPreferences.parseDnsFallbackServers("1.1.1.1\n8.8.8.8, 9.9.9.9"),
        )
    }
}
