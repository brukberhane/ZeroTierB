package com.brukb.zerotier.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RootsStatusCopyTest {
    @Test
    fun waitingMessage_table() {
        val lan = "Waiting for roots/moons (LAN ok)"
        val earth = "Node offline — waiting for roots"
        assertEquals(
            lan,
            RootsStatusCopy.waitingMessage(
                source = LivePlanetSource.DUMMY,
                wentOffline = true,
                dummyStarting = false,
                lanOk = lan,
                earthOffline = earth,
            ),
        )
        assertEquals(
            lan,
            RootsStatusCopy.waitingMessage(
                source = LivePlanetSource.DUMMY,
                wentOffline = false,
                dummyStarting = true,
                lanOk = lan,
                earthOffline = earth,
            ),
        )
        assertEquals(
            earth,
            RootsStatusCopy.waitingMessage(
                source = LivePlanetSource.EARTH,
                wentOffline = true,
                dummyStarting = true,
                lanOk = lan,
                earthOffline = earth,
            ),
        )
        assertNull(
            RootsStatusCopy.waitingMessage(
                source = LivePlanetSource.EARTH,
                wentOffline = false,
                dummyStarting = true,
                lanOk = lan,
                earthOffline = earth,
            ),
        )
        assertNull(
            RootsStatusCopy.waitingMessage(
                source = LivePlanetSource.CUSTOM,
                wentOffline = false,
                dummyStarting = false,
                lanOk = lan,
                earthOffline = earth,
            ),
        )
    }
}
