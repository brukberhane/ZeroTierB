package com.brukb.zerotier.data.model

enum class GlobalMode {
    OFF,
    PROXY,
    VPN,
    AUTO,
    ;

    companion object {
        fun parse(raw: String?): GlobalMode {
            if (raw.isNullOrBlank()) return OFF
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: OFF
        }
    }
}

enum class LinkKind {
    WIFI,
    MOBILE,
    OTHER,
}

enum class LinkMode {
    OFF,
    PROXY,
    VPN,
}

enum class UplinkDnsPreference {
    WIFI_FIRST,
    CELLULAR_FIRST,
    ;

    companion object {
        fun parse(raw: String?): UplinkDnsPreference {
            if (raw.isNullOrBlank()) return WIFI_FIRST
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
                ?: WIFI_FIRST
        }
    }
}

object GlobalModeMigrate {
    fun initial(startOnBoot: Boolean, stored: String?): GlobalMode {
        if (!stored.isNullOrBlank()) return GlobalMode.parse(stored)
        return if (startOnBoot) GlobalMode.VPN else GlobalMode.OFF
    }
}
