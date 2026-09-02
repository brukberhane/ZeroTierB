package com.brukb.zerotier.log

import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Logcat plus a rotating file ring. INFO from lifecycle tags always hits the
 * file; other INFO only when [verbose]. WARN/ERROR always persist.
 */
object AppLog {
    @Volatile
    var verbose: Boolean = false

    @Volatile
    private var ring: LogRing? = null

    private val timeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    fun install(dir: File, maxBytes: Long = LogRing.DEFAULT_MAX_BYTES) {
        ring = LogRing(dir, maxBytes)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        if (verbose || tag in LIFECYCLE_TAGS) persist('I', tag, msg)
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        if (verbose) persist('D', tag, msg)
    }

    fun v(tag: String, msg: String) {
        Log.v(tag, msg)
        if (verbose) persist('V', tag, msg)
    }

    fun w(tag: String, msg: String, err: Throwable? = null) {
        if (err != null) Log.w(tag, msg, err) else Log.w(tag, msg)
        persist('W', tag, msg, err)
    }

    fun e(tag: String, msg: String, err: Throwable? = null) {
        if (err != null) Log.e(tag, msg, err) else Log.e(tag, msg)
        persist('E', tag, msg, err)
    }

    fun copyForShare(dest: File): File {
        dest.parentFile?.mkdirs()
        val r = ring
        if (r == null) {
            dest.writeText("AppLog not installed\n")
            return dest
        }
        r.concatTo(dest)
        if (dest.length() == 0L) {
            dest.writeText("No log lines yet\n")
        }
        return dest
    }

    private fun persist(level: Char, tag: String, msg: String, err: Throwable? = null) {
        val r = ring ?: return
        val ts = timeFmt.format(Instant.now())
        val line = buildString {
            append(ts).append(' ').append(level).append('/').append(tag).append(": ").append(msg)
            if (err != null) {
                append('\n').append(Log.getStackTraceString(err).trimEnd())
            }
        }
        runCatching { r.append(line) }
    }

    private val LIFECYCLE_TAGS = setOf(
        "ConnectionOrchestrator",
        "ProxyModeService",
        "ZerotierBVpnService",
        "ProxyWatchdog",
        "ZerotierBApplication",
        "ZeroTierNodeManager",
        "HttpProxyServer",
        "IdleGate",
        "ProxyHealthJob",
        "LinkObserver",
        "ShizukuPermission",
    )
}
