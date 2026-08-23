package com.brukb.zerotier.vpn.scheduling

import com.brukb.zerotier.vpn.IPPacketUtils

/**
 * Classifies packets into priority lanes using built-in heuristics and optional [PriorityRule]s.
 */
class PacketClassifier(
    private val extraRules: List<PriorityRule> = emptyList(),
) {
    private val builtInRules: List<PriorityRule> = listOf(
        PriorityRule { packet, etherType -> classifyArp(etherType) },
        PriorityRule { packet, etherType -> classifyIpPacket(packet, etherType) },
    )

    fun classifyOutboundIp(packet: ByteArray, etherType: Int): PacketPriority =
        classify(packet, etherType)

    fun classifyInboundIp(packet: ByteArray, etherType: Int): PacketPriority =
        classify(packet, etherType)

    /** Wire envelopes are not IP; use size heuristic. */
    fun classifyInboundWire(packet: ByteArray): PacketPriority =
        if (packet.size <= WIRE_CONTROL_MAX_BYTES) PacketPriority.CONTROL else PacketPriority.BULK

    fun classifyArpFrame(): PacketPriority = PacketPriority.CONTROL

    private fun classify(packet: ByteArray, etherType: Int): PacketPriority {
        for (rule in extraRules + builtInRules) {
            val result = rule.classify(packet, etherType)
            if (result != null) return result
        }
        return PacketPriority.BULK
    }

    private fun classifyArp(etherType: Int): PacketPriority? =
        if (etherType == ARP_ETHER_TYPE) PacketPriority.CONTROL else null

    private fun classifyIpPacket(packet: ByteArray, etherType: Int): PacketPriority? {
        if (etherType != IPV4_ETHER_TYPE && etherType != IPV6_ETHER_TYPE) return null
        val version = IPPacketUtils.getIPVersion(packet).toInt()
        return when (version) {
            4 -> classifyIpv4(packet)
            6 -> classifyIpv6(packet)
            else -> PacketPriority.BULK
        }
    }

    private fun classifyIpv4(packet: ByteArray): PacketPriority {
        if (packet.size < 20) return PacketPriority.BULK
        val protocol = packet[9].toInt() and 0xFF
        return when (protocol) {
            PROTO_ICMP -> PacketPriority.CONTROL
            PROTO_UDP -> {
                if (packet.size >= 24) {
                    val dstPort = ((packet[22].toInt() and 0xFF) shl 8) or (packet[23].toInt() and 0xFF)
                    val srcPort = ((packet[20].toInt() and 0xFF) shl 8) or (packet[21].toInt() and 0xFF)
                    if (dstPort == DNS_PORT || srcPort == DNS_PORT) PacketPriority.CONTROL else PacketPriority.BULK
                } else {
                    PacketPriority.BULK
                }
            }
            else -> PacketPriority.BULK
        }
    }

    private fun classifyIpv6(packet: ByteArray): PacketPriority {
        if (packet.size < 40) return PacketPriority.BULK
        val nextHeader = packet[6].toInt() and 0xFF
        return when (nextHeader) {
            PROTO_ICMPV6 -> PacketPriority.CONTROL
            PROTO_UDP -> {
                if (packet.size >= 44) {
                    val dstPort = ((packet[42].toInt() and 0xFF) shl 8) or (packet[43].toInt() and 0xFF)
                    val srcPort = ((packet[40].toInt() and 0xFF) shl 8) or (packet[41].toInt() and 0xFF)
                    if (dstPort == DNS_PORT || srcPort == DNS_PORT) PacketPriority.CONTROL else PacketPriority.BULK
                } else {
                    PacketPriority.BULK
                }
            }
            else -> PacketPriority.BULK
        }
    }

    companion object {
        const val ARP_ETHER_TYPE = 2054
        const val IPV4_ETHER_TYPE = 2048
        const val IPV6_ETHER_TYPE = 34525
        private const val PROTO_ICMP = 1
        private const val PROTO_UDP = 17
        private const val PROTO_ICMPV6 = 58
        private const val DNS_PORT = 53
        private const val WIRE_CONTROL_MAX_BYTES = 200
    }
}
