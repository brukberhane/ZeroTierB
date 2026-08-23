package com.brukb.zerotier.vpn.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkPacketQueueTest {
    @Test
    fun controlLanePolledBeforeBulk() {
        val queue = NetworkPacketQueue(maxPerLane = 8)
        queue.enqueue(frame(1L, PacketPriority.BULK))
        queue.enqueue(frame(2L, PacketPriority.CONTROL))
        assertEquals(2L, queue.poll(PacketPriority.CONTROL)?.networkId)
        assertEquals(1L, queue.poll(PacketPriority.BULK)?.networkId)
    }

    @Test
    fun headDropWhenLaneFull() {
        val queue = NetworkPacketQueue(maxPerLane = 2)
        queue.enqueue(frame(1L, PacketPriority.BULK))
        queue.enqueue(frame(2L, PacketPriority.BULK))
        queue.enqueue(frame(3L, PacketPriority.BULK))
        assertEquals(1, queue.dropCount(PacketPriority.BULK))
        assertEquals(2L, queue.poll(PacketPriority.BULK)?.networkId)
        assertEquals(3L, queue.poll(PacketPriority.BULK)?.networkId)
        assertNull(queue.poll(PacketPriority.BULK))
    }

    @Test
    fun wireQueueHeadDrop() {
        val wire = WireInboundQueue(maxPerLane = 1)
        wire.enqueue(wirePacket(1))
        wire.enqueue(wirePacket(2))
        assertNotNull(wire.poll(PacketPriority.BULK))
        assertNull(wire.poll(PacketPriority.BULK))
    }

    private fun frame(networkId: Long, priority: PacketPriority): ScheduledPacket.OutboundFrame =
        ScheduledPacket.OutboundFrame(
            networkId = networkId,
            sourceMac = 1L,
            destMac = 2L,
            etherType = PacketClassifier.IPV4_ETHER_TYPE,
            vlanId = 0,
            frameData = byteArrayOf(0x45),
            priority = priority,
        )

    private fun wirePacket(id: Int): ScheduledPacket.InboundWire =
        ScheduledPacket.InboundWire(
            localSocket = -1,
            remote = java.net.InetSocketAddress("1.2.3.4", 9994),
            packet = byteArrayOf(id.toByte()),
            priority = PacketPriority.BULK,
        )
}
