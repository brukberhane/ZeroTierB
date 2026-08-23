package com.brukb.zerotier.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "link_profiles")
data class LinkProfile(
    @PrimaryKey val id: String,
    val kind: LinkKind,
    val mode: LinkMode,
    val ssid: String? = null,
    val subscriptionId: Int? = null,
    val simSlotIndex: Int? = null,
    val label: String = "",
    val iccId: String? = null,
) {
    companion object {
        const val OTHER_ID = "other"

        fun seedOther(): LinkProfile =
            LinkProfile(
                id = OTHER_ID,
                kind = LinkKind.OTHER,
                mode = LinkMode.PROXY,
                label = "Other",
            )

        fun mobileId(subscriptionId: Int): String = "mobile-$subscriptionId"

        fun wifiId(ssid: String): String = "wifi-$ssid"

        fun mergeWifi(existing: LinkProfile?, ssid: String, mode: LinkMode): LinkProfile {
            if (existing == null) {
                return LinkProfile(
                    id = wifiId(ssid),
                    kind = LinkKind.WIFI,
                    mode = mode,
                    ssid = ssid,
                )
            }
            return existing
        }

        fun mergeMobile(
            existing: LinkProfile?,
            subscriptionId: Int,
            simSlotIndex: Int?,
            label: String,
            iccId: String?,
        ): LinkProfile {
            if (existing == null) {
                return LinkProfile(
                    id = mobileId(subscriptionId),
                    kind = LinkKind.MOBILE,
                    mode = LinkMode.PROXY,
                    subscriptionId = subscriptionId,
                    simSlotIndex = simSlotIndex,
                    label = label,
                    iccId = iccId,
                )
            }
            return existing.copy(
                subscriptionId = subscriptionId,
                simSlotIndex = simSlotIndex ?: existing.simSlotIndex,
                label = label.ifBlank { existing.label },
                iccId = iccId ?: existing.iccId,
            )
        }
    }
}
