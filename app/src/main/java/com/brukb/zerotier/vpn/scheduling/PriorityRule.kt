package com.brukb.zerotier.vpn.scheduling

/**
 * Pluggable rule for classifying IP packet priority. Rules run in order; first match wins.
 * Return null to defer to the next rule.
 */
fun interface PriorityRule {
    fun classify(packet: ByteArray, etherType: Int): PacketPriority?
}
