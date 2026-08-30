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
    }
}
