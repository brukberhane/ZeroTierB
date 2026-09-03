package com.brukb.zerotier.proxy

import java.net.InetAddress

object SystemProxyEnablePolicy {
    /** IANA example.com — public A/AAAA on working recursive DNS. Not a captive-portal hostname. */
    val PROBE_HOSTS: List<String> = listOf("example.com", "one.one.one.one")

    fun probeSucceeded(addresses: List<InetAddress>): Boolean =
        addresses.isNotEmpty()
}
