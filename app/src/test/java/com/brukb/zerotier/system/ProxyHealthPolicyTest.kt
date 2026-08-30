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

    @Test
    fun armFromJobWhenAlreadyAllowed() {
        assertTrue(
            ProxyHealthPolicy.shouldArmFromJob(
                startAllowed = true,
                startOnBoot = false,
                globalMode = GlobalMode.PROXY,
            ),
        )
    }

    @Test
    fun armFromJobAfterBootWhenStartOnBoot() {
        assertTrue(
            ProxyHealthPolicy.shouldArmFromJob(
                startAllowed = false,
                startOnBoot = true,
                globalMode = GlobalMode.VPN,
            ),
        )
    }

    @Test
    fun noArmFromJobAfterBootWhenStartOnBootOff() {
        assertFalse(
            ProxyHealthPolicy.shouldArmFromJob(
                startAllowed = false,
                startOnBoot = false,
                globalMode = GlobalMode.PROXY,
            ),
        )
    }

    @Test
    fun noArmFromJobWhenModeOff() {
        assertFalse(
            ProxyHealthPolicy.shouldArmFromJob(
                startAllowed = true,
                startOnBoot = true,
                globalMode = GlobalMode.OFF,
            ),
        )
    }

    @Test
    fun callSchedulerOnlyWhenNotArmedAndNotPending() {
        assertTrue(ProxyHealthPolicy.shouldCallScheduler(armedThisProcess = false, pending = false))
        assertFalse(ProxyHealthPolicy.shouldCallScheduler(armedThisProcess = true, pending = false))
        assertFalse(ProxyHealthPolicy.shouldCallScheduler(armedThisProcess = false, pending = true))
        assertFalse(ProxyHealthPolicy.shouldCallScheduler(armedThisProcess = true, pending = true))
    }
}
