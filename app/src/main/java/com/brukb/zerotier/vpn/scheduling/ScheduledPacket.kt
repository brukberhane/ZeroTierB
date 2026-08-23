package com.brukb.zerotier.vpn.scheduling

import java.net.InetSocketAddress

sealed class ScheduledPacket {
    abstract val priority: PacketPriority
    abstract val timestampMs: Long

    /** TUN → ZeroTier virtual network frame (outbound). */
    data class OutboundFrame(
        val networkId: Long,
        val sourceMac: Long,
        val destMac: Long,
        val etherType: Int,
        val vlanId: Int,
        val frameData: ByteArray,
        override val priority: PacketPriority,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : ScheduledPacket()

    /** Internet UDP → ZeroTier wire packet (inbound). */
    data class InboundWire(
        val localSocket: Long,
        val remote: InetSocketAddress,
        val packet: ByteArray,
        override val priority: PacketPriority,
        override val timestampMs: Long = System.currentTimeMillis(),
    ) : ScheduledPacket()
}
