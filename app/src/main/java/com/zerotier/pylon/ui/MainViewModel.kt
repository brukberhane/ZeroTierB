package com.zerotier.pylon.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zerotier.pylon.PylonApplication
import com.zerotier.pylon.data.model.PylonNetwork
import com.zerotier.pylon.proxy.SystemProxyManager
import com.zerotier.pylon.service.NetworkJoinStatus
import com.zerotier.pylon.service.NodeStatus
import com.zerotier.pylon.service.PylonService
import com.zerotier.pylon.service.PylonServiceState
import com.zerotier.pylon.system.ProxyWatchdog
import com.zerotier.pylon.system.ShizukuPermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val serviceState: PylonServiceState = PylonServiceState(),
    val networks: List<PylonNetwork> = emptyList(),
    val adbGrantCommand: String = "",
    val serviceWanted: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PylonApplication

    private val networks = app.networkRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<MainUiState> = combine(
        PylonService.state,
        networks,
        app.preferences.serviceWanted,
    ) { service, networkList, wanted ->
        MainUiState(
            serviceState = service,
            networks = networkList,
            adbGrantCommand = SystemProxyManager.adbGrantCommand(application.packageName),
            serviceWanted = wanted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private val _showAddNetwork = MutableStateFlow(false)
    val showAddNetwork: StateFlow<Boolean> = _showAddNetwork

    private val _selectedNetwork = MutableStateFlow<PylonNetwork?>(null)
    val selectedNetwork: StateFlow<PylonNetwork?> = _selectedNetwork

    fun toggleRunning(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            PylonService.start(context)
        } else {
            PylonService.stop(context)
        }
    }

    fun showAddNetworkDialog(show: Boolean) {
        _showAddNetwork.value = show
    }

    fun addNetwork(networkId: String, name: String = "") {
        if (!PylonNetwork.isValidNetworkId(networkId)) return
        val normalized = PylonNetwork.normalizeNetworkId(networkId)
        viewModelScope.launch {
            app.networkRepository.upsert(
                PylonNetwork(networkId = normalized, name = name.ifBlank { normalized }),
            )
            if (PylonService.state.value.isRunning) {
                PylonService.joinNetwork(getApplication(), normalized)
            }
        }
        _showAddNetwork.value = false
    }

    fun deleteNetwork(networkId: String) {
        viewModelScope.launch {
            if (PylonService.state.value.isRunning) {
                PylonService.leaveNetwork(getApplication(), networkId)
            }
            app.networkRepository.delete(networkId)
        }
    }

    fun joinNetwork(networkId: String) {
        PylonService.joinNetwork(getApplication(), networkId)
    }

    fun leaveNetwork(networkId: String) {
        PylonService.leaveNetwork(getApplication(), networkId)
    }

    fun toggleProxy(enabled: Boolean) {
        PylonService.setProxyEnabled(getApplication(), enabled)
    }

    fun grantViaShizuku() {
        viewModelScope.launch {
            ShizukuPermissionHelper.grantWriteSecureSettings(getApplication())
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            app.preferences.setStartOnBoot(enabled)
        }
    }

    fun setWatchdogEnabled(enabled: Boolean): Boolean {
        if (enabled && !ShizukuPermissionHelper.canRunPrivileged()) {
            return false
        }
        viewModelScope.launch {
            app.preferences.setPrivilegedWatchdogEnabled(enabled)
            val context = getApplication<Application>()
            if (enabled) {
                ProxyWatchdog.startIfNeeded(context)
            } else {
                ProxyWatchdog.stop(context)
            }
        }
        return true
    }

    fun setPauseNodeInDoze(enabled: Boolean) {
        viewModelScope.launch {
            app.preferences.setPauseNodeInDoze(enabled)
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val appContext = getApplication<Application>()
        val pm = appContext.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    fun requestBatteryExemption(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    }

    fun openBatterySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openNetworkDetail(network: PylonNetwork) {
        _selectedNetwork.value = network
    }

    fun closeNetworkDetail() {
        _selectedNetwork.value = null
    }

    fun saveNetwork(network: PylonNetwork) {
        viewModelScope.launch {
            app.networkRepository.update(network)
            _selectedNetwork.value = network
        }
    }

    fun setSocks5Enabled(enabled: Boolean) {
        viewModelScope.launch {
            app.preferences.setSocks5Enabled(enabled)
        }
    }

    fun setHttpPort(port: Int) {
        viewModelScope.launch {
            app.preferences.setHttpProxyPort(port)
        }
    }

    fun setSocks5Port(port: Int) {
        viewModelScope.launch {
            app.preferences.setSocks5ProxyPort(port)
        }
    }

    fun statusLabel(state: PylonServiceState, serviceWanted: Boolean): String {
        return when {
            state.nodeStatus == NodeStatus.ERROR -> state.statusMessage
            state.networkJoinStatus == NetworkJoinStatus.ACCESS_DENIED -> "Network access denied"
            state.isRunning -> state.statusMessage
            serviceWanted -> "restarting…"
            else -> "Stopped"
        }
    }
}
