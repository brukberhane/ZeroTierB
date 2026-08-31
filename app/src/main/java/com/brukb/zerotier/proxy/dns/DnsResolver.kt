package com.brukb.zerotier.proxy.dns

import android.os.SystemClock
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.ProxyDebugLog
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import java.net.InetAddress

class DnsResolver {
    private val networkResolvers = mutableMapOf<Long, NetworkDnsResolver>()

    @Synchronized
    fun updateNetwork(config: ZerotierBNetwork, status: ZtNetworkStatus) {
        val netHex = java.lang.Long.toUnsignedString(config.networkIdLong(), 16)
        if (!config.allowDns) {
            networkResolvers.remove(config.networkIdLong())
            ProxyDebugLog.i("dns-cfg net=$netHex allowDns=false (removed)")
            return
        }
        val servers = status.dnsServers
        if (servers.isEmpty()) {
            networkResolvers.remove(config.networkIdLong())
            ProxyDebugLog.i("dns-cfg net=$netHex allowDns=true servers=empty domain=${status.dnsDomain}")
            return
        }
        networkResolvers[config.networkIdLong()] = NetworkDnsResolver(
            networkId = config.networkIdLong(),
            domain = status.dnsDomain,
            servers = servers,
        )
        ProxyDebugLog.i(
            "dns-cfg net=$netHex domain='${status.dnsDomain}' servers=$servers",
        )
    }

    @Synchronized
    fun removeNetwork(networkId: Long) {
        networkResolvers.remove(networkId)
    }

    @Synchronized
    fun clear() {
        networkResolvers.clear()
    }

    fun resolve(host: String): List<InetAddress> {
        val normalized = host.trimEnd('.')
        val t0 = SystemClock.elapsedRealtime()
        val resolver = pickResolver(normalized)
        val domains = synchronized(this) {
            networkResolvers.values.joinToString(";") { "'${it.domain}'" }.ifEmpty { "-" }
        }
        return try {
            val (via, addrs) = if (resolver != null && resolver.shouldResolve(normalized)) {
                "zt" to resolver.resolve(normalized)
            } else {
                "system" to InetAddress.getAllByName(normalized).toList()
            }
            val ms = SystemClock.elapsedRealtime() - t0
            val ips = addrs.mapNotNull { it.hostAddress }.joinToString(",")
            ProxyDebugLog.i(
                "dns host=$normalized via=$via addrs=[$ips] ms=$ms ztDomains=$domains",
            )
            addrs
        } catch (e: Exception) {
            val ms = SystemClock.elapsedRealtime() - t0
            ProxyDebugLog.w("dns FAIL host=$normalized via=system ms=$ms ztDomains=$domains err=${e.message}")
            emptyList()
        }
    }

    @Synchronized
    private fun pickResolver(host: String): NetworkDnsResolver? {
        if (networkResolvers.isEmpty()) return null
        return networkResolvers.values.firstOrNull { it.shouldResolve(host) }
    }
}
