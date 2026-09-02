package com.brukb.zerotier.system

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.brukb.zerotier.log.AppLog
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.proxy.SystemProxyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Inexact 15-minute JobScheduler check. Deferred into Doze maintenance windows.
 * Does not use exact/idle-while alarms, so it does not reset the Doze timer.
 */
class ProxyHealthJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as ZerotierBApplication
        app.applicationScope.launch {
            try {
                val mode = app.preferences.globalMode.first()
                val startOnBoot = app.preferences.startOnBoot.first()
                if (!ProxyHealthPolicy.shouldArmFromJob(
                        startAllowed = app.orchestrator.startAllowed,
                        startOnBoot = startOnBoot,
                        globalMode = mode,
                        jobId = params?.jobId ?: ProxyHealthJob.JOB_ID,
                    )
                ) {
                    if (!app.orchestrator.startAllowed) {
                        SystemProxyManager(app, app.preferences).clearIfOurs()
                    }
                    ProxyHealthJob.cancel(this@ProxyHealthJobService)
                    return@launch
                }
                app.orchestrator.refresh(syncJob = false)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = false
}

object ProxyHealthPolicy {
    fun shouldSchedule(globalMode: GlobalMode): Boolean = globalMode != GlobalMode.OFF

    /**
     * Persisted jobs survive reboot. A fresh process has [startAllowed] false.
     * Only arm from the job when start-on-boot would also restore; otherwise
     * the 15-minute timer would bypass the boot toggle.
     */
    fun shouldArmFromJob(
        startAllowed: Boolean,
        startOnBoot: Boolean,
        globalMode: GlobalMode,
        jobId: Int = ProxyHealthJob.JOB_ID,
    ): Boolean {
        if (!shouldSchedule(globalMode)) return false
        // One-shot after FGS timeout / LMK: mode is still PROXY/AUTO/VPN.
        // Process death resets startAllowed; do not wait for start-on-boot.
        if (jobId == ProxyHealthJob.RESTART_JOB_ID) return true
        if (startAllowed) return true
        return BootRestorePolicy.shouldRestore(RestoreTrigger.BOOT, startOnBoot, globalMode)
    }

    /** JobScheduler.schedule() is quota-limited (250/60s). Never replace a live job. */
    fun shouldCallScheduler(armedThisProcess: Boolean, pending: Boolean): Boolean =
        !armedThisProcess && !pending
}

object ProxyHealthJob {
    const val JOB_ID = 7101
    const val RESTART_JOB_ID = 7102
    const val FGS_TIMEOUT_RESTART_DELAY_MS = 30_000L
    const val PACKAGE_REPLACED_RESTART_DELAY_MS = 5_000L
    private const val PERIOD_MS = 15 * 60 * 1000L
    private const val TAG = "ProxyHealthJob"

    @Volatile
    private var armedThisProcess = false

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (!ProxyHealthPolicy.shouldCallScheduler(
                armedThisProcess,
                pending = scheduler.getPendingJob(JOB_ID) != null,
            )
        ) {
            armedThisProcess = true
            return
        }
        val info = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, ProxyHealthJobService::class.java),
        )
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .build()
        val result = scheduler.schedule(info)
        armedThisProcess = result == JobScheduler.RESULT_SUCCESS
        AppLog.i(TAG, "schedule periodic=$PERIOD_MS result=$result")
    }

    /**
     * One-shot after FGS onTimeout. Must not start FGS inside onTimeout
     * (type quota may still be exhausted). Delay lets stopSelf finish, then
     * [ProxyHealthJobService] calls refresh.
     */
    fun scheduleRestart(
        context: Context,
        delayMs: Long = FGS_TIMEOUT_RESTART_DELAY_MS,
    ) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val info = JobInfo.Builder(
            RESTART_JOB_ID,
            ComponentName(context, ProxyHealthJobService::class.java),
        )
            .setMinimumLatency(delayMs)
            .setOverrideDeadline(delayMs + 90_000L)
            .setPersisted(true)
            .build()
        val result = scheduler.schedule(info)
        AppLog.i(TAG, "schedule restart delay=$delayMs result=$result")
    }

    fun cancel(context: Context) {
        armedThisProcess = false
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancel(JOB_ID)
        scheduler.cancel(RESTART_JOB_ID)
        AppLog.i(TAG, "cancelled")
    }
}
