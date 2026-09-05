package com.brukb.zerotier.data

object DummyPlanet {
    const val WORLD_ID_LONG = 0x5e2071e4b0000001L
    const val WORLD_ID_HEX = "5e2071e4b0000001"

    fun isValid(bytes: ByteArray): Boolean {
        val parsed = WorldBlobParser.parse(bytes)
        return parsed is WorldBlobParseResult.Planet && parsed.worldId == WORLD_ID_HEX
    }
}
