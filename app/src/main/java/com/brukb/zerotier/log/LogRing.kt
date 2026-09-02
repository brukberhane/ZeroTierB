package com.brukb.zerotier.log

import java.io.File
import java.nio.charset.StandardCharsets

/** Two-file rotate: current + one previous. Oldest lines live in [rotatedName]. */
class LogRing(
    private val dir: File,
    private val maxBytes: Long,
    private val currentName: String = CURRENT_NAME,
    private val rotatedName: String = ROTATED_NAME,
) {
    private val lock = Any()

    fun append(line: String) {
        val payload = if (line.endsWith('\n')) line else line + '\n'
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        synchronized(lock) {
            dir.mkdirs()
            val current = File(dir, currentName)
            if (current.exists() && current.length() + bytes.size > maxBytes) {
                rotateLocked(current)
            }
            current.appendBytes(bytes)
        }
    }

    /** Oldest first: rotated, then current. */
    fun filesOldestFirst(): List<File> {
        synchronized(lock) {
            val out = mutableListOf<File>()
            val rotated = File(dir, rotatedName)
            if (rotated.exists() && rotated.length() > 0) out += rotated
            val current = File(dir, currentName)
            if (current.exists() && current.length() > 0) out += current
            return out
        }
    }

    fun concatTo(dest: File) {
        dest.parentFile?.mkdirs()
        dest.writeText("")
        for (src in filesOldestFirst()) {
            dest.appendBytes(src.readBytes())
        }
    }

    private fun rotateLocked(current: File) {
        val rotated = File(dir, rotatedName)
        if (rotated.exists()) rotated.delete()
        current.renameTo(rotated)
    }

    companion object {
        const val CURRENT_NAME = "zerotierb.log"
        const val ROTATED_NAME = "zerotierb.log.1"
        const val DEFAULT_MAX_BYTES = 1024L * 1024
    }
}
