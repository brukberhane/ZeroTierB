package com.brukb.zerotier.system

import android.content.Context
import android.content.Intent
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.system.BootRestorePolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as ZerotierBApplication
        app.applicationScope.launch {
            try {
                val startOnBoot = app.preferences.startOnBoot.first()
                val globalMode = app.preferences.globalMode.first()
                if (BootRestorePolicy.shouldRestore(startOnBoot, globalMode)) {
                    app.orchestrator.refresh()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
