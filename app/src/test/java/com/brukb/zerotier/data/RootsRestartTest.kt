package com.brukb.zerotier.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RootsRestartTest {
    @Test
    fun requiresRestart_table() {
        val earth = RootsFingerprint(LivePlanetSource.EARTH, emptyList(), 0L)
        val dummy = RootsFingerprint(LivePlanetSource.DUMMY, emptyList(), 0L)
        val earthMoon = RootsFingerprint(LivePlanetSource.EARTH, listOf("a"), 0L)

        assertEquals(false, RootsRestart.requiresRestart(false, earth, earth))
        assertEquals(false, RootsRestart.requiresRestart(true, earth, earth))
        assertEquals(false, RootsRestart.requiresRestart(false, earth, dummy))
        assertEquals(true, RootsRestart.requiresRestart(true, earth, dummy))
        assertEquals(true, RootsRestart.requiresRestart(true, earth, earthMoon))
        assertEquals(true, RootsRestart.requiresRestart(true, null, earth))
    }
}
