package com.brukb.zerotier.vpn.scheduling

import java.util.ArrayDeque

/**
 * Per-network outbound queue with CONTROL and BULK lanes. Head-drop when full.
 */
class NetworkPacketQueue(
    private val maxPerLane: Int = DEFAULT_MAX_PER_LANE,
) {
    private val lanes = PacketPriority.entries.associateWith { ArrayDeque<ScheduledPacket.OutboundFrame>() }
    private val dropCounts = PacketPriority.entries.associateWith { 0 }.toMutableMap()

    @Synchronized
    fun enqueue(packet: ScheduledPacket.OutboundFrame): Boolean {
        val lane = lanes.getValue(packet.priority)
        if (lane.size >= maxPerLane) {
            lane.pollFirst()
            dropCounts[packet.priority] = dropCounts.getValue(packet.priority) + 1
        }
        lane.addLast(packet)
        return true
    }

    @Synchronized
    fun poll(priority: PacketPriority): ScheduledPacket.OutboundFrame? =
        lanes.getValue(priority).pollFirst()

    @Synchronized
    fun hasWork(): Boolean = lanes.values.any { it.isNotEmpty() }

    @Synchronized
    fun hasWork(priority: PacketPriority): Boolean = lanes.getValue(priority).isNotEmpty()

    @Synchronized
    fun size(): Int = lanes.values.sumOf { it.size }

    @Synchronized
    fun dropCount(priority: PacketPriority): Int = dropCounts.getValue(priority)

    @Synchronized
    fun clear() {
        lanes.values.forEach { it.clear() }
    }

    companion object {
        const val DEFAULT_MAX_PER_LANE = 128
    }
}

/**
 * Global inbound wire queue (not attributable to a network before Node decode).
 */
class WireInboundQueue(
    private val maxPerLane: Int = DEFAULT_MAX_PER_LANE,
) {
    private val lanes = PacketPriority.entries.associateWith { ArrayDeque<ScheduledPacket.InboundWire>() }
    private val dropCounts = PacketPriority.entries.associateWith { 0 }.toMutableMap()

    @Synchronized
    fun enqueue(packet: ScheduledPacket.InboundWire) {
        val lane = lanes.getValue(packet.priority)
        if (lane.size >= maxPerLane) {
            lane.pollFirst()
            dropCounts[packet.priority] = dropCounts.getValue(packet.priority) + 1
        }
        lane.addLast(packet)
    }

    @Synchronized
    fun poll(priority: PacketPriority): ScheduledPacket.InboundWire? =
        lanes.getValue(priority).pollFirst()

    @Synchronized
    fun hasWork(): Boolean = lanes.values.any { it.isNotEmpty() }

    @Synchronized
    fun hasWork(priority: PacketPriority): Boolean = lanes.getValue(priority).isNotEmpty()

    @Synchronized
    fun clear() {
        lanes.values.forEach { it.clear() }
    }

    companion object {
        const val DEFAULT_MAX_PER_LANE = 256
    }
}
