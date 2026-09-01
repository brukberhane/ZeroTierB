package com.brukb.zerotier.proxy.dns

import com.zerotier.sockets.ZeroTierDatagramSocket
import com.brukb.zerotier.proxy.ProxyDebugLog
import java.net.DatagramPacket
import java.net.InetAddress

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
            val query = DnsMessage.buildQuery(host)
            val socket = ZeroTierDatagramSocket()
            socket.soTimeout = 3000
            val packet = DatagramPacket(query, query.size, InetAddress.getByName(server), 53)
            socket.send(packet)
            val responseBuffer = ByteArray(512)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)
            DnsMessage.parseAnswers(responseBuffer, responsePacket.length)
        }.getOrElse { err ->
            ProxyDebugLog.w("dns-zt FAIL server=$server host=$host err=${err.message}")
            emptyList()
        }
    }
}
