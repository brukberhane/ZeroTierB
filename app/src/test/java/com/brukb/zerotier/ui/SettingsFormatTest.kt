package com.brukb.zerotier.ui

import com.brukb.zerotier.connection.Runtime
import com.brukb.zerotier.data.model.GlobalMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormatTest {
    @Test
    fun settingsGrantHintVisible_onlyWhenProxyWithoutPermission() {
        assertTrue(
            settingsGrantHintVisible(
                globalMode = GlobalMode.PROXY,
                runtime = null,
                hasSecureSettingsPermission = false,
            ),
        )
        assertTrue(
            settingsGrantHintVisible(
                globalMode = GlobalMode.AUTO,
                runtime = Runtime.PROXY,
                hasSecureSettingsPermission = false,
            ),
        )
        assertFalse(
            settingsGrantHintVisible(
                globalMode = GlobalMode.PROXY,
                runtime = Runtime.PROXY,
                hasSecureSettingsPermission = true,
            ),
        )
        assertFalse(
            settingsGrantHintVisible(
                globalMode = GlobalMode.VPN,
                runtime = Runtime.VPN,
                hasSecureSettingsPermission = false,
            ),
        )
        assertFalse(
            settingsGrantHintVisible(
                globalMode = GlobalMode.OFF,
                runtime = null,
                hasSecureSettingsPermission = false,
            ),
        )
    }
}
