package com.brukb.zerotier.proxy.dns

import java.net.InetAddress

/**
 * Uplink (non-ZeroTier) DNS. Must not use [InetAddress.getAllByName] —
 * that hits netd / Private DNS and deadlocks with Global HTTP_PROXY.
 *
 * [lookupNetd] uses android.net.DnsResolver or [Network.getAllByName] on the
 * physical uplink. [lookupUdp] is only for explicit nameserver IPs (Settings
 * fallbacks) that netd cannot target.
 */
interface UplinkDnsClient {
    fun lookupNetd(host: String, timeoutMs: Int): DnsLookupResult
    fun lookupUdp(server: InetAddress, host: String, timeoutMs: Int): DnsLookupResult
}
