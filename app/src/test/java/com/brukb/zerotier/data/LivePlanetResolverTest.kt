package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.PlanetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlanetResolverTest {
    @Test
    fun resolve_table() {
        assertDecision(
            airgap = false,
            latch = false,
            moons = 0,
            planetSource = PlanetSource.EARTH,
            customFile = false,
            source = LivePlanetSource.EARTH,
            forcedOff = false,
        )
        assertDecision(
            airgap = false,
            latch = true,
            moons = 2,
            planetSource = PlanetSource.CUSTOM,
            customFile = true,
            source = LivePlanetSource.CUSTOM,
            forcedOff = false,
        )
        assertDecision(
            airgap = false,
            latch = false,
            moons = 0,
            planetSource = PlanetSource.CUSTOM,
            customFile = false,
            source = LivePlanetSource.EARTH,
            forcedOff = false,
        )
        assertDecision(
            airgap = true,
            latch = false,
            moons = 0,
            planetSource = PlanetSource.EARTH,
            customFile = false,
            source = LivePlanetSource.EARTH,
            forcedOff = true,
        )
        assertDecision(
            airgap = true,
            latch = true,
            moons = 0,
            planetSource = PlanetSource.EARTH,
            customFile = false,
            source = LivePlanetSource.DUMMY,
            forcedOff = false,
        )
        assertDecision(
            airgap = true,
            latch = false,
            moons = 1,
            planetSource = PlanetSource.EARTH,
            customFile = false,
            source = LivePlanetSource.DUMMY,
            forcedOff = false,
        )
        assertDecision(
            airgap = true,
            latch = true,
            moons = 1,
            planetSource = PlanetSource.CUSTOM,
            customFile = true,
            source = LivePlanetSource.DUMMY,
            forcedOff = false,
        )
    }

    private fun assertDecision(
        airgap: Boolean,
        latch: Boolean,
        moons: Int,
        planetSource: PlanetSource,
        customFile: Boolean,
        source: LivePlanetSource,
        forcedOff: Boolean,
    ) {
        val decision = LivePlanetResolver.resolve(
            airgap = airgap,
            airgapWithoutMoons = latch,
            planetSource = planetSource,
            moonCount = moons,
            customPlanetPresent = customFile,
        )
        assertEquals(source, decision.source)
        assertEquals(forcedOff, decision.airgapForcedOff)
        if (source == LivePlanetSource.DUMMY) {
            assertTrue(!forcedOff)
        }
    }
}
