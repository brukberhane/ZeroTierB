package com.brukb.zerotier.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.brukb.zerotier.log.AppLog

class IdleGate(
    private val context: Context,
    private val onChanged: (allowPeriodicWork: Boolean, deviceIdle: Boolean) -> Unit,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var registered = false

    val allowPeriodicWork: Boolean
        get() = powerManager.isInteractive && !powerManager.isDeviceIdleMode

    val isDeviceIdle: Boolean
        get() = powerManager.isDeviceIdleMode

    val isInteractive: Boolean
        get() = powerManager.isInteractive

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val allow = allowPeriodicWork
            AppLog.i(TAG, "idle gate ${intent?.action} interactive=$isInteractive idle=$isDeviceIdle allow=$allow")
            onChanged(allow, isDeviceIdle)
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    companion object {
        private const val TAG = "IdleGate"
    }
}
