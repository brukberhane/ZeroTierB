package com.brukb.zerotier.proxy.dns

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import com.brukb.zerotier.proxy.ProxyDebugLog
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class AndroidUplinkDnsClient(context: Context) : UplinkDnsClient {
    private val connectivity =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    @Volatile private var cachedDotServers: Pair<String, List<InetAddress>>? = null

    override fun hasPrivateDns(): Boolean = privateDnsName(linkProperties()) != null

    override fun lookupPrivate(host: String, timeoutMs: Int): List<InetAddress> {
        if (timeoutMs <= 0) return emptyList()
        val network = pickUplink() ?: return emptyList()
        val lp = connectivity.getLinkProperties(network) ?: return emptyList()
        val sni = privateDnsName(lp) ?: return emptyList()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        val servers = dotServers(network, sni, remaining(deadline))
        if (servers.isEmpty()) {
            ProxyDebugLog.w("dns-private bootstrap empty sni=$sni host=$host")
            return emptyList()
        }
        for (server in servers) {
            val remaining = remaining(deadline)
            if (remaining <= 0) break
            val addrs = queryDot(network, server, sni, host, remaining)
            if (addrs.isNotEmpty()) return addrs
        }
        return emptyList()
    }

    override fun lookupLink(host: String, timeoutMs: Int): List<InetAddress> {
        if (timeoutMs <= 0) return emptyList()
        val network = pickUplink() ?: return emptyList()
        val servers = connectivity.getLinkProperties(network)?.dnsServers.orEmpty()
        if (servers.isEmpty()) return emptyList()
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        for (server in servers.take(2)) {
            val remaining = remaining(deadline)
            if (remaining <= 0) break
            val addrs = queryUdp(network, server, host, remaining)
            if (addrs.isNotEmpty()) return addrs
        }
        return emptyList()
    }

    private fun dotServers(network: Network, sni: String, timeoutMs: Int): List<InetAddress> {
        cachedDotServers?.let { (name, ips) ->
            if (name == sni && ips.isNotEmpty()) return ips
        }
        val ips = queryUdp(network, null, sni, timeoutMs)
        if (ips.isNotEmpty()) {
            cachedDotServers = sni to ips
        }
        return ips
    }

    private fun queryDot(
        network: Network,
        server: InetAddress,
        sni: String,
        host: String,
        timeoutMs: Int,
    ): List<InetAddress> {
        if (timeoutMs <= 0) return emptyList()
        return runCatching {
            val factory = SSLSocketFactory.getDefault()
            val socket = factory.createSocket() as SSLSocket
            try {
                runCatching { network.bindSocket(socket) }
                socket.soTimeout = timeoutMs
                val params = socket.sslParameters
                params.serverNames = listOf(SNIHostName(sni))
                params.endpointIdentificationAlgorithm = "HTTPS"
                socket.sslParameters = params
                socket.connect(InetSocketAddress(server, DOT_PORT), timeoutMs)
                socket.startHandshake()
                val results = mutableListOf<InetAddress>()
                results += dotQuery(socket, host, DnsMessage.TYPE_A)
                if (results.isEmpty()) {
                    results += dotQuery(socket, host, DnsMessage.TYPE_AAAA)
                }
                results
            } finally {
                runCatching { socket.close() }
            }
        }.getOrElse { err ->
            ProxyDebugLog.w("dns-private FAIL sni=$sni server=$server host=$host err=${err.message}")
            emptyList()
        }
    }

    private fun dotQuery(socket: SSLSocket, host: String, type: Int): List<InetAddress> {
        val query = DnsMessage.buildQuery(host, type)
        val out = socket.outputStream
        out.write(query.size ushr 8)
        out.write(query.size)
        out.write(query)
        out.flush()
        val input = DataInputStream(socket.inputStream)
        val length = input.readUnsignedShort()
        if (length <= 0 || length > 4096) return emptyList()
        val response = ByteArray(length)
        input.readFully(response)
        return DnsMessage.parseAnswers(response, response.size)
    }

    private fun queryUdp(
        network: Network,
        preferredServer: InetAddress?,
        host: String,
        timeoutMs: Int,
    ): List<InetAddress> {
        if (timeoutMs <= 0) return emptyList()
        val servers = if (preferredServer != null) {
            listOf(preferredServer)
        } else {
            connectivity.getLinkProperties(network)?.dnsServers.orEmpty()
        }
        if (servers.isEmpty()) return emptyList()
        val perServer = (timeoutMs / servers.size.coerceAtLeast(1)).coerceAtLeast(500)
        for (server in servers) {
            val addrs = udpOnce(network, server, host, DnsMessage.TYPE_A, perServer)
            if (addrs.isNotEmpty()) return addrs
            val aaaa = udpOnce(network, server, host, DnsMessage.TYPE_AAAA, perServer)
            if (aaaa.isNotEmpty()) return aaaa
        }
        return emptyList()
    }

    private fun udpOnce(
        network: Network,
        server: InetAddress,
        host: String,
        type: Int,
        timeoutMs: Int,
    ): List<InetAddress> {
        return runCatching {
            DatagramSocket().use { socket ->
                runCatching { network.bindSocket(socket) }
                socket.soTimeout = timeoutMs
                val query = DnsMessage.buildQuery(host, type)
                socket.send(DatagramPacket(query, query.size, server, 53))
                val buf = ByteArray(512)
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                DnsMessage.parseAnswers(buf, packet.length)
            }
        }.getOrElse { err ->
            ProxyDebugLog.w("dns-link FAIL server=$server host=$host type=$type err=${err.message}")
            emptyList()
        }
    }

    private fun pickUplink(): Network? {
        val active = connectivity.activeNetwork
        if (active != null && !isVpn(active)) return active
        val networks = connectivity.allNetworks.filter { !isVpn(it) && hasInternet(it) }
        return networks.firstOrNull { hasTransport(it, NetworkCapabilities.TRANSPORT_WIFI) }
            ?: networks.firstOrNull { hasTransport(it, NetworkCapabilities.TRANSPORT_CELLULAR) }
            ?: networks.firstOrNull()
    }

    private fun isVpn(network: Network): Boolean =
        hasTransport(network, NetworkCapabilities.TRANSPORT_VPN)

    private fun hasInternet(network: Network): Boolean {
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun hasTransport(network: Network, transport: Int): Boolean {
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(transport)
    }

    private fun linkProperties(): LinkProperties? {
        val network = pickUplink() ?: return null
        return connectivity.getLinkProperties(network)
    }

    private fun privateDnsName(lp: LinkProperties?): String? {
        if (lp == null || Build.VERSION.SDK_INT < 28) return null
        if (!lp.isPrivateDnsActive) return null
        return lp.privateDnsServerName?.takeIf { it.isNotBlank() }
    }

    private fun remaining(deadlineElapsed: Long): Int =
        (deadlineElapsed - SystemClock.elapsedRealtime()).toInt().coerceAtLeast(0)

    private companion object {
        const val DOT_PORT = 853
    }
}