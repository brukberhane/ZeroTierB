package com.brukb.zerotier.ui

import com.brukb.zerotier.connection.Runtime
import com.brukb.zerotier.data.model.GlobalMode

fun settingsGrantHintVisible(
    globalMode: GlobalMode,
    runtime: Runtime?,
    hasSecureSettingsPermission: Boolean,
): Boolean {
    val proxyActive = globalMode == GlobalMode.PROXY || runtime == Runtime.PROXY
    return proxyActive && !hasSecureSettingsPermission
}
