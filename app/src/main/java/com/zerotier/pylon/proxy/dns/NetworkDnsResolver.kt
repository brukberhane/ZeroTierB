package com.zerotier.pylon.proxy.dns

import com.zerotier.sockets.ZeroTierDatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.ByteBuffer

class NetworkDnsResolver(
    val networkId: Long,
    val domain: String,
    val servers: List<String>,
) {
    fun shouldResolve(host: String): Boolean {
        val normalized = host.trimEnd('.').lowercase()
        if (domain.isBlank()) {
            return !normalized.contains('.') ||
                normalized.endsWith(".local") ||
                normalized.endsWith(".home.arpa")
        }
        val suffix = domain.trimEnd('.').lowercase()
        return normalized == suffix || normalized.endsWith(".$suffix")
    }

    fun resolve(host: String): List<InetAddress> {
        for (server in servers) {
            val result = queryServer(server, host)
            if (result.isNotEmpty()) return result
        }
        return InetAddress.getAllByName(host).toList()
    }

    private fun queryServer(server: String, host: String): List<InetAddress> {
        return runCatching {
            val query = buildDnsQuery(host)
            val socket = ZeroTierDatagramSocket()
            socket.soTimeout = 3000
            val packet = DatagramPacket(query, query.size, InetAddress.getByName(server), 53)
            socket.send(packet)
            val responseBuffer = ByteArray(512)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)
            parseDnsResponse(responseBuffer, responsePacket.length)
        }.getOrElse { emptyList() }
    }

    private fun buildDnsQuery(host: String): ByteArray {
        val buffer = ByteBuffer.allocate(512)
        buffer.putShort(0x1234.toShort())
        buffer.putShort(0x0100.toShort())
        buffer.putShort(1)
        buffer.putShort(0)
        buffer.putShort(0)
        buffer.putShort(0)
        host.split('.').forEach { label ->
            buffer.put(label.length.toByte())
            buffer.put(label.toByteArray())
        }
        buffer.put(0)
        buffer.putShort(1)
        buffer.putShort(1)
        val array = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(array)
        return array
    }

    private fun parseDnsResponse(response: ByteArray, length: Int): List<InetAddress> {
        if (length < 12) return emptyList()
        val answerCount = ((response[6].toInt() and 0xFF) shl 8) or (response[7].toInt() and 0xFF)
        if (answerCount == 0) return emptyList()
        var offset = 12
        while (offset < length && response[offset] != 0.toByte()) {
            val len = response[offset].toInt() and 0xFF
            offset += if (len and 0xC0 == 0xC0) 2 else len + 1
        }
        offset += 5
        val results = mutableListOf<InetAddress>()
        repeat(answerCount.coerceAtMost(8)) {
            if (offset + 10 >= length) return@repeat
            if (response[offset].toInt() and 0xC0 == 0xC0) offset += 2 else {
                while (offset < length && response[offset] != 0.toByte()) {
                    offset += (response[offset].toInt() and 0xFF) + 1
                }
                offset++
            }
            val type = ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
            offset += 8
            val rdLength = ((response[offset].toInt() and 0xFF) shl 8) or (response[offset + 1].toInt() and 0xFF)
            offset += 2
            if (offset + rdLength > length) return@repeat
            when (type) {
                1 -> if (rdLength == 4) {
                    results.add(InetAddress.getByAddress(response.copyOfRange(offset, offset + 4)))
                }
                28 -> if (rdLength == 16) {
                    results.add(InetAddress.getByAddress(response.copyOfRange(offset, offset + 16)))
                }
            }
            offset += rdLength
        }
        return results
    }
}
