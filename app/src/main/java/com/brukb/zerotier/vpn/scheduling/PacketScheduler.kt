package com.brukb.zerotier.vpn.scheduling

import android.util.Log
import com.brukb.zerotier.vpn.TunTapHost
import com.zerotier.sdk.ResultCode
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Fair scheduler between per-network outbound queues and the global inbound wire queue.
 */
class PacketScheduler(
    private val host: TunTapHost,
    private val classifier: PacketClassifier = PacketClassifier(),
    private val controlBudget: Int = DEFAULT_CONTROL_BUDGET,
    private val bulkBudget: Int = DEFAULT_BULK_BUDGET,
    private val wireBudget: Int = DEFAULT_WIRE_BUDGET,
) {
    private val outboundQueues = ConcurrentHashMap<Long, NetworkPacketQueue>()
    private val wireQueue = WireInboundQueue()
    private val paused = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    @Volatile
    private var schedulerThread: Thread? = null
    @Volatile
    private var roundRobinIndex = 0

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val thread = Thread(::schedulerLoop, "Packet Scheduler")
        schedulerThread = thread
        thread.start()
        Log.d(TAG, "Packet scheduler started")
    }

    fun stop() {
        running.set(false)
        schedulerThread?.interrupt()
        val thread = schedulerThread
        if (thread != null) {
            LockSupport.unpark(thread)
            try {
                thread.join(2000)
            } catch (_: InterruptedException) {
            }
        }
        schedulerThread = null
        outboundQueues.clear()
        wireQueue.clear()
        Log.d(TAG, "Packet scheduler stopped")
    }

    fun pause() {
        paused.set(true)
    }

    fun resume() {
        paused.set(false)
        unparkScheduler()
    }

    fun registerNetwork(networkId: Long) {
        outboundQueues.computeIfAbsent(networkId) { NetworkPacketQueue() }
    }

    fun unregisterNetwork(networkId: Long) {
        outboundQueues.remove(networkId)?.clear()
    }

    fun enqueueOutboundFrame(
        networkId: Long,
        sourceMac: Long,
        destMac: Long,
        etherType: Int,
        vlanId: Int,
        frameData: ByteArray,
        priority: PacketPriority? = null,
    ) {
        if (!running.get()) return
        val resolvedPriority = priority ?: when (etherType) {
            PacketClassifier.ARP_ETHER_TYPE -> classifier.classifyArpFrame()
            PacketClassifier.IPV4_ETHER_TYPE,
            PacketClassifier.IPV6_ETHER_TYPE,
            -> classifier.classifyOutboundIp(frameData, etherType)
            else -> PacketPriority.BULK
        }
        val queue = outboundQueues.computeIfAbsent(networkId) { NetworkPacketQueue() }
        queue.enqueue(
            ScheduledPacket.OutboundFrame(
                networkId = networkId,
                sourceMac = sourceMac,
                destMac = destMac,
                etherType = etherType,
                vlanId = vlanId,
                frameData = frameData,
                priority = resolvedPriority,
            ),
        )
        unparkScheduler()
    }

    fun enqueueInboundWire(
        localSocket: Long,
        remote: InetSocketAddress,
        packet: ByteArray,
    ) {
        if (!running.get()) return
        val priority = classifier.classifyInboundWire(packet)
        wireQueue.enqueue(
            ScheduledPacket.InboundWire(
                localSocket = localSocket,
                remote = remote,
                packet = packet,
                priority = priority,
            ),
        )
        unparkScheduler()
    }

    fun hasPendingWork(): Boolean {
        if (wireQueue.hasWork()) return true
        return outboundQueues.values.any { it.hasWork() }
    }

    private fun unparkScheduler() {
        LockSupport.unpark(schedulerThread)
    }

    private fun schedulerLoop() {
        while (running.get()) {
            try {
                if (paused.get()) {
                    Thread.sleep(10)
                    continue
                }
                val processed = processRound()
                if (processed == 0) {
                    LockSupport.parkNanos(IDLE_PARK_NS)
                }
            } catch (_: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.e(TAG, "Scheduler loop error", e)
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    /** @return number of packets processed this round */
    private fun processRound(): Int {
        var processed = 0
        val networkIds = outboundQueues.keys().toList().sorted()
        if (networkIds.isNotEmpty()) {
            if (roundRobinIndex >= networkIds.size) roundRobinIndex = 0
            val networkId = networkIds[roundRobinIndex]
            roundRobinIndex = (roundRobinIndex + 1) % networkIds.size
            processed += drainOutboundNetwork(networkId)
        }
        processed += drainWireInbound()
        return processed
    }

    private fun drainOutboundNetwork(networkId: Long): Int {
        val queue = outboundQueues[networkId] ?: return 0
        var processed = 0
        for (priority in PacketPriority.PROCESSING_ORDER) {
            repeat(if (priority == PacketPriority.CONTROL) controlBudget else bulkBudget) {
                val packet = queue.poll(priority) ?: return@repeat
                if (processOutbound(packet) == ResultCode.RESULT_OK) {
                    processed++
                }
            }
        }
        return processed
    }

    private fun drainWireInbound(): Int {
        var processed = 0
        for (priority in PacketPriority.PROCESSING_ORDER) {
            val budget = if (priority == PacketPriority.CONTROL) wireBudget else wireBudget / 2
            repeat(budget) {
                val packet = wireQueue.poll(priority) ?: return@repeat
                if (processInboundWire(packet) == ResultCode.RESULT_OK) {
                    processed++
                }
            }
        }
        return processed
    }

    private fun processOutbound(packet: ScheduledPacket.OutboundFrame): ResultCode {
        val result = host.processVirtualNetworkFrame(
            packet.timestampMs,
            packet.networkId,
            packet.sourceMac,
            packet.destMac,
            packet.etherType,
            packet.vlanId,
            packet.frameData,
        )
        if (result != ResultCode.RESULT_OK) {
            Log.e(TAG, "processVirtualNetworkFrame failed: $result (nw=${packet.networkId})")
        }
        return result
    }

    private fun processInboundWire(packet: ScheduledPacket.InboundWire): ResultCode {
        val result = host.processWirePacket(
            packet.timestampMs,
            packet.localSocket,
            packet.remote,
            packet.packet,
        )
        if (result != ResultCode.RESULT_OK) {
            Log.e(TAG, "processWirePacket failed: $result")
        }
        return result
    }

    companion object {
        private const val TAG = "PacketScheduler"
        const val DEFAULT_CONTROL_BUDGET = 8
        const val DEFAULT_BULK_BUDGET = 4
        const val DEFAULT_WIRE_BUDGET = 8
        private const val IDLE_PARK_NS = 2_000_000L // 2ms
    }
}
