package com.zerotier.pylon.proxy

import com.zerotier.pylon.data.model.PylonNetwork
import com.zerotier.pylon.zt.ZtNetworkStatus
import java.net.InetAddress

class ProxyRulesEngine {
    fun isAllowed(
        host: String,
        port: Int,
        network: PylonNetwork?,
        decision: RouteDecision,
    ): Boolean {
        if (decision.block) return false
        val deny = parseRules(network?.denyRules.orEmpty())
        if (deny.any { it.matches(host, port) }) return false
        val allow = parseRules(network?.allowRules.orEmpty())
        if (allow.isEmpty()) return true
        return allow.any { it.matches(host, port) }
    }

    private fun parseRules(raw: String): List<Rule> {
        if (raw.isBlank()) return emptyList()
        return raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .mapNotNull { line ->
                val parts = line.split(':', limit = 2)
                when (parts.size) {
                    1 -> Rule(parts[0], null)
                    2 -> Rule(parts[0], parts[1].toIntOrNull())
                    else -> null
                }
            }
    }

    private data class Rule(val hostPattern: String, val port: Int?) {
        fun matches(host: String, port: Int): Boolean {
            val hostMatch = when {
                hostPattern == "*" -> true
                hostPattern.startsWith("*.") -> host.endsWith(hostPattern.removePrefix("*"))
                else -> host.equals(hostPattern, ignoreCase = true)
            }
            val portMatch = this.port == null || this.port == port
            return hostMatch && portMatch
        }
    }
}
