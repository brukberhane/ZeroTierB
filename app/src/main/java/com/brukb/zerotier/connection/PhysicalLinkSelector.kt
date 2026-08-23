package com.brukb.zerotier.connection

data class LinkCandidate(
    val isVpn: Boolean,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val underlyingWifi: Boolean = false,
    val underlyingCellular: Boolean = false,
)

object PhysicalLinkSelector {
    fun pick(candidates: List<LinkCandidate>): LinkCandidate? {
        if (candidates.isEmpty()) return null
        val vpn = candidates.filter { it.isVpn }
        val remaining = candidates.filter { !it.isVpn }
        if (remaining.isEmpty()) return null

        val wantWifi = vpn.any { it.underlyingWifi }
        val wantCell = vpn.any { it.underlyingCellular }
        if (wantWifi) {
            remaining.firstOrNull { it.isWifi }?.let { return it }
        }
        if (wantCell) {
            remaining.firstOrNull { it.isCellular }?.let { return it }
        }
        remaining.firstOrNull { it.isWifi }?.let { return it }
        remaining.firstOrNull { it.isCellular }?.let { return it }
        return remaining.first()
    }
}
