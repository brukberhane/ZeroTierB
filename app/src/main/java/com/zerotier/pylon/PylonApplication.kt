package com.zerotier.pylon

import android.app.Application
import android.os.PowerManager
import android.util.Log
import com.zerotier.pylon.data.AppDatabase
import com.zerotier.pylon.data.AppPreferences
import com.zerotier.pylon.data.NetworkRepository
import com.zerotier.pylon.proxy.SystemProxyManager
import com.zerotier.pylon.system.ProxyWatchdog
import com.zerotier.pylon.system.ShizukuPermissionHelper
import rikka.shizuku.Shizuku

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

        val wanted = preferences.serviceWantedBlocking()
        val startOnBoot = preferences.startOnBootBlocking()
        if (!wanted && !startOnBoot) {
            SystemProxyManager(this, preferences).clearIfOurs()
        }

        val binderListener = Shizuku.OnBinderReceivedListener {
            if (!preferences.privilegedWatchdogEnabledBlocking()) return@OnBinderReceivedListener
            if (!ShizukuPermissionHelper.canRunPrivileged()) return@OnBinderReceivedListener
            val power = getSystemService(PowerManager::class.java)
            if (power.isInteractive && !power.isDeviceIdleMode) {
                ProxyWatchdog.startIfNeeded(this)
            }
        }
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderListener) }
            .onFailure { Log.w(TAG, "Shizuku binder listener not registered", it) }
    }

    companion object {
        private const val TAG = "PylonApplication"
    }
}
