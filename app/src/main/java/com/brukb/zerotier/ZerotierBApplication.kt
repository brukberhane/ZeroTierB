package com.brukb.zerotier

import android.app.Application
import com.brukb.zerotier.data.AppDatabase
import com.brukb.zerotier.data.AppPreferences
import com.brukb.zerotier.data.NetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZerotierBApplication : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var networkRepository: NetworkRepository
        private set

    lateinit var preferences: AppPreferences
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        networkRepository = NetworkRepository(database.networkDao())
        preferences = AppPreferences(this)
        appScope.launch {
            networkRepository.migrateStoredNetworkIds()
        }
    }
}
