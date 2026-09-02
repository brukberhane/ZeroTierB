package com.brukb.zerotier

import android.app.Application
import com.brukb.zerotier.log.AppLog
import com.brukb.zerotier.connection.ConnectionOrchestrator
import com.brukb.zerotier.data.AppDatabase
import com.brukb.zerotier.data.AppPreferences
import com.brukb.zerotier.data.LinkProfileRepository
import com.brukb.zerotier.data.NetworkRepository
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.proxy.SystemProxyManager
import com.brukb.zerotier.system.LinkObserver
import com.brukb.zerotier.system.ProxyHealthJob
import com.brukb.zerotier.system.ProxyWatchdog
import com.brukb.zerotier.system.ShizukuPermissionHelper
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ZerotierBApplication : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var networkRepository: NetworkRepository
        private set

    lateinit var linkProfileRepository: LinkProfileRepository
        private set

    lateinit var preferences: AppPreferences
        private set

    lateinit var orchestrator: ConnectionOrchestrator
        private set

    lateinit var linkObserver: LinkObserver
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal val applicationScope: CoroutineScope get() = appScope

    override fun onCreate() {
        super.onCreate()
        AppLog.install(File(filesDir, "logs"))
        installCrashLogger()
        registerShizukuListeners()
        database = AppDatabase.getInstance(this)
        networkRepository = NetworkRepository(database.networkDao())
        linkProfileRepository = LinkProfileRepository(database.linkProfileDao())
        preferences = AppPreferences(this)
        orchestrator = ConnectionOrchestrator(
            context = this,
            preferences = preferences,
            networkRepository = networkRepository,
            linkProfileRepository = linkProfileRepository,
            scope = appScope,
        )
        linkObserver = LinkObserver(
            context = this,
            preferences = preferences,
            linkProfileRepository = linkProfileRepository,
            orchestrator = orchestrator,
            scope = appScope,
        )
        appScope.launch {
            preferences.verboseFileLog.collect { AppLog.verbose = it }
        }
        appScope.launch {
            networkRepository.migrateStoredNetworkIds()
            preferences.migrateGlobalModeIfNeeded()
            linkProfileRepository.seedOther()
            runCatching {
                val mgr = SystemProxyManager(this@ZerotierBApplication, preferences)
                val mode = preferences.globalMode.first()
                val current = mgr.currentProxy()
                val saved = preferences.savedHttpProxy.first()
                val lastPort = preferences.lastHttpProxyPort.first()
                val port = SystemProxyManager.parseLoopbackPort(current)
                    ?: lastPort.takeIf { it > 0 && SystemProxyManager.isOurLoopback(current, lastPort) }
                val listenAlive = port?.let { SystemProxyManager.probeListen(it) }
                val clear = SystemProxyManager.shouldClearStale(
                    current,
                    saved,
                    lastPort,
                    mode == GlobalMode.PROXY,
                    listenAlive,
                )
                AppLog.i(
                    TAG,
                    "proxy-boot current=$current saved=$saved lastPort=$lastPort mode=$mode " +
                        "listenAlive=$listenAlive clear=$clear",
                )
                if (clear) {
                    mgr.disable()
                    AppLog.i(TAG, "Cleared stale system proxy (mode=$mode listenAlive=$listenAlive)")
                }
                if (mode == GlobalMode.OFF) {
                    ProxyHealthJob.cancel(this@ZerotierBApplication)
                }
            }
            linkObserver.start()
        }
    }

    private fun registerShizukuListeners() {
        runCatching {
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode != ShizukuPermissionHelper.REQUEST_CODE) return@addRequestPermissionResultListener
                if (grantResult != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    AppLog.w(TAG, "Shizuku permission denied")
                    return@addRequestPermissionResultListener
                }
                appScope.launch {
                    ShizukuPermissionHelper.grantWriteSecureSettings(this@ZerotierBApplication)
                        .onSuccess {
                            orchestrator.invalidateAppliedPlan()
                            orchestrator.refresh()
                        }
                        .onFailure { AppLog.w(TAG, "grant after Shizuku permission failed", it) }
                }
            }
        }.onFailure { AppLog.w(TAG, "Shizuku listener registration failed", it) }
        val binderListener = Shizuku.OnBinderReceivedListener {
            appScope.launch {
                if (!preferences.privilegedWatchdogEnabled.first()) return@launch
                if (!ShizukuPermissionHelper.hasApiPermission()) return@launch
                ProxyWatchdog.startIfNeeded(this@ZerotierBApplication)
            }
        }
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderListener) }
            .onFailure { AppLog.w(TAG, "Shizuku binder listener not registered", it) }
        runCatching {
            Shizuku.addBinderDeadListener {
                AppLog.w(TAG, "Shizuku binder died")
            }
        }.onFailure { AppLog.w(TAG, "Shizuku binder-dead listener not registered", it) }
    }

    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            AppLog.e(TAG, "uncaught on ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        private const val TAG = "ZerotierBApplication"
    }
}
