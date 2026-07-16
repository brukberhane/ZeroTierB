package com.zerotier.pylon.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zerotier.pylon.PylonApplication
import com.zerotier.pylon.service.PylonService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = (context.applicationContext as PylonApplication).preferences
        val startOnBoot = runBlocking { prefs.startOnBoot.first() }
        if (startOnBoot) {
            PylonService.start(context)
        }
    }
}
