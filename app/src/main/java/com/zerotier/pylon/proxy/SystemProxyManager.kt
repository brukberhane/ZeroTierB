package com.zerotier.pylon.proxy

import android.content.Context
import android.provider.Settings
import com.zerotier.pylon.data.AppPreferences
import kotlinx.coroutines.flow.first

class SystemProxyManager(
    private val context: Context,
    private val preferences: AppPreferences,
) {
    private var savedProxy: String? = null

    fun hasPermission(): Boolean {
        return try {
            Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    suspend fun enable(port: Int): Result<Unit> = runCatching {
        if (!hasPermission()) {
            error("WRITE_SECURE_SETTINGS not granted")
        }
        val current = Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
        if (!current.isNullOrBlank() && current != loopbackProxy(port)) {
            savedProxy = current
            preferences.saveHttpProxy(current)
        } else {
            savedProxy = preferences.savedHttpProxy.first()
        }
        Settings.Global.putString(
            context.contentResolver,
            Settings.Global.HTTP_PROXY,
            loopbackProxy(port),
        )
    }

    suspend fun disable(): Result<Unit> = runCatching {
        if (!hasPermission()) return@runCatching
        val restore = savedProxy ?: preferences.savedHttpProxy.first()
        if (restore.isNullOrBlank() || restore == ":0") {
            Settings.Global.putString(context.contentResolver, Settings.Global.HTTP_PROXY, ":0")
        } else {
            Settings.Global.putString(context.contentResolver, Settings.Global.HTTP_PROXY, restore)
        }
        savedProxy = null
        preferences.saveHttpProxy(null)
    }

    fun currentProxy(): String? {
        return try {
            Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
        } catch (_: SecurityException) {
            null
        }
    }

    companion object {
        fun loopbackProxy(port: Int): String = "127.0.0.1:$port"

        fun adbGrantCommand(packageName: String): String =
            "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
    }
}
