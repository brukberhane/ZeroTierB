package com.brukb.zerotier.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("zerotierb_prefs")

class AppPreferences(private val context: Context) {
    private val startOnBootKey = booleanPreferencesKey("start_on_boot")
    private val vpnAlwaysOnKey = booleanPreferencesKey("vpn_always_on")

    val startOnBoot: Flow<Boolean> = context.dataStore.data.map { it[startOnBootKey] ?: false }
    val vpnAlwaysOn: Flow<Boolean> = context.dataStore.data.map { it[vpnAlwaysOnKey] ?: false }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[startOnBootKey] = enabled }
    }

    suspend fun setVpnAlwaysOn(enabled: Boolean) {
        context.dataStore.edit { it[vpnAlwaysOnKey] = enabled }
    }
}
