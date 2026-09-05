package com.brukb.zerotier.data

import com.brukb.zerotier.log.AppLog
import com.brukb.zerotier.data.model.Moon
import kotlinx.coroutines.flow.first

data class RootsStageResult(
    val source: LivePlanetSource,
    val planetBytes: ByteArray?,
    val extraMoonIdsToDeorbit: Set<String>,
    val moons: List<Moon>,
    val copiedMoonIds: Set<String>,
    val fingerprint: RootsFingerprint,
)

class RootsApplier internal constructor(
    private val prefs: RootsApplyPrefs,
    private val repo: RootsRepository,
    private val identity: IdentityHomeStore,
    private val worlds: RootsFileStore,
) {
    constructor(
        prefs: AppPreferences,
        repo: RootsRepository,
        identity: IdentityHomeStore,
        worlds: RootsFileStore,
    ) : this(AppPreferencesRootsApplyPrefs(prefs), repo, identity, worlds)

    suspend fun stageBeforeNode(generateDummy: () -> ByteArray): RootsStageResult {
        val moons = repo.getMoons()
        val customPresent = repo.customPlanetPresent()
        val decision = LivePlanetResolver.resolve(
            airgap = prefs.readAirgap(),
            airgapWithoutMoons = prefs.readAirgapWithoutMoons(),
            planetSource = prefs.readPlanetSource(),
            moonCount = moons.size,
            customPlanetPresent = customPresent,
        )
        if (decision.airgapForcedOff) {
            prefs.setAirgap(false)
        }
        val roomIds = moons.map { it.worldId }.toSet()
        val extraMoonIds = identity.listMoonWorldIds() - roomIds
        for (id in extraMoonIds) {
            identity.delete("moons.d/$id.moon")
        }
        val planetBytes = when (decision.source) {
            LivePlanetSource.EARTH -> {
                identity.delete("planet")
                identity.delete("roots")
                null
            }
            LivePlanetSource.CUSTOM -> {
                val bytes = worlds.customPlanetFile().readBytes()
                identity.write("planet", bytes)
                bytes
            }
            LivePlanetSource.DUMMY -> {
                val bytes = repo.ensureDummyPlanet(generateDummy)
                check(DummyPlanet.isValid(bytes)) { "dummy planet invalid" }
                identity.write("planet", bytes)
                bytes
            }
        }
        val copiedMoonIds = mutableSetOf<String>()
        for (moon in moons) {
            if (!moon.hasMoonFile) continue
            val moonFile = worlds.moonFile(moon.worldId)
            if (!moonFile.exists()) {
                AppLog.w(TAG, "moon file missing for ${moon.worldId}")
                continue
            }
            identity.write("moons.d/${moon.worldId}.moon", moonFile.readBytes())
            copiedMoonIds.add(moon.worldId)
        }
        val customStamp = if (customPresent) worlds.customPlanetFile().lastModified() else 0L
        return RootsStageResult(
            source = decision.source,
            planetBytes = planetBytes,
            extraMoonIdsToDeorbit = extraMoonIds,
            moons = moons,
            copiedMoonIds = copiedMoonIds,
            fingerprint = buildRootsFingerprint(decision.source, moons, customStamp),
        )
    }

    suspend fun applyOrbits(
        result: RootsStageResult,
        orbit: suspend (worldId: Long, seed: Long) -> Unit,
        deorbit: suspend (worldId: Long) -> Unit,
    ) {
        for (id in result.extraMoonIdsToDeorbit) {
            deorbit(java.lang.Long.parseUnsignedLong(id, 16))
        }
        for (moon in result.moons) {
            val seed = orbitSeedForMoon(moon, result.copiedMoonIds)
            if (seed == null) {
                AppLog.w(TAG, "skip orbit ${moon.worldId}: no copied file or seed")
                continue
            }
            orbit(moon.worldIdLong(), seed)
        }
    }

    companion object {
        private const val TAG = "RootsApplier"

        fun orbitSeedForMoon(moon: Moon, copiedMoonIds: Set<String>): Long? = when {
            moon.worldId in copiedMoonIds -> 0L
            moon.seed != null -> moon.seedLongOrNull()
            else -> null
        }
    }
}

internal interface RootsApplyPrefs {
    suspend fun readAirgap(): Boolean

    suspend fun readAirgapWithoutMoons(): Boolean

    suspend fun readPlanetSource(): com.brukb.zerotier.data.model.PlanetSource

    suspend fun setAirgap(value: Boolean)
}

private class AppPreferencesRootsApplyPrefs(
    private val prefs: AppPreferences,
) : RootsApplyPrefs {
    override suspend fun readAirgap(): Boolean = prefs.airgap.first()

    override suspend fun readAirgapWithoutMoons(): Boolean = prefs.airgapWithoutMoons.first()

    override suspend fun readPlanetSource(): com.brukb.zerotier.data.model.PlanetSource =
        prefs.planetSource.first()

    override suspend fun setAirgap(value: Boolean) = prefs.setAirgap(value)
}

fun Moon.worldIdLong(): Long = java.lang.Long.parseUnsignedLong(worldId, 16)

fun Moon.seedLongOrNull(): Long? =
    seed?.let { java.lang.Long.parseUnsignedLong(it, 16) }
