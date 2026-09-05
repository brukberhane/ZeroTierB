package com.brukb.zerotier.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.GlobalModeMigrate
import com.brukb.zerotier.data.model.PlanetSource
import com.brukb.zerotier.data.model.UplinkDnsPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("zerotierb_prefs")

class AppPreferences(private val context: Context) {
    private val startOnBootKey = booleanPreferencesKey("start_on_boot")
    private val vpnAlwaysOnKey = booleanPreferencesKey("vpn_always_on")
    private val globalModeKey = stringPreferencesKey("global_mode")
    private val savedHttpProxyKey = stringPreferencesKey("saved_http_proxy")
    private val lastHttpProxyPortKey = intPreferencesKey("last_http_proxy_port")
    private val linkDebounceMsKey = intPreferencesKey("link_debounce_ms")
    private val batteryOptPromptedKey = booleanPreferencesKey("battery_opt_prompted")
    private val privilegedWatchdogKey = booleanPreferencesKey("privileged_watchdog")
    private val pauseNodeInDozeKey = booleanPreferencesKey("pause_node_in_doze")
    private val reinitNodeOnDozeResumeKey = booleanPreferencesKey("reinit_node_on_doze_resume")
    private val dnsFailOpenKey = booleanPreferencesKey("dns_fail_open")
    private val dnsFallbackServersKey = stringPreferencesKey("dns_fallback_servers")
    private val verboseFileLogKey = booleanPreferencesKey("verbose_file_log")
    private val skipUplinkDnsProbeKey = booleanPreferencesKey("skip_uplink_dns_probe")
    private val uplinkDnsHealKey = booleanPreferencesKey("uplink_dns_heal")
    private val uplinkDnsPreferenceKey = stringPreferencesKey("uplink_dns_preference")
    private val airgapKey = booleanPreferencesKey("airgap")
    private val airgapWithoutMoonsKey = booleanPreferencesKey("airgap_without_moons")
    private val planetSourceKey = stringPreferencesKey("planet_source")

