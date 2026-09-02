package com.brukb.zerotier.proxy.dns

import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.random.Random

object DnsMessage {
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val RCODE_NXDOMAIN = 3

    fun rcode(response: ByteArray, length: Int): Int {
        if (length < 4) return -1
        return response[3].toInt() and 0x0F
    }

    fun toLookupResult(response: ByteArray, length: Int): DnsLookupResult {
        val rc = rcode(response, length)
        if (rc == RCODE_NXDOMAIN) return DnsLookupResult.NxDomain
        val addrs = parseAnswers(response, length)
        if (addrs.isNotEmpty()) return DnsLookupResult.Ok(addrs)
        if (rc == 0) return DnsLookupResult.NoData
        return DnsLookupResult.Failure("rcode=$rc empty")
    }

    fun buildQuery(host: String, type: Int = TYPE_A): ByteArray {
        val buffer = ByteBuffer.allocate(512)
        buffer.putShort(Random.nextInt(0, 65536).toShort())
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
        buffer.putShort(type.toShort())
        buffer.putShort(1)
        val array = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(array)
        return array
    }

    fun parseAnswers(response: ByteArray, length: Int): List<InetAddress> {
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
            if (response[offset].toInt() and 0xC0 == 0xC0) {
                offset += 2
            } else {
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
                TYPE_A -> if (rdLength == 4) {
                    results.add(InetAddress.getByAddress(response.copyOfRange(offset, offset + 4)))
                }
                TYPE_AAAA -> if (rdLength == 16) {
                    results.add(InetAddress.getByAddress(response.copyOfRange(offset, offset + 16)))
                }
            }
            offset += rdLength
        }
        return results
    }
}
