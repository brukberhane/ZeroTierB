package com.brukb.zerotier.vpn

import java.net.InetAddress

object RouteSelector {
    fun select(entries: List<RouteEntry>, dest: InetAddress): RouteEntry? {
        val matches = entries.filter { it.route.belongsToRoute(dest) }
        if (matches.isEmpty()) return null
        val maxPrefix = matches.maxOf { it.route.prefix }
        val candidates = matches.filter { it.route.prefix == maxPrefix }
        return candidates.minWith(compareBy<RouteEntry> { it.priority }.thenBy { it.networkId })
    }
}
