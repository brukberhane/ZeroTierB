package com.brukb.zerotier.system

import android.content.Context
import android.net.ConnectivityManager
import android.telephony.SubscriptionManager
import com.brukb.zerotier.log.AppLog
import com.brukb.zerotier.connection.ConnectionOrchestrator
import com.brukb.zerotier.connection.DataSubscriptionIds
import com.brukb.zerotier.connection.LinkDebouncer
import com.brukb.zerotier.data.AppPreferences
import com.brukb.zerotier.data.LinkProfileRepository
import com.brukb.zerotier.data.model.GlobalMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LinkObserver(
    private val context: Context,
    private val preferences: AppPreferences,
    private val linkProfileRepository: LinkProfileRepository,
    private val orchestrator: ConnectionOrchestrator,
    private val scope: CoroutineScope,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val debouncer = LinkDebouncer(
        scope,
        { preferences.linkDebounceMs.first().toLong() },
        { onQuietPeriod() },
    )
    private val callback = LinkNetworkCallback { debouncer.trigger() }
    private var registered = false
    private var observeJob: Job? = null

    fun start() {
        observeJob = scope.launch {
            preferences.globalMode.collect { mode ->
                if (mode != GlobalMode.OFF) {
                    ensureRegistered()
                } else {
                    ensureUnregistered()
                }
            }
        }
    }

    private fun ensureRegistered() {
        if (registered) return
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(callback)
            registered = true
            debouncer.trigger()
        }.onFailure {
            AppLog.w(TAG, "registerDefaultNetworkCallback failed: ${it.message}")
        }
    }

    private fun ensureUnregistered() {
        if (!registered) return
        runCatching {
            connectivityManager.unregisterNetworkCallback(callback)
        }.onFailure {
            AppLog.w(TAG, "unregisterNetworkCallback failed: ${it.message}")
        }
        registered = false
        scope.launch { debouncer.cancel() }
    }

    private suspend fun onQuietPeriod() {
        upsertDataSimIfNew()
        if (!orchestrator.startAllowed) return
        orchestrator.refresh()
    }

    private suspend fun upsertDataSimIfNew() {
        val id = DataSubscriptionIds.activeDataSubscriptionId()
        if (id == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return
        if (linkProfileRepository.getBySubscriptionId(id) == null) {
            linkProfileRepository.upsertMobile(id, null, "SIM $id", null)
        }
    }

    companion object {
        private const val TAG = "LinkObserver"
    }
}
