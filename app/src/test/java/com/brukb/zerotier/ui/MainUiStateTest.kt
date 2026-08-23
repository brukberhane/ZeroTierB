package com.brukb.zerotier.ui

import com.brukb.zerotier.connection.PhysicalLink
import com.brukb.zerotier.connection.Runtime
import com.brukb.zerotier.connection.RuntimePlan
import com.brukb.zerotier.data.model.LinkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {
    @Test
    fun vpnConsentMissing_derivedFromPlan() {
        val plan = RuntimePlan(Runtime.PROXY, "AUTO consent missing", null, listOf("aabb"), true)
        val fromCombine = plan.vpnConsentMissing
        assertTrue(fromCombine)
        assertTrue(MainUiState(plan = plan, vpnConsentMissing = fromCombine).vpnConsentMissing)

        val cleared = plan.copy(runtime = Runtime.VPN, vpnNetworkId = "aabb", vpnConsentMissing = false)
        assertFalse(cleared.vpnConsentMissing)
    }

    @Test
    fun canSaveSsid_onlyWhenUnsavedReadable() {
        assertTrue(canSaveSsid(PhysicalLink.WifiUnsaved("Cafe")))
        assertFalse(canSaveSsid(PhysicalLink.WifiKnown("Cafe", LinkMode.PROXY)))
        assertFalse(canSaveSsid(PhysicalLink.WifiUnknown))
        assertFalse(canSaveSsid(PhysicalLink.None))
        assertEquals("Cafe", unsavedWifiSsid(PhysicalLink.WifiUnsaved("Cafe")))
    }
}
