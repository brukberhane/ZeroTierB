package com.brukb.zerotier.system

import com.brukb.zerotier.data.model.GlobalMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyHealthPolicyTest {
    @Test
    fun scheduleWhenModeNotOff() {
        assertTrue(ProxyHealthPolicy.shouldSchedule(GlobalMode.PROXY))
        assertTrue(ProxyHealthPolicy.shouldSchedule(GlobalMode.VPN))
        assertTrue(ProxyHealthPolicy.shouldSchedule(GlobalMode.AUTO))
    }

    @Test
    fun noScheduleWhenOff() {
        assertFalse(ProxyHealthPolicy.shouldSchedule(GlobalMode.OFF))
    }
}
