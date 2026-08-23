package com.brukb.zerotier.vpn

import android.util.Log
import com.brukb.zerotier.vpn.scheduling.PacketScheduler
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

class UdpCom(
    private val scheduler: PacketScheduler,
    private val socket: DatagramSocket,
) : Runnable {
    override fun run() {
        Log.d(TAG, "UDP listen thread started")
        val buffer = ByteArray(16384)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    if (packet.length > 0) {
                        val data = packet.data.copyOf(packet.length)
                        val remote = java.net.InetSocketAddress(packet.address, packet.port)
                        scheduler.enqueueInboundWire(-1, remote, data)
                    }
                } catch (_: SocketTimeoutException) {
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "UDP thread error", e)
        }
        Log.d(TAG, "UDP listen thread ended")
    }

    companion object {
        private const val TAG = "UdpCom"
    }
}
