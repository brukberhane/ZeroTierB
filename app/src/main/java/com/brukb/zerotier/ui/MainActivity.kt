package com.brukb.zerotier.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.proxy.ProxyModeService
import com.brukb.zerotier.system.ShizukuPermissionHelper
import com.brukb.zerotier.vpn.ZerotierBVpnService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ZerotierBVpnService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainScreen(viewModel = viewModel())
        }
        handleDebugIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent(intent)
    }

    private fun handleDebugIntent(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_DEBUG_ACTION)) {
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
                val result = ShizukuPermissionHelper.grantWriteSecureSettings(this)
                Log.i(
                    TAG,
                    if (result.isSuccess) "grant ok" else "grant failed: ${result.exceptionOrNull()?.message}",
                )
            }
            ACTION_APPLY_MODE -> {
                val raw = intent.getStringExtra(EXTRA_MODE)
                val mode = GlobalMode.parse(raw)
                Log.i(TAG, "adb debug: apply mode=$mode")
                lifecycleScope.launch {
                    (application as ZerotierBApplication).orchestrator.applyGlobalMode(mode)
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

    fun requestVpnAndStart() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            vpnConsentLauncher.launch(prepare)
        } else {
            ZerotierBVpnService.start(this)
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
