package com.zerotier.pylon.proxy

import com.zerotier.pylon.data.model.PylonNetwork
import com.zerotier.pylon.zt.ZtNetworkStatus
import java.net.InetAddress

data class RouteDecision(
    val useZeroTier: Boolean,
    val networkId: Long? = null,
    val block: Boolean = false,
    val reason: String = "",
)

class RouteResolver {
    private data class NetworkRoutes(
        val networkId: Long,
        val config: PylonNetwork,
        val prefixes: List<IpPrefix>,
    )

    private val networks = mutableMapOf<Long, NetworkRoutes>()

    @Synchronized
    fun updateNetwork(config: PylonNetwork, status: ZtNetworkStatus) {
        if (!config.enabledInProxy || status.status != ZtNetworkStatus.Status.OK) {
            networks.remove(config.networkIdLong())
            return
        }
        val prefixes = buildPrefixes(config, status)
        networks[config.networkIdLong()] = NetworkRoutes(config.networkIdLong(), config, prefixes)
    }

    @Synchronized
    fun removeNetwork(networkId: Long) {
        networks.remove(networkId)
    }

    @Synchronized
    fun clear() {
        networks.clear()
    }

    @Synchronized
    fun resolveHost(host: String, resolvedAddresses: List<InetAddress>): RouteDecision {
        if (networks.isEmpty()) {
            return RouteDecision(useZeroTier = false, reason = "no networks")
        }
        var best: RouteDecision? = null
        var bestPrefix = -1
        for (address in resolvedAddresses) {
            val decision = resolveAddress(address)
            if (decision.block) return decision
            if (decision.useZeroTier) {
                val prefixLen = prefixLengthFor(address.hostAddress ?: continue, decision.networkId)
                if (prefixLen > bestPrefix) {
                    bestPrefix = prefixLen
                    best = decision
                }
            }
        }
        if (best != null) return best
        val anyBlockOutside = networks.values.any { it.config.blockOutside }
        return if (anyBlockOutside) {
            RouteDecision(useZeroTier = false, block = true, reason = "outside blocked")
        } else {
            RouteDecision(useZeroTier = false, reason = "default outside")
        }
    }

    @Synchronized
    fun resolveAddress(address: InetAddress): RouteDecision {
        val host = address.hostAddress ?: return RouteDecision(useZeroTier = false)
        return resolveIpString(host)
    }

    @Synchronized
    fun resolveIpString(ip: String): RouteDecision {
        if (networks.isEmpty()) {
            return RouteDecision(useZeroTier = false, reason = "no networks")
        }
        var bestNetworkId: Long? = null
        var bestPrefix = -1
        for ((networkId, routes) in networks) {
            for (prefix in routes.prefixes) {
                if (prefix.contains(ip) && prefix.prefixLength > bestPrefix) {
                    bestPrefix = prefix.prefixLength
                    bestNetworkId = networkId
                }
            }
        }
        if (bestNetworkId != null) {
            return RouteDecision(
                useZeroTier = true,
                networkId = bestNetworkId,
                reason = "longest-prefix match",
            )
        }
        val blockOutside = networks.values.any { it.config.blockOutside }
        return if (blockOutside) {
            RouteDecision(useZeroTier = false, block = true, reason = "outside blocked")
        } else {
            RouteDecision(useZeroTier = false, reason = "outside")
        }
    }

    @Synchronized
    private fun prefixLengthFor(ip: String, networkId: Long?): Int {
        if (networkId == null) return 0
        val routes = networks[networkId]?.prefixes.orEmpty()
        return routes.filter { it.contains(ip) }.maxOfOrNull { it.prefixLength } ?: 0
    }

    private fun buildPrefixes(config: PylonNetwork, status: ZtNetworkStatus): List<IpPrefix> {
        val prefixes = mutableListOf<IpPrefix>()
        status.assignedAddresses.forEach { addr ->
            runCatching { prefixes.add(IpPrefix.parse(normalizeCidr(addr))) }
        }
        if (config.allowManaged) {
            status.routes.forEach { route ->
                if (shouldIncludeRoute(route, config)) {
                    runCatching { prefixes.add(IpPrefix.parse(normalizeCidr(route))) }
                }
            }
        }
        return prefixes.distinctBy { "${it.networkAddress.contentHashCode()}/${it.prefixLength}" }
    }

    private fun normalizeCidr(addr: String): String {
        return when {
            addr.contains('/') -> addr
            addr.contains(':') -> "$addr/128"
            else -> "$addr/32"
        }
    }

    private fun shouldIncludeRoute(cidr: String, config: PylonNetwork): Boolean {
        if (isDefaultRoute(cidr)) return config.allowDefault
        val ip = cidr.substringBefore('/')
        if (IpClassification.isPrivateOrLocal(ip)) return true
        return config.allowGlobal
    }

    private fun isDefaultRoute(cidr: String): Boolean {
        return cidr.startsWith("0.0.0.0/0") || cidr == "::/0" || cidr.startsWith("::/0")
    }
}

data class IpPrefix(
    val networkAddress: ByteArray,
    val prefixLength: Int,
    val isIpv6: Boolean,
) {
    fun contains(ip: String): Boolean {
        val address = InetAddress.getByName(ip)
        val target = address.address
        if (target.size != networkAddress.size) return false
        var bitsRemaining = prefixLength
        for (i in target.indices) {
            if (bitsRemaining <= 0) break
            val mask = if (bitsRemaining >= 8) 0xFF else (0xFF shl (8 - bitsRemaining)) and 0xFF
            if ((target[i].toInt() and mask) != (networkAddress[i].toInt() and mask)) {
                return false
            }
            bitsRemaining -= 8
        }
        return true
    }

    companion object {
        fun parse(cidr: String): IpPrefix {
            val parts = cidr.split('/')
            val address = InetAddress.getByName(parts[0])
            val prefix = parts.getOrNull(1)?.toInt()
                ?: if (address.address.size == 16) 128 else 32
            return IpPrefix(address.address, prefix, address.address.size == 16)
        }
    }
}

object IpClassification {
    fun isPrivateOrLocal(ip: String): Boolean {
        val address = runCatching { InetAddress.getByName(ip) }.getOrNull() ?: return false
        if (address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            return when {
                b0 == 10 -> true
                b0 == 172 && b1 in 16..31 -> true
                b0 == 192 && b1 == 168 -> true
                b0 == 169 && b1 == 254 -> true
                else -> false
            }
        }
        return address.isMulticastAddress || bytes[0] == 0xfd.toByte()
    }
}
