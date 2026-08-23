package com.brukb.zerotier.proxy

import android.content.Context
import android.provider.Settings
import com.brukb.zerotier.data.AppPreferences
import kotlinx.coroutines.flow.first

class SystemProxyManager(
    private val context: Context,
    private val preferences: AppPreferences,
) {
    private var savedProxy: String? = null

    fun hasPermission(): Boolean = try {
        val cr = context.contentResolver
        Settings.Global.putString(
            cr,
            Settings.Global.HTTP_PROXY,
            Settings.Global.getString(cr, Settings.Global.HTTP_PROXY) ?: "",
        )
        true
    } catch (_: SecurityException) {
        false
    }

    suspend fun enable(port: Int): Result<Unit> = runCatching {
        if (!hasPermission()) {
            error("WRITE_SECURE_SETTINGS not granted")
        }
        val current = Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
        val toSave = decideValueToSaveOnEnable(current, port)
        if (toSave != null) {
            savedProxy = toSave
            preferences.setSavedHttpProxy(toSave)
        } else {
            val existing = preferences.savedHttpProxy.first()
            savedProxy = existing?.takeUnless { isLoopbackProxy(it) }
            if (savedProxy == null) {
                preferences.setSavedHttpProxy(null)
            }
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
        Settings.Global.putString(
            context.contentResolver,
            Settings.Global.HTTP_PROXY,
            decideRestoreOnDisable(restore),
        )
        savedProxy = null
        preferences.setSavedHttpProxy(null)
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

        private val LOOPBACK_PROXY = Regex("^127\\.0\\.0\\.1:\\d+$")

        fun isLoopbackProxy(value: String?): Boolean =
            value != null && LOOPBACK_PROXY.matches(value)

        fun decideValueToSaveOnEnable(current: String?, port: Int): String? =
            if (!current.isNullOrBlank() && current != ":0" && !isLoopbackProxy(current)) {
                current
            } else {
                null
            }

        fun decideRestoreOnDisable(saved: String?): String =
            if (saved.isNullOrBlank() || saved == ":0" || isLoopbackProxy(saved)) ":0" else saved

        fun isOurLoopback(current: String?, lastPort: Int): Boolean =
            lastPort > 0 && current == loopbackProxy(lastPort)

        fun shouldClearStale(
            current: String?,
            saved: String?,
            lastPort: Int,
            proxyModeActive: Boolean,
        ): Boolean =
            !proxyModeActive && (isLoopbackProxy(current) || isOurLoopback(current, lastPort) || !saved.isNullOrBlank())
    }
}
