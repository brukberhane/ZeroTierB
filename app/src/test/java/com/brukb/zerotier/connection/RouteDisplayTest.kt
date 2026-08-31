package com.brukb.zerotier.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteDisplayTest {
    @Test
    fun formatAssignedCidr_formatsHostAndPrefix() {
        assertEquals("10.147.20.1/24", formatAssignedCidr("10.147.20.1", 24))
        assertNull(formatAssignedCidr(null, 24))
    }

    @Test
    fun formatRouteLine_withAndWithoutVia() {
        assertEquals("10.0.0.0/8", formatRouteLine("10.0.0.0/8", null))
        assertEquals("10.0.0.0/8 → 10.147.20.1", formatRouteLine("10.0.0.0/8", "10.147.20.1"))
        assertEquals("0.0.0.0/0", formatRouteLine("0.0.0.0/0", "0.0.0.0"))
    }

    @Test
    fun filterDisplayRoutes_managedDefaultAndGlobal() {
        assertEquals(
            emptyList<String>(),
            filterDisplayRoutes(listOf("10.0.0.0/8"), allowManaged = false, allowDefault = true, allowGlobal = true),
        )
        assertEquals(
            emptyList<String>(),
            filterDisplayRoutes(listOf("0.0.0.0/0"), allowManaged = true, allowDefault = false, allowGlobal = true),
        )
        assertEquals(
            listOf("0.0.0.0/0"),
            filterDisplayRoutes(listOf("0.0.0.0/0"), allowManaged = true, allowDefault = true, allowGlobal = false),
        )
        assertEquals(
            emptyList<String>(),
            filterDisplayRoutes(listOf("8.8.8.0/24"), allowManaged = true, allowDefault = false, allowGlobal = false),
        )
        assertEquals(
            listOf("8.8.8.0/24"),
            filterDisplayRoutes(listOf("8.8.8.0/24"), allowManaged = true, allowDefault = false, allowGlobal = true),
        )
        assertEquals(
            listOf("10.0.0.0/8 → 10.147.20.1"),
            filterDisplayRoutes(
                listOf("10.0.0.0/8 → 10.147.20.1"),
                allowManaged = true,
                allowDefault = false,
                allowGlobal = false,
            ),
        )
    }
}
