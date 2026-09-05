package com.brukb.zerotier.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanetSourceTest {
    @Test
    fun parse_nullOrBlank_defaultsEarth() {
        assertEquals(PlanetSource.EARTH, PlanetSource.parse(null))
        assertEquals(PlanetSource.EARTH, PlanetSource.parse(""))
        assertEquals(PlanetSource.EARTH, PlanetSource.parse("   "))
    }

    @Test
    fun parse_earthCaseInsensitive() {
        assertEquals(PlanetSource.EARTH, PlanetSource.parse("earth"))
        assertEquals(PlanetSource.EARTH, PlanetSource.parse("EARTH"))
    }

    @Test
    fun parse_custom() {
        assertEquals(PlanetSource.CUSTOM, PlanetSource.parse("custom"))
        assertEquals(PlanetSource.CUSTOM, PlanetSource.parse("CUSTOM"))
    }

    @Test
    fun parse_unknown_defaultsEarth() {
        assertEquals(PlanetSource.EARTH, PlanetSource.parse("bogus"))
    }
}
