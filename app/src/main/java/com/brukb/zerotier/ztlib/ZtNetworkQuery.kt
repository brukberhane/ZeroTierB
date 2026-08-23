package com.brukb.zerotier.ztlib

import com.zerotier.sockets.ZeroTierNative

object ZtNetworkQuery {
    inline fun <T> withCoreLock(block: () -> T): T {
        ZeroTierNative.zts_core_lock_obtain()
        return try {
            block()
        } finally {
            ZeroTierNative.zts_core_lock_release()
        }
    }

    fun setNetworkSettings(
        networkId: Long,
        allowManaged: Boolean,
        allowGlobal: Boolean,
        allowDefault: Boolean,
    ): Int {
        return ZeroTierNative.zts_net_set_settings(
            networkId,
            if (allowManaged) 1 else 0,
            if (allowGlobal) 1 else 0,
            if (allowDefault) 1 else 0,
        )
    }

    fun queryAssignedCidrs(networkId: Long): List<String> = withCoreLock {
        val count = ZeroTierNative.zts_core_query_addr_count(networkId)
        if (count <= 0) return@withCoreLock emptyList()
        buildList {
            repeat(count) { idx ->
                ZeroTierNative.zts_core_query_addr_cidr(networkId, idx)?.let { add(it) }
            }
        }
    }

    fun queryManagedRouteCidrs(networkId: Long): List<String> = withCoreLock {
        val count = ZeroTierNative.zts_core_query_route_count(networkId)
        if (count <= 0) return@withCoreLock emptyList()
        buildList {
            repeat(count) { idx ->
                ZeroTierNative.zts_core_query_route_cidr(networkId, idx)?.let { add(it) }
            }
        }
    }

    fun queryDnsDomain(networkId: Long): String = withCoreLock {
        ZeroTierNative.zts_core_query_dns_domain(networkId).orEmpty()
    }

    fun queryDnsServers(networkId: Long): List<String> = withCoreLock {
        val count = ZeroTierNative.zts_core_query_dns_count(networkId)
        if (count <= 0) return@withCoreLock emptyList()
        buildList {
            repeat(count) { idx ->
                ZeroTierNative.zts_core_query_dns_server(networkId, idx)?.let { add(it) }
            }
        }
    }

    fun queryPathCount(peerId: Long): Int = withCoreLock {
        ZeroTierNative.zts_core_query_path_count(peerId)
    }
}