    val startOnBoot: Flow<Boolean> = context.dataStore.data.map { it[startOnBootKey] ?: false }
    val vpnAlwaysOn: Flow<Boolean> = context.dataStore.data.map { it[vpnAlwaysOnKey] ?: false }
    val globalMode: Flow<GlobalMode> = context.dataStore.data.map {
        GlobalMode.parse(it[globalModeKey])
    }
    val savedHttpProxy: Flow<String?> = context.dataStore.data.map { it[savedHttpProxyKey] }
    val lastHttpProxyPort: Flow<Int> = context.dataStore.data.map { it[lastHttpProxyPortKey] ?: 0 }
    val linkDebounceMs: Flow<Int> = context.dataStore.data.map {
        it[linkDebounceMsKey] ?: DEFAULT_LINK_DEBOUNCE_MS
    }
    val privilegedWatchdogEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[privilegedWatchdogKey] ?: false
    }
    val pauseNodeInDoze: Flow<Boolean> = context.dataStore.data.map {
        it[pauseNodeInDozeKey] ?: false
    }
    val reinitNodeOnDozeResume: Flow<Boolean> = context.dataStore.data.map {
        it[reinitNodeOnDozeResumeKey] ?: false
    }
    val dnsFailOpen: Flow<Boolean> = context.dataStore.data.map {
        it[dnsFailOpenKey] ?: true
    }
    val dnsFallbackServers: Flow<List<String>> = context.dataStore.data.map {
        parseDnsFallbackServers(it[dnsFallbackServersKey].orEmpty())
    }
    val verboseFileLog: Flow<Boolean> = context.dataStore.data.map {
        it[verboseFileLogKey] ?: false
    }
    val skipUplinkDnsProbe: Flow<Boolean> = context.dataStore.data.map {
        it[skipUplinkDnsProbeKey] ?: false
    }
    val uplinkDnsHeal: Flow<Boolean> = context.dataStore.data.map {
        it[uplinkDnsHealKey] ?: true
    }
    val uplinkDnsPreference: Flow<UplinkDnsPreference> = context.dataStore.data.map {
        UplinkDnsPreference.parse(it[uplinkDnsPreferenceKey])
    }
    val airgap: Flow<Boolean> = context.dataStore.data.map { it[airgapKey] ?: false }
    val airgapWithoutMoons: Flow<Boolean> = context.dataStore.data.map {
        it[airgapWithoutMoonsKey] ?: false
    }
    val planetSource: Flow<PlanetSource> = context.dataStore.data.map {
        PlanetSource.parse(it[planetSourceKey])
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[startOnBootKey] = enabled }
    }

    suspend fun setVpnAlwaysOn(enabled: Boolean) {
        context.dataStore.edit { it[vpnAlwaysOnKey] = enabled }
    }

    suspend fun setGlobalMode(mode: GlobalMode) {
        context.dataStore.edit { it[globalModeKey] = mode.name }
    }

    suspend fun setSavedHttpProxy(value: String?) {
        context.dataStore.edit { prefs ->
            if (value.isNullOrBlank()) {
                prefs.remove(savedHttpProxyKey)
            } else {
                prefs[savedHttpProxyKey] = value
            }
        }
    }

    suspend fun setLastHttpProxyPort(port: Int) {
        context.dataStore.edit { it[lastHttpProxyPortKey] = port.coerceAtLeast(0) }
    }

    suspend fun setLinkDebounceMs(ms: Int) {
        val clamped = ms.coerceIn(MIN_LINK_DEBOUNCE_MS, MAX_LINK_DEBOUNCE_MS)
        context.dataStore.edit { it[linkDebounceMsKey] = clamped }
    }

    suspend fun hasBatteryOptPrompted(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[batteryOptPromptedKey] ?: false
    }

    suspend fun setBatteryOptPrompted() {
        context.dataStore.edit { it[batteryOptPromptedKey] = true }
    }

    suspend fun setPrivilegedWatchdogEnabled(enabled: Boolean) {
        context.dataStore.edit { it[privilegedWatchdogKey] = enabled }
    }

    suspend fun setPauseNodeInDoze(enabled: Boolean) {
        context.dataStore.edit { it[pauseNodeInDozeKey] = enabled }
    }

    suspend fun setReinitNodeOnDozeResume(enabled: Boolean) {
        context.dataStore.edit { it[reinitNodeOnDozeResumeKey] = enabled }
    }

    suspend fun setDnsFailOpen(enabled: Boolean) {
        context.dataStore.edit { it[dnsFailOpenKey] = enabled }
    }

    suspend fun setDnsFallbackServers(servers: List<String>) {
        val cleaned = sanitizeDnsFallbackServers(servers)
        context.dataStore.edit { it[dnsFallbackServersKey] = cleaned.joinToString("\n") }
    }

    suspend fun setVerboseFileLog(enabled: Boolean) {
        context.dataStore.edit { it[verboseFileLogKey] = enabled }
    }

    suspend fun setSkipUplinkDnsProbe(enabled: Boolean) {
        context.dataStore.edit { it[skipUplinkDnsProbeKey] = enabled }
    }

    suspend fun setUplinkDnsHeal(enabled: Boolean) {
        context.dataStore.edit { it[uplinkDnsHealKey] = enabled }
    }

    suspend fun setUplinkDnsPreference(value: UplinkDnsPreference) {
        context.dataStore.edit { it[uplinkDnsPreferenceKey] = value.name }
    }

    suspend fun setAirgap(enabled: Boolean) {
        context.dataStore.edit { it[airgapKey] = enabled }
    }

    suspend fun setAirgapWithoutMoons(enabled: Boolean) {
        context.dataStore.edit { it[airgapWithoutMoonsKey] = enabled }
    }

    suspend fun setPlanetSource(value: PlanetSource) {
        context.dataStore.edit { it[planetSourceKey] = value.name }
    }

    suspend fun migrateGlobalModeIfNeeded() {
        context.dataStore.edit { prefs ->
            if (prefs.contains(globalModeKey)) return@edit
            val startOnBoot = prefs[startOnBootKey] ?: false
            val mode = GlobalModeMigrate.initial(startOnBoot, stored = null)
            prefs[globalModeKey] = mode.name
        }
    }

    companion object {
        const val DEFAULT_LINK_DEBOUNCE_MS = 5_000
        const val MIN_LINK_DEBOUNCE_MS = 3_000
        const val MAX_LINK_DEBOUNCE_MS = 15_000
        const val MAX_DNS_FALLBACK_SERVERS = 5

        fun parseDnsFallbackServers(raw: String): List<String> =
            raw.split('\n', ',', ' ')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .let { sanitizeDnsFallbackServers(it) }

        fun sanitizeDnsFallbackServers(servers: List<String>): List<String> =
            servers.map { it.trim() }
                .filter { isNumericDnsServer(it) }
                .distinct()
                .take(MAX_DNS_FALLBACK_SERVERS)

        private fun isNumericDnsServer(value: String): Boolean {
            val host = value.removePrefix("[").removeSuffix("]")
            if (host.contains(':')) {
                return host.all {
                    it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.'
                }
            }
            val parts = host.split('.')
            if (parts.size != 4) return false
            return parts.all { part ->
                part.isNotEmpty() &&
                    part.length <= 3 &&
                    part.all { it in '0'..'9' } &&
                    part.toInt() in 0..255
            }
        }
    }
}
