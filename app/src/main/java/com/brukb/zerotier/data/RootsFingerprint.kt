package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon

data class RootsFingerprint(
    val source: LivePlanetSource,
    val moonIds: List<String>,
    val customStamp: Long,
)

object RootsRestart {
    fun requiresRestart(
        stackRunning: Boolean,
        last: RootsFingerprint?,
        next: RootsFingerprint,
    ): Boolean = stackRunning && last != next
}

fun buildRootsFingerprint(
    source: LivePlanetSource,
    moons: List<Moon>,
    customStamp: Long,
): RootsFingerprint = RootsFingerprint(
    source = source,
    moonIds = moons.map { it.worldId }.sorted(),
    customStamp = if (source == LivePlanetSource.CUSTOM) customStamp else 0L,
)
