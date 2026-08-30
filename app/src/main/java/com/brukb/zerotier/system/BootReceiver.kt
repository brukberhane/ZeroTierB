package com.brukb.zerotier.system

import android.content.Context
import android.content.Intent
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.proxy.SystemProxyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val trigger = triggerFor(intent?.action) ?: return
        val pending = goAsync()
        val app = context.applicationContext as ZerotierBApplication
        app.applicationScope.launch {
            try {
                val startOnBoot = app.preferences.startOnBoot.first()
                val globalMode = app.preferences.globalMode.first()
                if (BootRestorePolicy.shouldRestore(trigger, startOnBoot, globalMode)) {
                    app.orchestrator.refresh()
                } else {
                    SystemProxyManager(context, app.preferences).clearIfOurs()
                    ProxyHealthJob.cancel(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

        internal fun triggerFor(action: String?): RestoreTrigger? = when (action) {
            Intent.ACTION_BOOT_COMPLETED, ACTION_QUICKBOOT_POWERON -> RestoreTrigger.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> RestoreTrigger.PACKAGE_REPLACED
            else -> null
        }
    }
}
