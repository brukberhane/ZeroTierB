package com.brukb.zerotier.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.SubscriptionManager
import com.brukb.zerotier.vpn.ZerotierBVpnService

class LinkClassifier(
    private val context: Context,
    private val connectivityManager: ConnectivityManager,
    private val modeLookup: LinkModeLookup,
) {
    fun isOurVpnRunning(): Boolean = ZerotierBVpnService.state.value.isRunning

    suspend fun classify(dataSubscriptionId: Int?): PhysicalLink {
        val active = connectivityManager.activeNetwork
        val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }
        if (caps == null) return PhysicalLink.None

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            // Public SDK has no NetworkCapabilities.getUnderlyingNetworks();
            // strip VPN via scan (PhysicalLinkSelector) when ours, else Other.
            return if (isOurVpnRunning()) {
                classifyFromScan(dataSubscriptionId)
            } else {
                otherLink()
            }
        }

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> wifiLink(caps)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                mobileLink(dataSubscriptionId)
            else -> otherLink()
        }
    }

    private suspend fun classifyFromScan(dataSubscriptionId: Int?): PhysicalLink {
        val candidates = mutableListOf<Pair<LinkCandidate, Network>>()
        for (network in connectivityManager.allNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            candidates += LinkCandidate(
                isVpn = isVpn,
                isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
            ) to network
        }
        val candidateList = candidates.map { it.first }
        val picked = PhysicalLinkSelector.pick(candidateList) ?: return PhysicalLink.None
        val index = candidateList.indexOf(picked)
        if (index < 0) return PhysicalLink.None
        val network = candidates[index].second
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return PhysicalLink.None
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> wifiLink(caps)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                mobileLink(dataSubscriptionId)
            else -> otherLink()
        }
    }

    private suspend fun wifiLink(caps: NetworkCapabilities): PhysicalLink {
        val ssid = SsidNormalizer.normalize(readSsid(caps))
        if (ssid == null) return PhysicalLink.WifiUnknown
        val mode = modeLookup.modeForSsid(ssid) ?: return PhysicalLink.WifiUnknown
        return PhysicalLink.WifiKnown(ssid, mode)
    }

    private suspend fun mobileLink(dataSubscriptionId: Int?): PhysicalLink {
        val subId = dataSubscriptionId ?: SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
        val mode = modeLookup.modeForSubscription(subId)
        return PhysicalLink.Mobile(subId, mode)
    }

    private suspend fun otherLink(): PhysicalLink =
        PhysicalLink.Other(modeLookup.modeForOther())

    @Suppress("DEPRECATION")
    private fun readSsid(caps: NetworkCapabilities): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val info = caps.transportInfo as? WifiInfo
            if (info != null) return info.ssid
        }
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        return wifi?.connectionInfo?.ssid
    }
}
