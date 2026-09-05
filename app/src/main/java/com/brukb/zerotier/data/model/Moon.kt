package com.brukb.zerotier.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "moons")
data class Moon(
    @PrimaryKey val worldId: String,
    val seed: String? = null,
    val label: String = "",
    val createdAt: Long = 0L,
    val hasMoonFile: Boolean = false,
) {
    companion object {
        const val MAX_MOONS = 16

        fun isValidWorldId(raw: String): Boolean = normalizeWorldIdOrNull(raw) != null

        fun normalizeWorldId(raw: String): String =
            normalizeWorldIdOrNull(raw)
                ?: throw IllegalArgumentException("Invalid world ID: $raw")

        fun normalizeWorldIdOrNull(raw: String): String? {
            val hex = sanitizeHex(raw, maxLen = 16) ?: return null
            return hex.padStart(16, '0')
        }

        fun normalizeSeed(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val hex = sanitizeHex(raw, maxLen = 10) ?: return null
            return hex.padStart(10, '0')
        }

        private fun sanitizeHex(raw: String, maxLen: Int): String? {
            val hex = raw.trim().lowercase().removePrefix("0x")
            if (hex.isEmpty() || hex.length > maxLen) return null
            if (!hex.all { it in '0'..'9' || it in 'a'..'f' }) return null
            return hex
        }
    }
}
