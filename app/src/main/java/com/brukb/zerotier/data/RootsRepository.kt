package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class RootsRepository(
    private val dao: MoonDao,
    private val files: RootsFileStore,
) {
    private val customPlanetEpoch = MutableStateFlow(0)

    fun observeMoons(): Flow<List<Moon>> = dao.observeAll()

    fun observeCustomPlanetEpoch(): Flow<Int> = customPlanetEpoch.asStateFlow()

    suspend fun getMoons(): List<Moon> = dao.getAll()

    fun customPlanetPresent(): Boolean = files.customPlanetFile().exists()

    fun customPlanetLastModified(): Long =
        files.customPlanetFile().takeIf { it.exists() }?.lastModified() ?: 0L

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
        bumpCustomPlanetEpoch()
    }

    suspend fun deleteCustomPlanet() {
        files.deleteCustomPlanet()
        bumpCustomPlanetEpoch()
    }

    private fun bumpCustomPlanetEpoch() {
        customPlanetEpoch.value += 1
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
