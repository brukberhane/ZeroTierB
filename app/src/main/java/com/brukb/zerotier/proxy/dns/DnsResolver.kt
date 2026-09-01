package com.brukb.zerotier.proxy.dns

import android.os.SystemClock
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.ProxyDebugLog
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import java.net.InetAddress

class DnsResolver(
    private val uplink: UplinkDnsClient,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val networkResolvers = mutableMapOf<Long, NetworkDnsResolver>()

    @Volatile
    var failOpen: Boolean = true

    private var privateFailCount = 0
    private var skipPrivateUntilElapsed = 0L
    private val negativeUntil = mutableMapOf<String, Long>()

    @Synchronized
    fun updateNetwork(config: ZerotierBNetwork, status: ZtNetworkStatus) {
        val netHex = java.lang.Long.toUnsignedString(config.networkIdLong(), 16)
        if (!config.allowDns) {
            networkResolvers.remove(config.networkIdLong())
            ProxyDebugLog.i("dns-cfg net=$netHex allowDns=false (removed)")
            return
        }
        val servers = status.dnsServers
        if (servers.isEmpty()) {
            networkResolvers.remove(config.networkIdLong())
            ProxyDebugLog.i("dns-cfg net=$netHex allowDns=true servers=empty domain=${status.dnsDomain}")
            return
        }
        networkResolvers[config.networkIdLong()] = NetworkDnsResolver(
            networkId = config.networkIdLong(),
            domain = status.dnsDomain,
            servers = servers,
        )
        ProxyDebugLog.i(
            "dns-cfg net=$netHex domain='${status.dnsDomain}' servers=$servers",
        )
    }

    @Synchronized
    fun removeNetwork(networkId: Long) {
        networkResolvers.remove(networkId)
    }

    @Synchronized
    fun clear() {
        networkResolvers.clear()
        negativeUntil.clear()
        privateFailCount = 0
        skipPrivateUntilElapsed = 0L
    }

    fun resolve(host: String): List<InetAddress> {
        val normalized = host.trimEnd('.')
        val t0 = elapsedRealtime()
        val zt = pickResolver(normalized)
        val domains = synchronized(this) {
            networkResolvers.values.joinToString(";") { "'${it.domain}'" }.ifEmpty { "-" }
        }
        if (zt != null && zt.shouldResolve(normalized)) {
            return try {
                val addrs = zt.resolve(normalized)
                val ms = elapsedRealtime() - t0
                val ips = addrs.mapNotNull { it.hostAddress }.joinToString(",")
                ProxyDebugLog.i("dns host=$normalized via=zt addrs=[$ips] ms=$ms ztDomains=$domains")
                addrs
            } catch (e: Exception) {
                val ms = elapsedRealtime() - t0
                ProxyDebugLog.w("dns FAIL host=$normalized via=zt ms=$ms ztDomains=$domains err=${e.message}")
                emptyList()
            }
        }
        synchronized(this) {
            val until = negativeUntil[normalized] ?: 0L
            if (elapsedRealtime() < until) {
                ProxyDebugLog.w("dns FAIL host=$normalized via=cache-neg ms=${elapsedRealtime() - t0}")
                return emptyList()
            }
        }
        return resolveUplink(normalized, t0, domains)
    }

    private fun resolveUplink(host: String, t0: Long, domains: String): List<InetAddress> {
        val privateConfigured = uplink.hasPrivateDns()
        if (privateConfigured && !shouldSkipPrivate()) {
            val addrs = runCatching { uplink.lookupPrivate(host, PRIVATE_DNS_TIMEOUT_MS) }
                .getOrElse { emptyList() }
            if (addrs.isNotEmpty()) {
                notePrivateSuccess()
                logOk(host, "private", addrs, t0, domains)
                return addrs
            }
            if (notePrivateFailure()) {
                rememberNegative(host)
                logFail(host, "private", t0, domains)
                return emptyList()
            }
        }
        if (!privateConfigured || failOpen) {
            val addrs = runCatching { uplink.lookupLink(host, LINK_DNS_TIMEOUT_MS) }
                .getOrElse { emptyList() }
            if (addrs.isNotEmpty()) {
                logOk(host, "link", addrs, t0, domains)
                return addrs
            }
            rememberNegative(host)
            logFail(host, "link", t0, domains)
            return emptyList()
        }
        rememberNegative(host)
        logFail(host, "private", t0, domains)
        return emptyList()
    }

    private fun rememberNegative(host: String) {
        synchronized(this) {
            negativeUntil[host] = elapsedRealtime() + NEGATIVE_CACHE_MS
        }
    }

    private fun logOk(
        host: String,
        via: String,
        addrs: List<InetAddress>,
        t0: Long,
        domains: String,
    ) {
        val ms = elapsedRealtime() - t0
        val ips = addrs.mapNotNull { it.hostAddress }.joinToString(",")
        ProxyDebugLog.i(
            "dns host=$host via=$via addrs=[$ips] ms=$ms failOpen=$failOpen ztDomains=$domains",
        )
    }

    private fun logFail(host: String, via: String, t0: Long, domains: String) {
        val ms = elapsedRealtime() - t0
        ProxyDebugLog.w(
            "dns FAIL host=$host via=$via ms=$ms failOpen=$failOpen ztDomains=$domains",
        )
    }

    @Synchronized
    private fun shouldSkipPrivate(): Boolean =
        failOpen && elapsedRealtime() < skipPrivateUntilElapsed

    @Synchronized
    private fun notePrivateSuccess() {
        privateFailCount = 0
    }

    /** Returns true if fail-closed (caller must not fall back). */
    @Synchronized
    private fun notePrivateFailure(): Boolean {
        privateFailCount++
        if (failOpen && privateFailCount >= PRIVATE_FAIL_THRESHOLD) {
            skipPrivateUntilElapsed = elapsedRealtime() + PRIVATE_SKIP_MS
            ProxyDebugLog.w("dns-private circuit-open skipMs=$PRIVATE_SKIP_MS")
        }
        return !failOpen
    }

    @Synchronized
    private fun pickResolver(host: String): NetworkDnsResolver? {
        if (networkResolvers.isEmpty()) return null
        return networkResolvers.values.firstOrNull { it.shouldResolve(host) }
    }

    companion object {
        const val PRIVATE_DNS_TIMEOUT_MS = 4_000
        const val LINK_DNS_TIMEOUT_MS = 2_000
        const val PRIVATE_FAIL_THRESHOLD = 2
        const val PRIVATE_SKIP_MS = 30_000L
        const val NEGATIVE_CACHE_MS = 5_000L
    }
}
