package com.brukb.zerotier.vpn.scheduling

import com.brukb.zerotier.vpn.TunTapHost
import com.zerotier.sdk.ResultCode
import com.zerotier.sdk.VirtualNetworkConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class PacketSchedulerFairnessTest {
    @Test
    fun roundRobinProcessesBothNetworks() {
        val host = RecordingHost()
        val scheduler = PacketScheduler(
            host = host,
            controlBudget = 4,
            bulkBudget = 2,
            wireBudget = 0,
        )
        scheduler.start()
        try {
            scheduler.registerNetwork(NET_A)
            scheduler.registerNetwork(NET_B)
            repeat(20) {
                scheduler.enqueueOutboundFrame(
                    networkId = NET_A,
                    sourceMac = 1L,
                    destMac = 2L,
                    etherType = PacketClassifier.IPV4_ETHER_TYPE,
                    vlanId = 0,
                    frameData = byteArrayOf(0x45, 0, 0, 0, 0, 0, 0, 0, 0, 6),
                    priority = PacketPriority.BULK,
                )
            }
            repeat(5) {
                scheduler.enqueueOutboundFrame(
                    networkId = NET_B,
                    sourceMac = 1L,
                    destMac = 2L,
                    etherType = PacketClassifier.IPV4_ETHER_TYPE,
                    vlanId = 0,
                    frameData = byteArrayOf(0x45, 0, 0, 0, 0, 0, 0, 0, 0, 1),
                    priority = PacketPriority.CONTROL,
                )
            }
            Thread.sleep(500)
            assertTrue(host.processedNetworkIds.contains(NET_A))
            assertTrue(host.processedNetworkIds.contains(NET_B))
            val bIndex = host.processedNetworkIds.indexOfFirst { it == NET_B }
            assertTrue("Network B control should be processed early", bIndex in 0..15)
        } finally {
            scheduler.stop()
        }
    }

    private class RecordingHost : TunTapHost {
        val processedNetworkIds = CopyOnWriteArrayList<Long>()

        override fun getVirtualNetworkConfig(networkId: Long): VirtualNetworkConfig? = null

        override fun processVirtualNetworkFrame(
            now: Long,
            networkId: Long,
            sourceMac: Long,
            destMac: Long,
            etherType: Int,
            vlanId: Int,
            frameData: ByteArray,
        ): ResultCode {
            processedNetworkIds.add(networkId)
            return ResultCode.RESULT_OK
        }

        override fun processWirePacket(
            now: Long,
            localSocket: Long,
            remote: InetSocketAddress,
            packet: ByteArray,
        ): ResultCode = ResultCode.RESULT_OK

        override fun multicastSubscribe(networkId: Long, mac: Long, adi: Long): ResultCode =
            ResultCode.RESULT_OK
    }

    companion object {
        private const val NET_A = 0x1111L
        private const val NET_B = 0x2222L
    }
}
