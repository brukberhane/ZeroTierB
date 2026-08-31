package com.brukb.zerotier.connection

import com.brukb.zerotier.proxy.IpClassification

fun formatAssignedCidr(host: String?, prefix: Int): String? {
    if (host.isNullOrBlank()) return null
    return "$host/$prefix"
}

fun formatRouteLine(prefixCidr: String, via: String?): String {
    val trimmedVia = via?.trim().orEmpty()
    if (trimmedVia.isEmpty() || trimmedVia == "0.0.0.0" || trimmedVia == "::") {
        return prefixCidr
    }
    return "$prefixCidr → $trimmedVia"
}

fun filterDisplayRoutes(
    routes: List<String>,
    allowManaged: Boolean,
    allowDefault: Boolean,
    allowGlobal: Boolean,
): List<String> {
    if (!allowManaged) return emptyList()
    return routes.filter { routeLine ->
        val cidr = routeLine.substringBefore(" → ").trim()
        shouldIncludeDisplayRoute(cidr, allowDefault, allowGlobal)
    }
}

private fun shouldIncludeDisplayRoute(cidr: String, allowDefault: Boolean, allowGlobal: Boolean): Boolean {
    if (isDefaultRoute(cidr)) return allowDefault
    val ip = cidr.substringBefore('/')
    if (IpClassification.isPrivateOrLocal(ip)) return true
    return allowGlobal
}

private fun isDefaultRoute(cidr: String): Boolean {
    return cidr.startsWith("0.0.0.0/0") || cidr == "::/0" || cidr.startsWith("::/0")
}
