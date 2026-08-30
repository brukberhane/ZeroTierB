package com.zerotier.pylon.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zerotier.pylon.PylonApplication
import com.zerotier.pylon.proxy.SystemProxyManager
import com.zerotier.pylon.service.PylonService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == ACTION_QUICKBOOT_POWERON
        if (!relevant) return

        val prefs = (context.applicationContext as PylonApplication).preferences
        val shouldStart = prefs.startOnBootBlocking() || prefs.serviceWantedBlocking()
        if (shouldStart) {
            PylonService.start(context)
        } else {
            SystemProxyManager(context, prefs).clearIfOurs()
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
