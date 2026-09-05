package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon
import java.io.File

class RootsFileStore(private val dir: File) {
    fun moonFile(worldId: String): File {
        val normalized = Moon.normalizeWorldId(worldId)
        return File(dir, "$normalized.moon")
    }

    fun writeMoon(worldId: String, bytes: ByteArray) {
        val file = moonFile(worldId)
        dir.mkdirs()
        file.writeBytes(bytes)
    }

    fun deleteMoon(worldId: String) {
        val normalized = Moon.normalizeWorldIdOrNull(worldId) ?: return
        val file = File(dir, "$normalized.moon")
        if (file.exists()) file.delete()
    }

    fun writeCustomPlanet(bytes: ByteArray) {
        dir.mkdirs()
        File(dir, CUSTOM_PLANET_NAME).writeBytes(bytes)
    }

    fun deleteCustomPlanet() {
        val file = File(dir, CUSTOM_PLANET_NAME)
        if (file.exists()) file.delete()
    }

    fun customPlanetFile(): File = File(dir, CUSTOM_PLANET_NAME)

    fun dummyPlanetFile(): File = File(dir, DUMMY_PLANET_NAME)

    companion object {
        const val CUSTOM_PLANET_NAME = "planet"
        const val DUMMY_PLANET_NAME = "dummy.planet"
    }
}
