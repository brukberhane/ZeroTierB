package com.brukb.zerotier.data.model

object MainNetworkSelector {
    fun select(enabled: List<ZerotierBNetwork>): ZerotierBNetwork? {
        if (enabled.isEmpty()) return null
        enabled.firstOrNull { it.isPinnedMain }?.let { return it }
        return enabled.minWith(
            compareBy<ZerotierBNetwork> { it.createdAt == 0L }
                .thenBy { it.createdAt }
                .thenBy { it.networkId },
        )
    }
}

object PinHelpers {
    fun applyPin(rows: List<ZerotierBNetwork>, networkId: String): List<ZerotierBNetwork> =
        rows.map { it.copy(isPinnedMain = it.networkId == networkId) }
}
