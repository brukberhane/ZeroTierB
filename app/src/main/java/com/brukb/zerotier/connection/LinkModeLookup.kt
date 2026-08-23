package com.brukb.zerotier.connection

import com.brukb.zerotier.data.model.LinkMode

interface LinkModeLookup {
    suspend fun modeForSsid(ssid: String): LinkMode?
    suspend fun modeForSubscription(subscriptionId: Int): LinkMode
    suspend fun modeForOther(): LinkMode
}
