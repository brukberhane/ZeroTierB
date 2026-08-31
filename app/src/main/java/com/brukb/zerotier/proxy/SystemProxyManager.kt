package com.brukb.zerotier.proxy

import android.content.Context
import android.provider.Settings
import com.brukb.zerotier.data.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

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
        writeMarker(port)
        Settings.Global.putString(
            context.contentResolver,
            Settings.Global.HTTP_PROXY,
            loopbackProxy(port),
        )
    }

    suspend fun disable(): Result<Unit> = runCatching {
        disableBlocking().getOrThrow()
    }

    fun disableBlocking(): Result<Unit> = runCatching {
        if (!hasPermission()) return@runCatching
        val restore = savedProxy ?: runBlocking { preferences.savedHttpProxy.first() }
        Settings.Global.putString(
            context.contentResolver,
            Settings.Global.HTTP_PROXY,
            decideRestoreOnDisable(restore),
        )
        savedProxy = null
        deleteMarker()
        runBlocking { preferences.setSavedHttpProxy(null) }
    }

    fun clearIfOurs(): Result<Unit> = runCatching {
        if (!hasPermission()) return@runCatching
        val current = currentProxy()
        val markerPort = markerFile(context).takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (!shouldClearIfOurs(current, markerPort)) return@runCatching
        disableBlocking().getOrThrow()
    }

    fun currentProxy(): String? {
        return try {
            Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun writeMarker(port: Int) {
        val file = markerFile(context)
        file.writeText(port.toString())
        file.setReadable(true, false)
        file.setWritable(true, true)
    }

    private fun deleteMarker() {
        markerFile(context).delete()
    }

    companion object {
        const val MARKER_FILENAME = "zerotierb-http-proxy-port"

        fun loopbackProxy(port: Int): String = "127.0.0.1:$port"

        fun adbGrantCommand(packageName: String): String =
            "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

        fun markerFile(context: Context): File = File(context.filesDir, MARKER_FILENAME)

        fun isOurs(value: String?, port: Int): Boolean {
            if (value.isNullOrBlank() || port <= 0) return false
            val trimmed = value.trim()
            return trimmed == loopbackProxy(port) ||
                trimmed == "[::1]:$port" ||
                trimmed == "localhost:$port"
        }

        private val LOOPBACK_PROXY = Regex("^127\\.0\\.0\\.1:\\d+$")

        fun isLoopbackProxy(value: String?): Boolean {
            if (value.isNullOrBlank()) return false
            val trimmed = value.trim()
            if (trimmed == ":0") return false
            return LOOPBACK_PROXY.matches(trimmed) ||
                trimmed.startsWith("[::1]:") ||
                trimmed.startsWith("localhost:")
        }

        fun decideValueToSaveOnEnable(current: String?, port: Int): String? =
            if (!current.isNullOrBlank() && current != ":0" && !isLoopbackProxy(current)) {
                current
            } else {
                null
            }

        fun decideRestoreOnDisable(saved: String?): String =
            if (saved.isNullOrBlank() || saved == ":0" || isLoopbackProxy(saved)) ":0" else saved

        fun isOurLoopback(current: String?, lastPort: Int): Boolean =
            isOurs(current, lastPort)

        fun parseLoopbackPort(value: String?): Int? {
            if (value.isNullOrBlank() || !isLoopbackProxy(value)) return null
            val trimmed = value.trim()
            val portStr = when {
                trimmed.startsWith("127.0.0.1:") -> trimmed.removePrefix("127.0.0.1:")
                trimmed.startsWith("[::1]:") -> trimmed.removePrefix("[::1]:")
                trimmed.startsWith("localhost:") -> trimmed.removePrefix("localhost:")
                else -> return null
            }
            return portStr.toIntOrNull()?.takeIf { it in 1..65535 }
        }

        fun probeListen(port: Int, timeoutMs: Int = 400): Boolean {
            if (port !in 1..65535) return false
            return runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
                    true
                }
            }.getOrDefault(false)
        }

        /**
         * [listenAlive] false = our loopback Global points at a dead port
         * (native crash skipped onDestroy). Clear even if mode is still PROXY.
         * Null = no probe; keep the old "only clear when not PROXY" gate.
         */
        fun shouldClearStale(
            current: String?,
            saved: String?,
            lastPort: Int,
            proxyModeActive: Boolean,
            listenAlive: Boolean? = null,
        ): Boolean {
            if (isLoopbackProxy(current) && listenAlive == false) return true
            if (proxyModeActive) return false
            return isLoopbackProxy(current) || isOurLoopback(current, lastPort) || !saved.isNullOrBlank()
        }

        fun shouldClearIfOurs(current: String?, markerPort: Int?): Boolean =
            isLoopbackProxy(current) || (markerPort != null && isOurs(current, markerPort))
    }
}
