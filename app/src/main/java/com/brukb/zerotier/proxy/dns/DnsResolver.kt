package com.brukb.zerotier.proxy.dns

import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import java.net.InetAddress

class DnsResolver {
    private val networkResolvers = mutableMapOf<Long, NetworkDnsResolver>()

    @Synchronized
    fun updateNetwork(config: ZerotierBNetwork, status: ZtNetworkStatus) {
        if (!config.allowDns) {
            networkResolvers.remove(config.networkIdLong())
            return
        }
        val servers = status.dnsServers
        if (servers.isEmpty()) {
            networkResolvers.remove(config.networkIdLong())
            return
        }
        networkResolvers[config.networkIdLong()] = NetworkDnsResolver(
            networkId = config.networkIdLong(),
            domain = status.dnsDomain,
            servers = servers,
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
        val resolver = pickResolver(normalized)
        return try {
            if (resolver != null && resolver.shouldResolve(normalized)) {
                resolver.resolve(normalized)
            } else {
                InetAddress.getAllByName(normalized).toList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    private fun pickResolver(host: String): NetworkDnsResolver? {
        if (networkResolvers.isEmpty()) return null
        return networkResolvers.values.firstOrNull { it.shouldResolve(host) }
            ?: networkResolvers.values.firstOrNull()
    }
}
