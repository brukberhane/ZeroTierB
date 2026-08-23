package com.brukb.zerotier.vpn

import android.util.Log
import com.brukb.zerotier.vpn.scheduling.PacketClassifier
import com.brukb.zerotier.vpn.scheduling.PacketPriority
import com.brukb.zerotier.vpn.scheduling.PacketScheduler
import com.zerotier.sdk.ResultCode
import com.zerotier.sdk.VirtualNetworkConfig
import com.zerotier.sdk.VirtualNetworkFrameListener
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface TunTapHost {
    fun getVirtualNetworkConfig(networkId: Long): VirtualNetworkConfig?

    fun processVirtualNetworkFrame(
        now: Long,
        networkId: Long,
        sourceMac: Long,
        destMac: Long,
        etherType: Int,
        vlanId: Int,
        frameData: ByteArray,
    ): ResultCode

    fun processWirePacket(
        now: Long,
        localSocket: Long,
        remote: java.net.InetSocketAddress,
        packet: ByteArray,
    ): ResultCode

    fun multicastSubscribe(networkId: Long, mac: Long, adi: Long = 0L): ResultCode
}

class TunTapAdapter(
    private val host: TunTapHost,
    private val scheduler: PacketScheduler,
) : VirtualNetworkFrameListener {
    private val routeEntries = mutableListOf<RouteEntry>()
    private var arpTable = ARPTable()
    private var ndpTable = NDPTable()
    private var inStream: FileInputStream? = null
    private var outStream: FileOutputStream? = null
    private var receiveThread: Thread? = null
    private val tunWriteLock = Any()

    fun setFileStreams(inStream: FileInputStream, outStream: FileOutputStream) {
        this.inStream = inStream
        this.outStream = outStream
    }

    fun addRouteEntry(entry: RouteEntry) {
        synchronized(routeEntries) {
            routeEntries.add(entry)
        }
        scheduler.registerNetwork(entry.networkId)
    }

    fun clearRouteMap() {
        synchronized(routeEntries) {
            routeEntries.clear()
        }
    }

    fun startThreads() {
        val thread = Thread({
            try {
                Log.d(TAG, "TUN receive thread started")
                val buffer = ByteArray(32767)
                while (!Thread.currentThread().isInterrupted) {
                    val readCount = inStream?.read(buffer) ?: -1
                    if (readCount > 0) {
                        val packet = buffer.copyOf(readCount)
                        when (IPPacketUtils.getIPVersion(packet).toInt()) {
                            4 -> handleIPv4Packet(packet)
                            6 -> handleIPv6Packet(packet)
                            else -> Log.e(TAG, "Unknown IP version")
                        }
                    } else {
                        Thread.sleep(10)
                    }
                }
            } catch (_: InterruptedException) {
            } catch (e: IOException) {
                if (!Thread.currentThread().isInterrupted) {
                    Log.e(TAG, "TUN receive error: ${e.message}", e)
                }
            } finally {
                Log.d(TAG, "TUN receive thread ended")
            }
        }, "Tunnel Receive Thread")
        receiveThread = thread
        thread.start()
    }

    fun stopReceiveThread() {
        receiveThread?.let { thread ->
            thread.interrupt()
            try {
                thread.join(2000)
            } catch (_: InterruptedException) {
            }
        }
        receiveThread = null
    }

    fun interrupt() {
        stopReceiveThread()
        synchronized(tunWriteLock) {
            try {
                inStream?.close()
                outStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing streams: ${e.message}", e)
            }
            inStream = null
            outStream = null
        }
    }

    fun join() {
        receiveThread?.join()
    }

    fun isRunning(): Boolean = receiveThread?.isAlive == true

    private fun handleIPv4Packet(packetData: ByteArray) {
        var destIp = IPPacketUtils.getDestIP(packetData) ?: return
        val sourceIp = IPPacketUtils.getSourceIP(packetData) ?: return
        val entry = routeEntryForDestination(destIp) ?: run {
            Log.e(TAG, "No route for destination $destIp")
            return
        }
        val networkId = entry.networkId
        val config = host.getVirtualNetworkConfig(networkId) ?: run {
            Log.e(TAG, "No network config for $networkId")
            return
        }
        val isMulticast = isIPv4Multicast(destIp)
        if (isMulticast) {
            val result = host.multicastSubscribe(networkId, multicastAddressToMac(destIp))
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "multicastSubscribe failed: $result")
            }
        }
        val gateway = entry.route.gateway
        var localV4: InetAddress? = null
        var cidr = 0
        for (address in config.assignedAddresses) {
            if (address.address is Inet4Address) {
                localV4 = address.address
                cidr = address.port
                break
            }
        }
        val destRoute = InetAddressUtils.addressToRouteNo0Route(destIp, cidr)
        val sourceRoute = InetAddressUtils.addressToRouteNo0Route(sourceIp, cidr)
        if (gateway != null && destRoute != sourceRoute) {
            destIp = gateway
        }
        val localAddress = localV4 ?: run {
            Log.e(TAG, "No local IPv4 address")
            return
        }
        val localMac = config.mac
        if (isMulticast || arpTable.hasMacForAddress(destIp)) {
            val destMac = if (isMulticast) {
                multicastAddressToMac(destIp)
            } else {
                arpTable.getMacForAddress(destIp)
            }
            scheduler.enqueueOutboundFrame(
                networkId = networkId,
                sourceMac = localMac,
                destMac = destMac,
                etherType = IPV4_PACKET,
                vlanId = 0,
                frameData = packetData,
            )
        } else {
            Log.d(TAG, "Unknown dest MAC for $destIp, sending ARP")
            val arpPacket = arpTable.getRequestPacket(localMac, localAddress, destIp)
            scheduler.enqueueOutboundFrame(
                networkId = networkId,
                sourceMac = localMac,
                destMac = InetAddressUtils.BROADCAST_MAC_ADDRESS,
                etherType = ARP_PACKET,
                vlanId = 0,
                frameData = arpPacket,
                priority = PacketPriority.CONTROL,
            )
        }
    }

    private fun handleIPv6Packet(packetData: ByteArray) {
        var destIp = IPPacketUtils.getDestIP(packetData) ?: return
        val sourceIp = IPPacketUtils.getSourceIP(packetData) ?: return
        val entry = routeEntryForDestination(destIp) ?: return
        val networkId = entry.networkId
        val config = host.getVirtualNetworkConfig(networkId) ?: return
        if (isIPv6Multicast(destIp)) {
            val result = host.multicastSubscribe(networkId, multicastAddressToMac(destIp))
            if (result != ResultCode.RESULT_OK) {
                Log.e(TAG, "multicastSubscribe failed: $result")
            }
        }
        val gateway = entry.route.gateway
        var localV6: InetAddress? = null
        var cidr = 0
        for (address in config.assignedAddresses) {
            if (address.address is Inet6Address) {
                localV6 = address.address
                cidr = address.port
                break
            }
        }
        val destRoute = InetAddressUtils.addressToRouteNo0Route(destIp, cidr)
        val sourceRoute = InetAddressUtils.addressToRouteNo0Route(sourceIp, cidr)
        if (gateway != null && destRoute != sourceRoute) {
            destIp = gateway
        }
        if (localV6 == null) {
            Log.e(TAG, "No local IPv6 address")
            return
        }
        val localMac = config.mac
        var destMac = 0L
        var sendNs = false
        when {
            isNeighborSolicitation(packetData) -> {
                destMac = if (ndpTable.hasMacForAddress(destIp)) {
                    ndpTable.getMacForAddress(destIp)
                } else {
                    InetAddressUtils.ipv6ToMulticastAddress(destIp)
                }
            }
            isIPv6Multicast(destIp) -> destMac = multicastAddressToMac(destIp)
            isNeighborAdvertisement(packetData) -> {
                destMac = if (ndpTable.hasMacForAddress(destIp)) {
                    ndpTable.getMacForAddress(destIp)
                } else {
                    0L
                }
                sendNs = true
            }
            ndpTable.hasMacForAddress(destIp) -> destMac = ndpTable.getMacForAddress(destIp)
            else -> {
                destMac = 0L
                sendNs = true
            }
        }
        if (destMac != 0L) {
            scheduler.enqueueOutboundFrame(
                networkId = networkId,
                sourceMac = localMac,
                destMac = destMac,
                etherType = IPV6_PACKET,
                vlanId = 0,
                frameData = packetData,
            )
        }
        if (sendNs) {
            if (destMac == 0L) {
                destMac = InetAddressUtils.ipv6ToMulticastAddress(destIp)
            }
            val nsPacket = ndpTable.getNeighborSolicitationPacket(sourceIp, destIp, localMac)
            scheduler.enqueueOutboundFrame(
                networkId = networkId,
                sourceMac = localMac,
                destMac = destMac,
                etherType = IPV6_PACKET,
                vlanId = 0,
                frameData = nsPacket,
                priority = PacketPriority.CONTROL,
            )
        }
    }

    override fun onVirtualNetworkFrame(
        networkId: Long,
        srcMac: Long,
        destMac: Long,
        etherType: Long,
        vlanId: Long,
        frameData: ByteArray,
    ) {
        when (etherType.toInt()) {
            ARP_PACKET -> {
                val arpReply = arpTable.processARPPacket(frameData) ?: return
                if (arpReply.destMac == 0L || arpReply.destAddress == null) return
                val networkConfig = host.getVirtualNetworkConfig(networkId) ?: return
                var localV4: InetAddress? = null
                for (address in networkConfig.assignedAddresses) {
                    if (address.address is Inet4Address) {
                        localV4 = address.address
                        break
                    }
                }
                if (localV4 != null) {
                    val reply = arpTable.getReplyPacket(
                        networkConfig.mac,
                        localV4,
                        arpReply.destMac,
                        arpReply.destAddress,
                    )
                    scheduler.enqueueOutboundFrame(
                        networkId = networkId,
                        sourceMac = networkConfig.mac,
                        destMac = srcMac,
                        etherType = ARP_PACKET,
                        vlanId = 0,
                        frameData = reply,
                        priority = PacketPriority.CONTROL,
                    )
                }
            }
            IPV4_PACKET -> {
                try {
                    val sourceIp = IPPacketUtils.getSourceIP(frameData)
                    if (sourceIp != null) {
                        if (isIPv4Multicast(sourceIp)) {
                            val result = host.multicastSubscribe(networkId, multicastAddressToMac(sourceIp))
                            if (result != ResultCode.RESULT_OK) {
                                Log.e(TAG, "multicastSubscribe failed: $result")
                            }
                        } else {
                            arpTable.setAddress(sourceIp, srcMac)
                        }
                    }
                    synchronized(tunWriteLock) {
                        val out = outStream ?: run {
                            Log.w(TAG, "Drop IPv4 frame: TUN output closed (src=$sourceIp)")
                            return
                        }
                        out.write(frameData)
                    }
                    Log.d(TAG, "Wrote IPv4 frame to TUN (${frameData.size} bytes, src=$sourceIp)")
                } catch (e: Exception) {
                    Log.e(TAG, "IPv4 write error: ${e.message}", e)
                }
            }
            IPV6_PACKET -> {
                try {
                    val sourceIp = IPPacketUtils.getSourceIP(frameData)
                    if (sourceIp != null) {
                        if (isIPv6Multicast(sourceIp)) {
                            val result = host.multicastSubscribe(networkId, multicastAddressToMac(sourceIp))
                            if (result != ResultCode.RESULT_OK) {
                                Log.e(TAG, "multicastSubscribe failed: $result")
                            }
                        } else {
                            ndpTable.setAddress(sourceIp, srcMac)
                        }
                    }
                    synchronized(tunWriteLock) {
                        val out = outStream ?: run {
                            Log.w(TAG, "Drop IPv6 frame: TUN output closed")
                            return
                        }
                        out.write(frameData)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "IPv6 write error: ${e.message}", e)
                }
            }
        }
    }

    private fun routeEntryForDestination(dest: InetAddress): RouteEntry? {
        synchronized(routeEntries) {
            return RouteSelector.select(routeEntries, dest)
        }
    }

    private fun isIPv4Multicast(address: InetAddress): Boolean =
        (address.address[0].toInt() and 0xF0) == 224

    private fun isIPv6Multicast(address: InetAddress): Boolean =
        (address.address[0].toInt() and 0xFF) == 0xFF

    private fun isNeighborSolicitation(packet: ByteArray): Boolean =
        packet.size > 40 && packet[6] == 58.toByte() && packet[40] == 0x87.toByte()

    private fun isNeighborAdvertisement(packet: ByteArray): Boolean =
        packet.size > 40 && packet[6] == 58.toByte() && packet[40] == 0x88.toByte()

    companion object {
        private const val TAG = "TunTapAdapter"
        private const val ARP_PACKET = PacketClassifier.ARP_ETHER_TYPE
        private const val IPV4_PACKET = PacketClassifier.IPV4_ETHER_TYPE
        private const val IPV6_PACKET = PacketClassifier.IPV6_ETHER_TYPE

        fun multicastAddressToMac(address: InetAddress): Long {
            return when (address) {
                is Inet4Address -> {
                    val raw = address.address
                    ByteBuffer.wrap(
                        byteArrayOf(0, 0, 1, 0, 94, (raw[1].toInt() and 0x7F).toByte(), raw[2], raw[3]),
                    ).order(ByteOrder.LITTLE_ENDIAN).long
                }
                is Inet6Address -> {
                    val raw = address.address
                    ByteBuffer.wrap(
                        byteArrayOf(0, 0, 0x33, 0x33, raw[12], raw[13], raw[14], raw[15]),
                    ).order(ByteOrder.LITTLE_ENDIAN).long
                }
                else -> 0L
            }
        }
    }
}
