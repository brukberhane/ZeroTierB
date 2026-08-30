package com.zerotier.pylon.system

import android.content.Context
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.File

object ShizukuPermissionHelper {
    private const val TAG = "ShizukuPermission"

    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun suAvailable(): Boolean {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su")
        return paths.any { File(it).canExecute() }
    }

    fun canRunPrivileged(): Boolean = isAvailable() || suAvailable()

    fun grantWriteSecureSettings(context: Context): Result<Unit> = runCatching {
        check(isAvailable()) { "Shizuku not running" }
        val packageName = context.packageName
        val process = invokeShizukuProcess(
            arrayOf("pm", "grant", packageName, "android.permission.WRITE_SECURE_SETTINGS"),
        )
        val exit = process.waitFor()
        check(exit == 0) { "pm grant exited with code $exit" }
        Log.i(TAG, "WRITE_SECURE_SETTINGS granted via Shizuku")
    }

    fun startPrivilegedShell(script: String): Process {
        if (isAvailable()) {
            return invokeShizukuProcess(arrayOf("sh", "-c", script))
        }
        check(suAvailable()) { "Shizuku not running and su not found" }
        return Runtime.getRuntime().exec(arrayOf("su", "-c", script))
    }

    fun invokeShizukuProcess(cmd: Array<String>): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, cmd, null, null) as Process
    }
}
