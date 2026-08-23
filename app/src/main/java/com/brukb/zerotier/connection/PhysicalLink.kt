package com.brukb.zerotier.connection

import com.brukb.zerotier.data.model.LinkMode

sealed interface PhysicalLink {
    data class WifiKnown(val ssid: String, val mode: LinkMode) : PhysicalLink
    data object WifiUnknown : PhysicalLink
    data class Mobile(val subscriptionId: Int, val mode: LinkMode) : PhysicalLink
    data class Other(val mode: LinkMode) : PhysicalLink
    data object None : PhysicalLink
}
