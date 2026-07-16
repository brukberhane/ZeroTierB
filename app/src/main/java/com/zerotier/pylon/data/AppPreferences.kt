package com.zerotier.pylon.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pylon_prefs")

class AppPreferences(private val context: Context) {
    val httpProxyPort: Flow<Int> = context.dataStore.data.map {
        it[KEY_HTTP_PORT] ?: DEFAULT_HTTP_PORT
    }

    val socks5ProxyPort: Flow<Int> = context.dataStore.data.map {
        it[KEY_SOCKS5_PORT] ?: DEFAULT_SOCKS5_PORT
    }

    val socks5Enabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SOCKS5_ENABLED] ?: false
    }

    val startOnBoot: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_START_ON_BOOT] ?: false
    }

    val proxyEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_PROXY_ENABLED] ?: true
    }

    val savedHttpProxy: Flow<String?> = context.dataStore.data.map {
        it[KEY_SAVED_HTTP_PROXY]
    }

    suspend fun setHttpProxyPort(port: Int) {
        context.dataStore.edit { it[KEY_HTTP_PORT] = port }
    }

    suspend fun setSocks5ProxyPort(port: Int) {
        context.dataStore.edit { it[KEY_SOCKS5_PORT] = port }
    }

    suspend fun setSocks5Enabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOCKS5_ENABLED] = enabled }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[KEY_START_ON_BOOT] = enabled }
    }

    suspend fun setProxyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PROXY_ENABLED] = enabled }
    }

    suspend fun saveHttpProxy(value: String?) {
        context.dataStore.edit {
            if (value == null) {
                it.remove(KEY_SAVED_HTTP_PROXY)
            } else {
                it[KEY_SAVED_HTTP_PROXY] = value
            }
        }
    }

    companion object {
        const val DEFAULT_HTTP_PORT = 8123
        const val DEFAULT_SOCKS5_PORT = 1080

        private val KEY_HTTP_PORT = intPreferencesKey("http_proxy_port")
        private val KEY_SOCKS5_PORT = intPreferencesKey("socks5_proxy_port")
        private val KEY_SOCKS5_ENABLED = booleanPreferencesKey("socks5_enabled")
        private val KEY_START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        private val KEY_PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        private val KEY_SAVED_HTTP_PROXY = stringPreferencesKey("saved_http_proxy")
    }
}
