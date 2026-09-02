package com.brukb.zerotier.proxy.dns

import java.net.InetAddress

sealed class DnsLookupResult {
    data class Ok(val addresses: List<InetAddress>) : DnsLookupResult()
    data object NxDomain : DnsLookupResult()
    data object NoData : DnsLookupResult()
    data class Failure(val reason: String) : DnsLookupResult()
}
