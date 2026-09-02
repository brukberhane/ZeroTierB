package com.brukb.zerotier.proxy.dns

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.ProxyDebugLog
import com.brukb.zerotier.ztlib.ZtNetworkStatus
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class DnsResolver(
    private val uplink: UplinkDnsClient,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val networkResolvers = mutableMapOf<Long, ZtDnsBackend>()

    @Volatile
    var failOpen: Boolean = true

    @Volatile
    var fallbackServers: List<InetAddress> = emptyList()

    private val negativeUntil = mutableMapOf<String, Long>()
    private val inflight = ConcurrentHashMap<String, CompletableFuture<List<InetAddress>>>()

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
            routePriority = config.routePriority,
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
        inflight.clear()
    }

    fun resolve(host: String): List<InetAddress> {
        val normalized = host.trimEnd('.').lowercase()
        inflight[normalized]?.let { return it.get() }
        val mine = CompletableFuture<List<InetAddress>>()
        val raced = inflight.putIfAbsent(normalized, mine)
        if (raced != null) return raced.get()
        return try {
            val addrs = resolveOnce(normalized)
            mine.complete(addrs)
            addrs
        } catch (e: Exception) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            inflight.remove(normalized, mine)
        }
    }

    private fun resolveOnce(normalized: String): List<InetAddress> {
        val t0 = elapsedRealtime()
        val domains = synchronized(this) {
            networkResolvers.values.joinToString(";") { "'${it.domainLabel}'" }.ifEmpty { "-" }
        }
        val ztMatch = pickResolver(normalized)
        if (ztMatch != null && ztMatch.shouldResolve(normalized)) {
            when (val r = ztMatch.resolve(normalized)) {
                is DnsLookupResult.Ok -> {
                    logOk(normalized, "zt-domain", r.addresses, t0, domains)
                    return r.addresses
                }
                is DnsLookupResult.NxDomain, is DnsLookupResult.NoData -> {
                    rememberNegative(normalized)
                    logFail(normalized, "zt-domain", t0, domains)
                    return emptyList()
                }
                is DnsLookupResult.Failure -> {
                    ProxyDebugLog.w(
                        "dns FAIL host=$normalized via=zt-domain ms=${elapsedRealtime() - t0} " +
                            "ztDomains=$domains err=${r.reason}",
                    )
                }
            }
        }
        if (negativeHit(normalized, t0)) return emptyList()

        when (val netd = uplink.lookupNetd(normalized, NETD_TIMEOUT_MS)) {
            is DnsLookupResult.Ok -> {
                logOk(normalized, "netd", netd.addresses, t0, domains)
                return netd.addresses
            }
            is DnsLookupResult.NxDomain -> return ztNxFallback(normalized, t0, domains)
            is DnsLookupResult.NoData -> {
                rememberNegative(normalized)
                logFail(normalized, "netd-nodata", t0, domains)
                return emptyList()
            }
            is DnsLookupResult.Failure -> {
                ProxyDebugLog.w(
                    "dns FAIL host=$normalized via=netd ms=${elapsedRealtime() - t0} " +
                        "failOpen=$failOpen ztDomains=$domains err=${netd.reason}",
                )
            }
        }

        if (failOpen) {
            for (server in fallbackServers) {
                when (val r = uplink.lookupUdp(server, normalized, FALLBACK_DNS_TIMEOUT_MS)) {
                    is DnsLookupResult.Ok -> {
                        logOk(normalized, "udp", r.addresses, t0, domains)
                        return r.addresses
                    }
                    is DnsLookupResult.NxDomain -> return ztNxFallback(normalized, t0, domains)
                    is DnsLookupResult.NoData -> {
                        rememberNegative(normalized)
                        logFail(normalized, "udp-nodata", t0, domains)
                        return emptyList()
                    }
                    is DnsLookupResult.Failure -> {
                        ProxyDebugLog.w(
                            "dns FAIL host=$normalized via=udp server=${server.hostAddress} " +
                                "ms=${elapsedRealtime() - t0} err=${r.reason}",
                        )
                    }
                }
            }
        }

        logFail(normalized, "uplink", t0, domains)
        return emptyList()
    }

    private fun ztNxFallback(host: String, t0: Long, domains: String): List<InetAddress> {
        val resolvers = synchronized(this) { networkResolvers.values.toList() }
        for (zt in resolvers) {
            when (val r = zt.resolve(host)) {
                is DnsLookupResult.Ok -> {
                    logOk(host, "zt-nx", r.addresses, t0, domains)
                    return r.addresses
                }
                is DnsLookupResult.NxDomain, is DnsLookupResult.NoData, is DnsLookupResult.Failure -> {
                    /* try next net */
                }
            }
        }
        rememberNegative(host)
        logFail(host, "zt-nx", t0, domains)
        return emptyList()
    }

    private fun negativeHit(host: String, t0: Long): Boolean {
        synchronized(this) {
            val until = negativeUntil[host] ?: 0L
            if (elapsedRealtime() < until) {
                ProxyDebugLog.w("dns FAIL host=$host via=cache-neg ms=${elapsedRealtime() - t0}")
                return true
            }
        }
        return false
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
    private fun pickResolver(host: String): ZtDnsBackend? {
        val matches = networkResolvers.values.filter { it.shouldResolve(host) }
        if (matches.isEmpty()) return null
        return matches.minWith(
            compareBy<ZtDnsBackend> { it.routePriority }
                .thenComparator { a, b ->
                    java.lang.Long.compareUnsigned(a.networkId, b.networkId)
                },
        )
    }

    @VisibleForTesting
    internal fun replaceBackendForTest(networkId: Long, backend: ZtDnsBackend) {
        synchronized(this) {
            networkResolvers[networkId] = backend
        }
    }

    companion object {
        const val NETD_TIMEOUT_MS = 4_000
        const val FALLBACK_DNS_TIMEOUT_MS = 2_000
        const val NEGATIVE_CACHE_MS = 5_000L
    }
}
