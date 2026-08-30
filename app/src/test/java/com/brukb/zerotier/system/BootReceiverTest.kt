package com.brukb.zerotier.system

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BootReceiverTest {
    @Test
    fun triggerFor_bootAndQuickboot() {
        assertEquals(RestoreTrigger.BOOT, BootReceiver.triggerFor(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(
            RestoreTrigger.BOOT,
            BootReceiver.triggerFor("android.intent.action.QUICKBOOT_POWERON"),
        )
    }

    @Test
    fun triggerFor_packageReplaced() {
        assertEquals(
            RestoreTrigger.PACKAGE_REPLACED,
            BootReceiver.triggerFor(Intent.ACTION_MY_PACKAGE_REPLACED),
        )
    }

    @Test
    fun triggerFor_unknown_returnsNull() {
        assertNull(BootReceiver.triggerFor(Intent.ACTION_PACKAGE_REPLACED))
        assertNull(BootReceiver.triggerFor(null))
        assertNull(BootReceiver.triggerFor(""))
    }
}
