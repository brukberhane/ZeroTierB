package com.brukb.zerotier.system

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.brukb.zerotier.proxy.SystemProxyManager
import java.io.File

/**
 * Privileged shell loop (Shizuku only — no su). If Global HTTP_PROXY still
 * points at loopback but nothing is listening, clear it so browsers do not hang.
 */
object ProxyWatchdog {
    private const val TAG = "ProxyWatchdog"
    private const val PID_FILENAME = "zerotierb-watchdog.pid"
    private const val INTERVAL_SECONDS = 60

    @Volatile
    private var process: Process? = null

    fun isRunning(): Boolean {
        val running = process
        if (running != null) {
            return try {
                running.exitValue()
                false
            } catch (_: IllegalThreadStateException) {
                true
            }
        }
        return false
    }

    fun startIfNeeded(context: Context) {
        val power = context.getSystemService(PowerManager::class.java)
        if (!power.isInteractive || power.isDeviceIdleMode) {
            Log.i(TAG, "skip start: not interactive")
            return
        }
        if (!ShizukuPermissionHelper.hasApiPermission()) {
            Log.w(TAG, "skip start: Shizuku not ready")
            return
        }
        if (isRunning()) return
        stop(context)
        val marker = SystemProxyManager.markerFile(context).absolutePath
        val pidFile = pidFile(context).absolutePath
        val script = buildScript(marker, pidFile)
        process = ShizukuPermissionHelper.startPrivilegedShell(script)
        Log.i(TAG, "watchdog started")
    }

    fun stop(context: Context) {
        val running = process
        process = null
        runCatching { running?.destroy() }
        val pid = pidFile(context).takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()
        if (pid != null && pid > 0) {
            runCatching {
                if (ShizukuPermissionHelper.hasApiPermission()) {
                    ShizukuPermissionHelper.startPrivilegedShell("kill $pid >/dev/null 2>&1").waitFor()
                }
            }
            pidFile(context).delete()
        }
        Log.i(TAG, "watchdog stopped")
    }

    private fun pidFile(context: Context): File = File(context.filesDir, PID_FILENAME)

    private fun buildScript(markerPath: String, pidPath: String): String {
        return """
            echo ${'$'}${'$'} > '$pidPath' 2>/dev/null || true
            while true; do
              PROXY=${'$'}(settings get global http_proxy 2>/dev/null)
              case "${'$'}PROXY" in
                127.0.0.1:*|localhost:*|'[::1]:'*)
                  PORT=${'$'}{PROXY##*:}
                  MARKER_PORT=""
                  if [ -f '$markerPath' ]; then
                    MARKER_PORT=${'$'}(cat '$markerPath' 2>/dev/null)
                    if [ -n "${'$'}MARKER_PORT" ] && [ "${'$'}PORT" != "${'$'}MARKER_PORT" ]; then
                      sleep $INTERVAL_SECONDS
                      continue
                    fi
                  fi
                  UP=0
                  if command -v toybox >/dev/null 2>&1; then
                    toybox nc -z 127.0.0.1 "${'$'}PORT" >/dev/null 2>&1 && UP=1
                  fi
                  if [ "${'$'}UP" = 0 ]; then
                    (echo >/dev/tcp/127.0.0.1/"${'$'}PORT") >/dev/null 2>&1 && UP=1
                  fi
                  if [ "${'$'}UP" = 0 ]; then
                    settings put global http_proxy :0
                  fi
                  ;;
              esac
              sleep $INTERVAL_SECONDS
            done
        """.trimIndent()
    }
}
