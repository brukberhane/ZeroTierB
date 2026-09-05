package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon
import kotlinx.coroutines.flow.Flow

class RootsRepository(
    private val dao: MoonDao,
    private val files: RootsFileStore,
) {
    fun observeMoons(): Flow<List<Moon>> = dao.observeAll()

    suspend fun addMoon(
        worldId: String,
        seed: String?,
        label: String = "",
        moonBytes: ByteArray? = null,
    ): AddMoonResult {
        val normalizedWorldId = Moon.normalizeWorldIdOrNull(worldId)
            ?: return AddMoonResult.Invalid
        val normalizedSeed = seed?.let { Moon.normalizeSeed(it) }
        if (seed != null && normalizedSeed == null) return AddMoonResult.Invalid

        val existingIds = dao.getAll().map { it.worldId }.toSet()
        when (MoonInsertPolicy.rejectReason(existingIds, normalizedWorldId)) {
            MoonInsertPolicy.REJECT_INVALID -> return AddMoonResult.Invalid
            MoonInsertPolicy.REJECT_DUPLICATE -> return AddMoonResult.Duplicate
            MoonInsertPolicy.REJECT_CAP -> return AddMoonResult.AtCap
        }

        if (moonBytes != null) {
            files.writeMoon(normalizedWorldId, moonBytes)
        }

        val moon = Moon(
            worldId = normalizedWorldId,
            seed = normalizedSeed,
            label = label,
            createdAt = System.currentTimeMillis(),
            hasMoonFile = moonBytes != null,
        )
        dao.insert(moon)
        return AddMoonResult.Ok
    }

    suspend fun removeMoon(worldId: String) {
        val normalized = Moon.normalizeWorldIdOrNull(worldId) ?: return
        dao.delete(normalized)
        files.deleteMoon(normalized)
    }

    suspend fun saveCustomPlanet(bytes: ByteArray) {
        files.writeCustomPlanet(bytes)
    }

    suspend fun deleteCustomPlanet() {
        files.deleteCustomPlanet()
    }

    fun ensureDummyPlanet(generate: () -> ByteArray): ByteArray {
        val file = files.dummyPlanetFile()
        if (file.exists()) {
            val existing = file.readBytes()
            if (DummyPlanet.isValid(existing)) {
                return existing
            }
        }
        val bytes = generate()
        check(DummyPlanet.isValid(bytes)) { "generated dummy planet invalid" }
        files.writeDummyPlanet(bytes)
        return bytes
    }

    sealed class AddMoonResult {
        data object Ok : AddMoonResult()

        data object Invalid : AddMoonResult()

        data object Duplicate : AddMoonResult()

        data object AtCap : AddMoonResult()
    }
}
