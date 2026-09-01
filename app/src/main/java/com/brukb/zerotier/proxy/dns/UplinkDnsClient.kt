package com.brukb.zerotier.proxy.dns

import java.net.InetAddress

/**
 * Uplink (non-ZeroTier) DNS. Must not use [InetAddress.getAllByName] —
 * that hits netd / Private DNS and deadlocks with Global HTTP_PROXY.
 */
interface UplinkDnsClient {
    fun hasPrivateDns(): Boolean
    fun lookupPrivate(host: String, timeoutMs: Int): List<InetAddress>
    fun lookupLink(host: String, timeoutMs: Int): List<InetAddress>
}
