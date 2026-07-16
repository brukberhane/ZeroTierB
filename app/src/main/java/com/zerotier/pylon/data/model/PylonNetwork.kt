package com.zerotier.pylon.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "networks")
data class PylonNetwork(
    @PrimaryKey val networkId: String,
    val name: String = "",
    val isEnabled: Boolean = true,
    val allowManaged: Boolean = true,
    val allowDefault: Boolean = false,
    val allowGlobal: Boolean = false,
    val allowDns: Boolean = true,
    val blockOutside: Boolean = false,
    val enabledInProxy: Boolean = true,
    val customDnsServers: String = "",
    val allowRules: String = "",
    val denyRules: String = "",
) {
    fun networkIdLong(): Long = parseNetworkIdLong(networkId)

    companion object {
        fun isValidNetworkId(id: String): Boolean = sanitizeHex(id) != null

        fun normalizeNetworkId(id: String): String {
            val hex = sanitizeHex(id)
                ?: throw IllegalArgumentException("Invalid network ID: $id")
            return hex.padStart(16, '0')
        }

        fun parseNetworkIdLong(hexId: String): Long =
            java.lang.Long.parseUnsignedLong(normalizeNetworkId(hexId), 16)

        private fun sanitizeHex(id: String): String? {
            val hex = id.trim().lowercase().removePrefix("0x")
            if (hex.isEmpty() || hex.length > 16) return null
            if (!hex.all { it in '0'..'9' || it in 'a'..'f' }) return null
            return hex
        }
    }
}
