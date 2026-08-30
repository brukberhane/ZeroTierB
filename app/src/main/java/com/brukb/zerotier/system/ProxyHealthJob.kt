package com.brukb.zerotier.system

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
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
    ): Boolean {
        if (!shouldSchedule(globalMode)) return false
        if (startAllowed) return true
        return BootRestorePolicy.shouldRestore(RestoreTrigger.BOOT, startOnBoot, globalMode)
    }

    /** JobScheduler.schedule() is quota-limited (250/60s). Never replace a live job. */
    fun shouldCallScheduler(armedThisProcess: Boolean, pending: Boolean): Boolean =
        !armedThisProcess && !pending
}

object ProxyHealthJob {
    const val JOB_ID = 7101
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
        Log.i(TAG, "schedule periodic=$PERIOD_MS result=$result")
    }

    fun cancel(context: Context) {
        armedThisProcess = false
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        Log.i(TAG, "cancelled")
    }
}
