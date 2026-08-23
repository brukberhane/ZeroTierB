package com.brukb.zerotier.vpn.scheduling

/**
 * Traffic priority lane. Lower ordinal = higher precedence within a network queue.
 * Add new values before [BULK] to extend without changing scheduler structure.
 */
enum class PacketPriority {
    CONTROL,
    BULK,
    ;

    companion object {
        val PROCESSING_ORDER: List<PacketPriority> = entries
    }
}
