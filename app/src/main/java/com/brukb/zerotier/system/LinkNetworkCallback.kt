package com.brukb.zerotier.system

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class LinkNetworkCallback(
    private val onEvent: () -> Unit,
) : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        onEvent()
    }

    override fun onLost(network: Network) {
        onEvent()
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        onEvent()
    }
}
