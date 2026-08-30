package com.brukb.zerotier.system

import com.brukb.zerotier.data.model.GlobalMode

enum class RestoreTrigger {
    /** Device boot / OEM quickboot. Honors start-on-boot. */
    BOOT,
    /** App update. Mode is still wanted; do not require start-on-boot. */
    PACKAGE_REPLACED,
    /** Activity visible. Recover a dead stack while the process is still up. */
    FOREGROUND,
}

object BootRestorePolicy {
    fun shouldRestore(
        trigger: RestoreTrigger,
        startOnBoot: Boolean,
        globalMode: GlobalMode,
    ): Boolean {
        if (globalMode == GlobalMode.OFF) return false
        return when (trigger) {
            RestoreTrigger.BOOT -> startOnBoot
            RestoreTrigger.PACKAGE_REPLACED, RestoreTrigger.FOREGROUND -> true
        }
    }

    /** Boot-only helper kept for callers that already know they are on the boot path. */
    fun shouldRestore(startOnBoot: Boolean, globalMode: GlobalMode): Boolean =
        shouldRestore(RestoreTrigger.BOOT, startOnBoot, globalMode)
}
