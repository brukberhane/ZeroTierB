package com.brukb.zerotier.system

import com.brukb.zerotier.data.model.GlobalMode

object BootRestorePolicy {
    /** Restore saved global mode on boot when start-on-boot is enabled and mode is not OFF. */
    fun shouldRestore(startOnBoot: Boolean, globalMode: GlobalMode): Boolean =
        startOnBoot && globalMode != GlobalMode.OFF
}
