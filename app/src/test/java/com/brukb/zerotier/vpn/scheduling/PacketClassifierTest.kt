package com.brukb.zerotier.vpn.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketClassifierTest {
    private val classifier = PacketClassifier()

    @Test
    fun icmpIsControl() {
        val packet = ipv4(protocol = 1)
        assertEquals(PacketPriority.CONTROL, classifier.classifyOutboundIp(packet, PacketClassifier.IPV4_ETHER_TYPE))
    }

    @Test
    fun tcpIsBulk() {
        val packet = ipv4(protocol = 6)
        assertEquals(PacketPriority.BULK, classifier.classifyOutboundIp(packet, PacketClassifier.IPV4_ETHER_TYPE))
    }

    @Test
    fun dnsUdpIsControl() {
        val packet = ipv4(protocol = 17, srcPort = 5353, dstPort = 53)
        assertEquals(PacketPriority.CONTROL, classifier.classifyOutboundIp(packet, PacketClassifier.IPV4_ETHER_TYPE))
    }

    @Test
    fun arpIsControl() {
        assertEquals(PacketPriority.CONTROL, classifier.classifyArpFrame())
    }

    @Test
    fun smallWireIsControl() {
        assertEquals(PacketPriority.CONTROL, classifier.classifyInboundWire(ByteArray(100)))
    }

    @Test
    fun largeWireIsBulk() {
        assertEquals(PacketPriority.BULK, classifier.classifyInboundWire(ByteArray(1500)))
    }

    @Test
    fun customRuleCanPromoteTraffic() {
        val sshRule = PriorityRule { _, etherType ->
            if (etherType == PacketClassifier.IPV4_ETHER_TYPE) PacketPriority.CONTROL else null
        }
        val custom = PacketClassifier(extraRules = listOf(sshRule))
        val tcp = ipv4(protocol = 6)
        assertEquals(PacketPriority.CONTROL, custom.classifyOutboundIp(tcp, PacketClassifier.IPV4_ETHER_TYPE))
    }

    private fun ipv4(protocol: Int, srcPort: Int = 0, dstPort: Int = 0): ByteArray {
        val packet = ByteArray(28)
        packet[0] = 0x45
        packet[9] = protocol.toByte()
        if (protocol == 17) {
            packet[20] = (srcPort shr 8).toByte()
            packet[21] = srcPort.toByte()
            packet[22] = (dstPort shr 8).toByte()
            packet[23] = dstPort.toByte()
        }
        return packet
    }
}
