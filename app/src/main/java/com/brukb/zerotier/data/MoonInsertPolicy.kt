package com.brukb.zerotier.data

import com.brukb.zerotier.data.model.Moon

object MoonInsertPolicy {
    const val REJECT_INVALID = "invalid"
    const val REJECT_DUPLICATE = "duplicate"
    const val REJECT_CAP = "cap"

    fun rejectReason(existingWorldIds: Set<String>, worldId: String): String? {
        val normalized = Moon.normalizeWorldIdOrNull(worldId)
            ?: return REJECT_INVALID
        if (normalized in existingWorldIds) return REJECT_DUPLICATE
        if (existingWorldIds.size >= Moon.MAX_MOONS) return REJECT_CAP
        return null
    }
}
