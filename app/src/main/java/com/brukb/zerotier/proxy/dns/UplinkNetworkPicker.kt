package com.brukb.zerotier.proxy.dns

import com.brukb.zerotier.data.model.UplinkDnsPreference

data class UplinkCandidate(
    val id: Long,
    val isVpn: Boolean,
    val isWifi: Boolean,
    val isCellular: Boolean,
    val hasInternet: Boolean,
)

object UplinkNetworkPicker {
    fun pick(candidates: List<UplinkCandidate>, preference: UplinkDnsPreference): UplinkCandidate? {
        val remaining = candidates.filter { !it.isVpn }
        if (remaining.isEmpty()) return null
        val withInternet = remaining.filter { it.hasInternet }
        val pool = if (withInternet.isNotEmpty()) withInternet else remaining
        val wifi = pool.filter { it.isWifi }
        val cell = pool.filter { it.isCellular }
        val other = pool.filter { !it.isWifi && !it.isCellular }
        return when (preference) {
            UplinkDnsPreference.WIFI_FIRST ->
                wifi.firstOrNull() ?: cell.firstOrNull() ?: other.firstOrNull()
            UplinkDnsPreference.CELLULAR_FIRST ->
                cell.firstOrNull() ?: wifi.firstOrNull() ?: other.firstOrNull()
        }
    }
}
