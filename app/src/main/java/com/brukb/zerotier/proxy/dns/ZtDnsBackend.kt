package com.brukb.zerotier.proxy.dns

interface ZtDnsBackend {
    val networkId: Long
    val routePriority: Int get() = 0
    val domainLabel: String get() = ""

    fun shouldResolve(host: String): Boolean

    fun resolve(host: String): DnsLookupResult
}
