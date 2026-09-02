package com.brukb.zerotier.proxy.dns

import com.brukb.zerotier.proxy.ProxyDebugLog
import com.zerotier.sockets.ZeroTierDatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress

class NetworkDnsResolver(
    override val networkId: Long,
    val domain: String,
    val servers: List<String>,
    override val routePriority: Int = 0,
) : ZtDnsBackend {
    override val domainLabel: String get() = domain

    override fun shouldResolve(host: String): Boolean {
        val normalized = host.trimEnd('.').lowercase()
        if (domain.isBlank()) {
            return !normalized.contains('.') ||
                normalized.endsWith(".local") ||
                normalized.endsWith(".home.arpa")
        }
        val suffix = domain.trimEnd('.').lowercase()
        return normalized == suffix || normalized.endsWith(".$suffix")
    }

    override fun resolve(host: String): DnsLookupResult {
        var sawNx = false
        for (server in servers) {
            when (val result = queryServer(server, host)) {
                is DnsLookupResult.Ok -> return result
                is DnsLookupResult.NxDomain -> sawNx = true
                is DnsLookupResult.NoData -> return DnsLookupResult.NoData
                is DnsLookupResult.Failure -> { /* try next */ }
            }
        }
        return if (sawNx) DnsLookupResult.NxDomain else DnsLookupResult.Failure("all servers failed")
    }

    private fun queryServer(server: String, host: String): DnsLookupResult {
        return combineAThenAaaa(queryOnce(server, host, DnsMessage.TYPE_A)) {
            queryOnce(server, host, DnsMessage.TYPE_AAAA)
        }
    }

    private fun queryOnce(server: String, host: String, type: Int): DnsLookupResult {
        return try {
            val socket = ZeroTierDatagramSocket()
            try {
                socket.soTimeout = 1500
                val query = DnsMessage.buildQuery(host, type)
                val packet = DatagramPacket(query, query.size, InetAddress.getByName(server), 53)
                socket.send(packet)
                val responseBuffer = ByteArray(512)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)
                DnsMessage.toLookupResult(responseBuffer, responsePacket.length)
            } finally {
                runCatching { socket.close() }
            }
        } catch (err: Exception) {
            ProxyDebugLog.w("dns-zt FAIL server=$server host=$host type=$type err=${err.message}")
            DnsLookupResult.Failure(err.message ?: "zt")
        }
    }
}
