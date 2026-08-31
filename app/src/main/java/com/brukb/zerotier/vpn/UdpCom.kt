package com.brukb.zerotier.vpn

import android.util.Log
import com.brukb.zerotier.vpn.scheduling.PacketScheduler
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

class UdpCom(
    private val scheduler: PacketScheduler,
    private val socket: DatagramSocket,
) : Runnable {
    override fun run() {
        Log.i(TAG, "UDP listen thread started localPort=${socket.localPort}")
        val buffer = ByteArray(16384)
        val received = AtomicLong()
        try {
            while (!Thread.currentThread().isInterrupted) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    if (packet.length > 0) {
                        val n = received.incrementAndGet()
                        if (n <= 8 || n % 50L == 0L) {
                            Log.i(
                                TAG,
                                "UDP recv #$n from ${packet.address}:${packet.port} len=${packet.length}",
                            )
                        }
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
        Log.i(TAG, "UDP listen thread ended recvd=${received.get()}")
    }

    companion object {
        private const val TAG = "UdpCom"
    }
}
