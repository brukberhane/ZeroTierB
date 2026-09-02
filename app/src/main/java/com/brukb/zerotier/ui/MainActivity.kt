package com.brukb.zerotier.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brukb.zerotier.R
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.log.AppLog
import com.brukb.zerotier.proxy.ProxyModeService
import com.brukb.zerotier.system.BatteryOptimizationHelper
import com.brukb.zerotier.system.BootRestorePolicy
import com.brukb.zerotier.system.RestoreTrigger
import com.brukb.zerotier.system.ShizukuPermissionHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        lifecycleScope.launch {
            (application as ZerotierBApplication).orchestrator.refresh()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* FGS still runs; grant only unhides the notification. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            MainScreen(viewModel = viewModel())
        }
        handleDebugIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val app = application as ZerotierBApplication
        lifecycleScope.launch {
            val mode = app.preferences.globalMode.first()
            if (BootRestorePolicy.shouldRestore(RestoreTrigger.FOREGROUND, startOnBoot = false, mode)) {
                app.orchestrator.refresh()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent(intent)
    }

    private fun handleDebugIntent(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_DEBUG_ACTION) ?: return
        intent.removeExtra(EXTRA_DEBUG_ACTION)
        when (action) {
            ACTION_START_PROXY -> {
                Log.i(TAG, "adb debug: starting proxy")
                ProxyModeService.start(this)
            }
            ACTION_STOP_PROXY -> {
                Log.i(TAG, "adb debug: stopping proxy")
                ProxyModeService.stop(this)
            }
            ACTION_GRANT_SECURE -> {
                Log.i(TAG, "adb debug: Shizuku grant WRITE_SECURE_SETTINGS")
                lifecycleScope.launch {
                    val result = ShizukuPermissionHelper.grantWriteSecureSettings(this@MainActivity)
                    Log.i(
                        TAG,
                        if (result.isSuccess) "grant ok" else "grant failed: ${result.exceptionOrNull()?.message}",
                    )
                }
            }
            ACTION_APPLY_MODE -> {
                val raw = intent.getStringExtra(EXTRA_MODE)
                val mode = GlobalMode.parse(raw)
                Log.i(TAG, "adb debug: apply mode=$mode")
                if (mode == GlobalMode.VPN) {
                    requestVpnViaOrchestrator()
                } else {
                    lifecycleScope.launch {
                        (application as ZerotierBApplication).orchestrator.applyGlobalMode(mode)
                    }
                }
            }
            ACTION_STOP_ALL -> {
                Log.i(TAG, "adb debug: stop all")
                lifecycleScope.launch {
                    (application as ZerotierBApplication).orchestrator.stopAll()
                }
            }
        }
    }

    fun requestVpnConsent() {
        requestVpnConsentOnly()
    }

    fun requestVpnAndStart() {
        requestVpnViaOrchestrator()
    }

    fun refreshAfterPermission() {
        lifecycleScope.launch {
            (application as ZerotierBApplication).orchestrator.refresh()
        }
    }

    fun openBatteryOptimizationSettings() {
        runCatching {
            startActivity(BatteryOptimizationHelper.requestIgnoreIntent(this))
        }.onFailure { Log.w(TAG, "battery opt intent failed", it) }
    }

    fun openBatteryOptimizationSettingsPage() {
        runCatching {
            startActivity(BatteryOptimizationHelper.openSettingsIntent())
        }.onFailure { Log.w(TAG, "battery settings page failed", it) }
    }

    fun exportAppLogs() {
        val dest = File(cacheDir, "log-export/zerotierb-logs.txt")
        AppLog.copyForShare(dest)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", dest)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ZerotierB logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, getString(R.string.export_logs_chooser)))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestVpnConsentOnly() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            Log.i(TAG, "VPN consent required — showing system dialog")
            vpnConsentLauncher.launch(prepare)
        } else {
            lifecycleScope.launch {
                (application as ZerotierBApplication).orchestrator.refresh()
            }
        }
    }

    private fun requestVpnViaOrchestrator() {
        lifecycleScope.launch {
            val app = application as ZerotierBApplication
            app.preferences.setGlobalMode(GlobalMode.VPN)
            val prepare = VpnService.prepare(this@MainActivity)
            if (prepare != null) {
                Log.i(TAG, "VPN consent required — showing system dialog")
                vpnConsentLauncher.launch(prepare)
            } else {
                app.orchestrator.refresh()
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_DEBUG_ACTION = "zerotierb_action"
        const val ACTION_START_PROXY = "start_proxy"
        const val ACTION_STOP_PROXY = "stop_proxy"
        const val ACTION_GRANT_SECURE = "grant_secure_settings"
        const val ACTION_APPLY_MODE = "apply_mode"
        const val EXTRA_MODE = "mode"
        const val ACTION_STOP_ALL = "stop_all"
    }
}
