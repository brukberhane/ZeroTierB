package com.brukb.zerotier.connection

import android.os.Build
import android.telephony.SubscriptionManager

object DataSubscriptionIds {
    fun activeDataSubscriptionId(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            SubscriptionManager.getActiveDataSubscriptionId()
        } else {
            SubscriptionManager.getDefaultDataSubscriptionId()
        }

    fun activeOrNull(): Int? {
        val id = activeDataSubscriptionId()
        return if (id == SubscriptionManager.INVALID_SUBSCRIPTION_ID) null else id
    }
}
