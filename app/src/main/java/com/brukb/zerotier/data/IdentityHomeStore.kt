package com.brukb.zerotier.data

import java.io.File

class IdentityHomeStore(private val home: File) {
    fun write(relative: String, bytes: ByteArray) {
        requireAllowed(relative)
        val file = resolve(relative)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    fun delete(relative: String) {
        requireAllowed(relative)
        val file = resolve(relative)
        if (file.exists()) file.delete()
    }

    fun read(relative: String): ByteArray? {
        requireAllowed(relative)
        val file = resolve(relative)
        return if (file.exists()) file.readBytes() else null
    }

    fun exists(relative: String): Boolean {
        requireAllowed(relative)
        return resolve(relative).exists()
    }

    fun listMoonWorldIds(): Set<String> {
        val moonsDir = File(home, "moons.d")
        if (!moonsDir.isDirectory) return emptySet()
        return moonsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".moon") }
            ?.mapNotNull { file ->
                val relative = "moons.d/${file.name}"
                if (!IdentityHomeAllowlist.isAllowedRelative(relative)) return@mapNotNull null
                file.name.removeSuffix(".moon")
            }
            ?.toSet()
            ?: emptySet()
    }

    private fun requireAllowed(relative: String) {
        if (!IdentityHomeAllowlist.isAllowedRelative(relative)) {
            throw IllegalArgumentException("path not allowed: $relative")
        }
    }

    private fun resolve(relative: String): File = File(home, relative)
}
