package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.PlanetSource

enum class LivePlanetSource {
    EARTH,
    CUSTOM,
    DUMMY,
}

data class LivePlanetDecision(
    val source: LivePlanetSource,
    val airgapForcedOff: Boolean,
)

object LivePlanetResolver {
    fun resolve(
        airgap: Boolean,
        airgapWithoutMoons: Boolean,
        planetSource: PlanetSource,
        moonCount: Int,
        customPlanetPresent: Boolean,
    ): LivePlanetDecision {
        var effectiveAirgap = airgap
        var airgapForcedOff = false
        if (airgap && moonCount == 0 && !airgapWithoutMoons) {
            airgapForcedOff = true
            effectiveAirgap = false
        }
        val source = when {
            effectiveAirgap -> LivePlanetSource.DUMMY
            planetSource == PlanetSource.CUSTOM && customPlanetPresent -> LivePlanetSource.CUSTOM
            else -> LivePlanetSource.EARTH
        }
        return LivePlanetDecision(source = source, airgapForcedOff = airgapForcedOff)
    }
}
