package com.zerotier.pylon

import android.app.Application
import com.zerotier.pylon.data.AppDatabase
import com.zerotier.pylon.data.AppPreferences
import com.zerotier.pylon.data.NetworkRepository

class PylonApplication : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var networkRepository: NetworkRepository
        private set

    lateinit var preferences: AppPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        networkRepository = NetworkRepository(database.networkDao())
        preferences = AppPreferences(this)
    }
}
