package com.brukb.zerotier.system

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.vpn.ZerotierBVpnService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as ZerotierBApplication
        val startOnBoot = runBlocking { app.preferences.startOnBoot.first() }
        if (!startOnBoot) return
        if (VpnService.prepare(context) != null) return
        ZerotierBVpnService.start(context)
    }
}
