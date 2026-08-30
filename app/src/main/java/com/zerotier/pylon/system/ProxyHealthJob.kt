package com.zerotier.pylon.system

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.zerotier.pylon.PylonApplication
import com.zerotier.pylon.service.PylonService

/**
 * Inexact 15-minute JobScheduler check. Deferred into Doze maintenance windows.
 * Does not use exact/idle-while alarms, so it does not reset the Doze timer.
 */
class ProxyHealthJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as PylonApplication
        val wanted = app.preferences.serviceWantedBlocking() || app.preferences.startOnBootBlocking()
        if (!wanted) {
            ProxyHealthJob.cancel(this)
            jobFinished(params, false)
            return false
        }
        if (!PylonService.state.value.isRunning) {
            runCatching { PylonService.start(this) }
                .onFailure { Log.w(TAG, "job could not start service", it) }
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = false

    companion object {
        private const val TAG = "ProxyHealthJob"
    }
}

object ProxyHealthJob {
    const val JOB_ID = 7101
    private const val PERIOD_MS = 15 * 60 * 1000L
    private const val TAG = "ProxyHealthJob"

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val info = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, ProxyHealthJobService::class.java),
        )
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .build()
        val result = scheduler.schedule(info)
        Log.i(TAG, "schedule periodic=$PERIOD_MS result=$result")
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        Log.i(TAG, "cancelled")
    }
}
