package com.brukb.zerotier.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldBlobParserTest {
    @Test
    fun binaryPlanet_earthId() {
        val bytes = byteArrayOf(
            0x01,
            0x00, 0x00, 0x00, 0x00, 0x08, 0xea.toByte(), 0xc9.toByte(), 0x0a,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        val result = WorldBlobParser.parse(bytes)
        assertTrue(result is WorldBlobParseResult.Planet)
        assertEquals("0000000008eac90a", (result as WorldBlobParseResult.Planet).worldId)
    }

    @Test
    fun binaryMoon() {
        val bytes = byteArrayOf(
            0x7f,
            0x00, 0x00, 0x00, 0xde.toByte(), 0xad.toByte(), 0xbe.toByte(), 0xef.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        val result = WorldBlobParser.parse(bytes)
        assertTrue(result is WorldBlobParseResult.Moon)
        val moon = result as WorldBlobParseResult.Moon
        assertEquals("000000deadbeef00", moon.worldId)
        assertEquals(null, moon.seed)
    }

    @Test
    fun binaryTooShortOrUnknownType() {
        assertTrue(WorldBlobParser.parse(ByteArray(16)) is WorldBlobParseResult.Error)
        val bytes = byteArrayOf(
            0x00,
            0x00, 0x00, 0x00, 0x00, 0x08, 0xea.toByte(), 0xc9.toByte(), 0x0a,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        assertTrue(WorldBlobParser.parse(bytes) is WorldBlobParseResult.Error)
    }

    @Test
    fun jsonMoon_extractsIdAndSeedWithoutSigningKeys() {
        val json = """
            {"objtype":"world","worldType":"moon","id":"deadbeef00",
            "signingKey":"aa","signingKey_SECRET":"bb",
            "roots":[{"identity":"deadbeef00:0:abcd","stableEndpoints":[]}]}
        """.trimIndent()
        val result = WorldBlobParser.parse(json.toByteArray())
        assertTrue(result is WorldBlobParseResult.Moon)
        val moon = result as WorldBlobParseResult.Moon
        assertEquals("000000deadbeef00", moon.worldId)
        assertEquals("deadbeef00", moon.seed)
        val serialized = moon.toString()
        assertFalse(serialized.contains("signingKey"))
        assertFalse(serialized.contains("bb"))
    }

    @Test
    fun jsonPlanetRejected() {
        val json = """{"worldType":"planet","id":"deadbeef00"}"""
        assertTrue(WorldBlobParser.parse(json.toByteArray()) is WorldBlobParseResult.Error)
    }

    @Test
    fun jsonMissingIdRejected() {
        val json = """{"worldType":"moon"}"""
        assertTrue(WorldBlobParser.parse(json.toByteArray()) is WorldBlobParseResult.Error)
    }

    @Test
    fun jsonGarbageRejected() {
        assertTrue(WorldBlobParser.parse("{not json".toByteArray()) is WorldBlobParseResult.Error)
    }

    @Test
    fun emptyRejected() {
        assertTrue(WorldBlobParser.parse(byteArrayOf()) is WorldBlobParseResult.Error)
    }
}
