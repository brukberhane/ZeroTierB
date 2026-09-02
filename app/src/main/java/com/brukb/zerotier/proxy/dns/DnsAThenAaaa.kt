package com.brukb.zerotier.proxy.dns

fun combineAThenAaaa(a: DnsLookupResult, aaaa: () -> DnsLookupResult): DnsLookupResult =
    when (a) {
        is DnsLookupResult.Ok -> a
        is DnsLookupResult.NxDomain, is DnsLookupResult.NoData -> when (val v6 = aaaa()) {
            is DnsLookupResult.Ok -> v6
            is DnsLookupResult.Failure -> v6
            else -> a
        }
        is DnsLookupResult.Failure -> when (val v6 = aaaa()) {
            is DnsLookupResult.Ok -> v6
            is DnsLookupResult.NxDomain, is DnsLookupResult.NoData -> v6
            is DnsLookupResult.Failure -> a
        }
    }
