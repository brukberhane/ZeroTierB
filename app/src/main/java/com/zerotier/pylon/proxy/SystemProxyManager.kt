package com.zerotier.pylon.proxy

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.zerotier.pylon.data.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File

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
        if (!current.isNullOrBlank() && current != loopbackProxy(port) && !isOurs(current, port)) {
            savedProxy = current
            preferences.saveHttpProxy(current)
        } else {
            savedProxy = preferences.savedHttpProxy.first()
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
        preferences.saveHttpProxy(null)
    }

    fun disableBlocking(): Result<Unit> = runCatching {
        if (!hasPermission()) return@runCatching
        val restore = savedProxy ?: preferences.savedHttpProxyBlocking()
        restoreProxy(restore)
        savedProxy = null
        deleteMarker()
        runBlocking { preferences.saveHttpProxy(null) }
    }

    fun clearIfOurs(port: Int = preferences.httpProxyPortBlocking()): Result<Unit> = runCatching {
        if (!hasPermission()) return@runCatching
        val current = currentProxy()
        val markerPort = readMarkerPort()
        val ours = isOurs(current, port) || (markerPort != null && isOurs(current, markerPort))
        if (!ours) return@runCatching
        val restore = savedProxy ?: preferences.savedHttpProxyBlocking()
        restoreProxy(restore)
        savedProxy = null
        deleteMarker()
        runBlocking { preferences.saveHttpProxy(null) }
        Log.i(TAG, "cleared stale system proxy $current")
    }

    fun currentProxy(): String? {
        return try {
            Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun restoreProxy(restore: String?) {
        if (restore.isNullOrBlank() || restore == ":0") {
            Settings.Global.putString(context.contentResolver, Settings.Global.HTTP_PROXY, ":0")
        } else {
            Settings.Global.putString(context.contentResolver, Settings.Global.HTTP_PROXY, restore)
        }
    }

    private fun markerFile(): File = File(context.filesDir, MARKER_FILENAME)

    private fun writeMarker(port: Int) {
        val file = markerFile()
        file.writeText(port.toString())
        file.setReadable(true, false)
        file.setWritable(true, true)
    }

    private fun deleteMarker() {
        markerFile().delete()
    }

    private fun readMarkerPort(): Int? = markerFile().takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()

    companion object {
        private const val TAG = "SystemProxyManager"
        const val MARKER_FILENAME = "pylon-http-proxy-port"

        fun loopbackProxy(port: Int): String = "127.0.0.1:$port"

        fun isOurs(value: String?, port: Int): Boolean {
            if (value.isNullOrBlank()) return false
            val trimmed = value.trim()
            return trimmed == loopbackProxy(port) ||
                trimmed == "[::1]:$port" ||
                trimmed == "localhost:$port"
        }

        fun isLoopbackProxy(value: String?): Boolean {
            if (value.isNullOrBlank()) return false
            val trimmed = value.trim()
            if (trimmed == ":0") return false
            return trimmed.startsWith("127.0.0.1:") ||
                trimmed.startsWith("[::1]:") ||
                trimmed.startsWith("localhost:")
        }

        fun parseProxyPort(value: String?): Int? {
            if (value.isNullOrBlank()) return null
            val colon = value.lastIndexOf(':')
            if (colon < 0) return null
            return value.substring(colon + 1).toIntOrNull()
        }

        fun adbGrantCommand(packageName: String): String =
            "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"

        fun markerFile(context: Context): File = File(context.filesDir, MARKER_FILENAME)
    }
}
