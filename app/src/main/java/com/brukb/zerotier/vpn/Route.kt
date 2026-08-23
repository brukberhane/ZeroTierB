package com.brukb.zerotier.vpn

import java.net.InetAddress

data class Route(
    val address: InetAddress,
    val prefix: Int,
    var gateway: InetAddress? = null,
) {
    fun belongsToRoute(dest: InetAddress): Boolean {
        val routePrefix = InetAddressUtils.addressToRouteNo0Route(dest, prefix)
        return address == routePrefix
    }
}

data class RouteEntry(
    val route: Route,
    val networkId: Long,
    val priority: Int,
)
