package com.brukb.zerotier

import android.app.Application
import android.util.Log
import com.brukb.zerotier.data.AppDatabase
import com.brukb.zerotier.data.AppPreferences
import com.brukb.zerotier.data.LinkProfileRepository
import com.brukb.zerotier.data.NetworkRepository
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.proxy.SystemProxyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ZerotierBApplication : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var networkRepository: NetworkRepository
        private set

    lateinit var linkProfileRepository: LinkProfileRepository
        private set

    lateinit var preferences: AppPreferences
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        networkRepository = NetworkRepository(database.networkDao())
        linkProfileRepository = LinkProfileRepository(database.linkProfileDao())
        preferences = AppPreferences(this)
        appScope.launch {
            networkRepository.migrateStoredNetworkIds()
            preferences.migrateGlobalModeIfNeeded()
            linkProfileRepository.seedOther()
            runCatching {
                val mgr = SystemProxyManager(this@ZerotierBApplication, preferences)
                val mode = preferences.globalMode.first()
                val current = mgr.currentProxy()
                val saved = preferences.savedHttpProxy.first()
                val lastPort = preferences.lastHttpProxyPort.first()
                if (SystemProxyManager.shouldClearStale(current, saved, lastPort, mode == GlobalMode.PROXY)) {
                    mgr.disable()
                    Log.i(TAG, "Cleared stale system proxy (mode=$mode)")
                }
            }
        }
    }

    companion object {
        private const val TAG = "ZerotierBApplication"
    }
}
