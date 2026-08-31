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
    fun restartJobArmsAfterProcessDeathWithoutStartOnBoot() {
        assertTrue(
            ProxyHealthPolicy.shouldArmFromJob(
                startAllowed = false,
                startOnBoot = false,
                globalMode = GlobalMode.PROXY,
                jobId = ProxyHealthJob.RESTART_JOB_ID,
            ),
        )
        assertFalse(
            ProxyHealthPolicy.shouldArmFromJob(
                startAllowed = false,
                startOnBoot = false,
                globalMode = GlobalMode.PROXY,
                jobId = ProxyHealthJob.JOB_ID,
            ),
        )
        assertFalse(
            ProxyHealthPolicy.shouldArmFromJob(
                startAllowed = false,
                startOnBoot = false,
                globalMode = GlobalMode.OFF,
                jobId = ProxyHealthJob.RESTART_JOB_ID,
            ),
        )
    }

    @Test
    fun restartJobIdDistinctFromPeriodic() {
        assertTrue(ProxyHealthJob.JOB_ID != ProxyHealthJob.RESTART_JOB_ID)
        assertTrue(ProxyHealthJob.FGS_TIMEOUT_RESTART_DELAY_MS > 0L)
        assertTrue(ProxyHealthJob.PACKAGE_REPLACED_RESTART_DELAY_MS > 0L)
    }

    @Test
    fun callSchedulerOnlyWhenNotArmedAndNotPending() {
        assertTrue(ProxyHealthPolicy.shouldCallScheduler(armedThisProcess = false, pending = false))
        assertFalse(ProxyHealthPolicy.shouldCallScheduler(armedThisProcess = true, pending = false))
        assertFalse(ProxyHealthPolicy.shouldCallScheduler(armedThisProcess = false, pending = true))
        assertFalse(ProxyHealthPolicy.shouldCallScheduler(armedThisProcess = true, pending = true))
    }
}
