package com.brukb.zerotier.system

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object ShizukuPermissionHelper {
    private const val TAG = "ShizukuPermission"
    const val REQUEST_CODE = 4107

    enum class ShizukuState {
        READY, NO_PERMISSION, DEAD, NOT_INSTALLED
    }

    sealed interface PermissionRequest {
        object Requested : PermissionRequest
        object AlreadyGranted : PermissionRequest
        data class Error(val message: String) : PermissionRequest
    }

    fun isAvailable(): Boolean = pingBinder()

    private fun pingBinder(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) {
        false
    }

    fun checkState(): ShizukuState = try {
        when {
            !Shizuku.pingBinder() -> ShizukuState.DEAD
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuState.READY
            else -> ShizukuState.NO_PERMISSION
        }
    } catch (_: Exception) {
        ShizukuState.DEAD
    }

    fun hasApiPermission(): Boolean = try {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }

    fun requestPermission(): PermissionRequest = try {
        when {
            !pingBinder() ->
                PermissionRequest.Error("Shizuku is not running — start it, then try again")
            Shizuku.isPreV11() ->
                PermissionRequest.Error("Shizuku version too old (pre-v11)")
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                PermissionRequest.AlreadyGranted
            Shizuku.shouldShowRequestPermissionRationale() ->
                PermissionRequest.Error("Shizuku permission denied — grant it for this app in the Shizuku app")
            else -> {
                Shizuku.requestPermission(REQUEST_CODE)
                PermissionRequest.Requested
            }
        }
    } catch (e: Exception) {
        PermissionRequest.Error(e.message ?: "Shizuku permission request failed")
    }

    suspend fun grantWriteSecureSettings(context: Context): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(pingBinder()) { "Shizuku not running" }
                check(hasApiPermission()) { "Shizuku permission not granted to this app" }
                val packageName = context.packageName
                val process = invokeShizukuProcess(
                    arrayOf("pm", "grant", packageName, "android.permission.WRITE_SECURE_SETTINGS"),
                )
                val stderr = process.errorStream.bufferedReader().readText().trim()
                val exit = process.waitFor()
                check(exit == 0) { "pm grant failed (exit $exit): $stderr" }
                val granted = context.checkSelfPermission(
                    android.Manifest.permission.WRITE_SECURE_SETTINGS,
                ) == PackageManager.PERMISSION_GRANTED
                check(granted) { "pm grant succeeded but permission still missing" }
                Log.i(TAG, "WRITE_SECURE_SETTINGS granted via Shizuku")
                Unit
            }
        }

    fun startPrivilegedShell(script: String): Process {
        check(hasApiPermission()) { "Shizuku not running" }
        return invokeShizukuProcess(arrayOf("sh", "-c", script))
    }

    private fun invokeShizukuProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, cmd, null, null) as Process
    }
}
