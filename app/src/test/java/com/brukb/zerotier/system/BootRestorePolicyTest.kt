package com.brukb.zerotier.system

import com.brukb.zerotier.data.model.GlobalMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootRestorePolicyTest {
    @Test
    fun restoreWhenBootOnAndModeNotOff() {
        assertTrue(BootRestorePolicy.shouldRestore(startOnBoot = true, globalMode = GlobalMode.PROXY))
        assertTrue(BootRestorePolicy.shouldRestore(startOnBoot = true, globalMode = GlobalMode.VPN))
        assertTrue(BootRestorePolicy.shouldRestore(startOnBoot = true, globalMode = GlobalMode.AUTO))
    }

    @Test
    fun noRestoreWhenOff() {
        assertFalse(BootRestorePolicy.shouldRestore(startOnBoot = true, globalMode = GlobalMode.OFF))
        assertFalse(
            BootRestorePolicy.shouldRestore(RestoreTrigger.PACKAGE_REPLACED, startOnBoot = true, GlobalMode.OFF),
        )
        assertFalse(
            BootRestorePolicy.shouldRestore(RestoreTrigger.FOREGROUND, startOnBoot = true, GlobalMode.OFF),
        )
    }

    @Test
    fun noRestoreWhenBootDisabled() {
        assertFalse(BootRestorePolicy.shouldRestore(startOnBoot = false, globalMode = GlobalMode.PROXY))
    }

    @Test
    fun packageReplacedRestoresWithoutStartOnBoot() {
        assertTrue(
            BootRestorePolicy.shouldRestore(
                RestoreTrigger.PACKAGE_REPLACED,
                startOnBoot = false,
                GlobalMode.PROXY,
            ),
        )
        assertTrue(
            BootRestorePolicy.shouldRestore(
                RestoreTrigger.PACKAGE_REPLACED,
                startOnBoot = false,
                GlobalMode.VPN,
            ),
        )
    }

    @Test
    fun foregroundRestoresWithoutStartOnBoot() {
        assertTrue(
            BootRestorePolicy.shouldRestore(RestoreTrigger.FOREGROUND, startOnBoot = false, GlobalMode.AUTO),
        )
    }
}
