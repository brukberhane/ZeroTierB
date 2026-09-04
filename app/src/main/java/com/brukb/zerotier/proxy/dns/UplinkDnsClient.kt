package com.brukb.zerotier.proxy.dns

import java.net.InetAddress

/**
 * Uplink (non-ZeroTier) DNS. Must not use [InetAddress.getAllByName] —
 * that hits netd / Private DNS and deadlocks with Global HTTP_PROXY.
 *
 * [lookupNetd] uses android.net.DnsResolver or [Network.getAllByName] on the
 * physical uplink. [lookupUdp] is only for explicit nameserver IPs (Settings
 * fallbacks) that netd cannot target. [lookupLinkDns] is UDP/53 to the picked
 * uplink’s LinkProperties DNS servers, bound with Network.bindSocket. It is
 * not gated by failOpen. Call only after netd Failure — not after Nx/NoData.
 */
interface UplinkDnsClient {
    fun lookupNetd(host: String, timeoutMs: Int): DnsLookupResult
    fun lookupUdp(server: InetAddress, host: String, timeoutMs: Int): DnsLookupResult
    fun lookupLinkDns(host: String, timeoutMs: Int): DnsLookupResult
}
