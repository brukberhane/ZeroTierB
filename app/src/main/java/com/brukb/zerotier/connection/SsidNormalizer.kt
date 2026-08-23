package com.brukb.zerotier.connection

object SsidNormalizer {
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        var s = raw.trim()
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            s = s.substring(1, s.length - 1).trim()
        }
        if (s.isBlank()) return null
        if (s.equals("<unknown ssid>", ignoreCase = true)) return null
        if (s.startsWith("0x", ignoreCase = true)) return null
        return s
    }
}
