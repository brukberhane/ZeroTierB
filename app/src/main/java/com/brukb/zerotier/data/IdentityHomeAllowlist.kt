package com.brukb.zerotier.data

object IdentityHomeAllowlist {
    private val MOON_FILE = Regex("^moons\\.d/[0-9a-f]{16}\\.moon$")

    fun isAllowedRelative(path: String): Boolean {
        val normalized = normalize(path) ?: return false
        return when {
            normalized == "planet" -> true
            normalized == "roots" -> true
            MOON_FILE.matches(normalized) -> true
            else -> false
        }
    }

    private fun normalize(path: String): String? {
        var p = path.replace('\\', '/').trim()
        while (p.startsWith("./")) {
            p = p.removePrefix("./")
        }
        if (p.isEmpty() || p.startsWith('/')) return null
        val segments = p.split('/')
        if (segments.any { it == ".." || it.isEmpty() }) return null
        return segments.joinToString("/")
    }
}
