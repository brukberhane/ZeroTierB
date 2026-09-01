package com.brukb.zerotier.proxy.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class DnsMessageTest {
    @Test
    fun buildQuery_containsLabelsAndType() {
        val query = DnsMessage.buildQuery("www.example.com", DnsMessage.TYPE_A)
        assertTrue(query.size > 12)
        val labels = String(query, 12, query.size - 16, Charsets.ISO_8859_1)
        assertTrue(labels.contains("www"))
        assertTrue(labels.contains("example"))
        assertEquals(DnsMessage.TYPE_A, ((query[query.size - 4].toInt() and 0xFF) shl 8) or (query[query.size - 3].toInt() and 0xFF))
    }

    @Test
    fun parseAnswers_readsCompressedARecord() {
        val packet = byteArrayOf(
            0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
            0x07, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(),
            'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            0x03, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0x00,
            0x00, 0x01, 0x00, 0x01,
            0xC0.toByte(), 0x0C,
            0x00, 0x01, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x3C,
            0x00, 0x04,
            1, 2, 3, 4,
        )
        val addrs = DnsMessage.parseAnswers(packet, packet.size)
        assertEquals(listOf(InetAddress.getByName("1.2.3.4")), addrs)
    }
}
