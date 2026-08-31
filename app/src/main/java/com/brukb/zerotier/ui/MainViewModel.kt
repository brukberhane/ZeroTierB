package com.brukb.zerotier.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.connection.OrchestratorState
import com.brukb.zerotier.connection.PhysicalLink
import com.brukb.zerotier.connection.Runtime
import com.brukb.zerotier.connection.RuntimePlan
import com.brukb.zerotier.connection.NetworkRuntimeStatus
import com.brukb.zerotier.connection.NodeLifecycleStatus
import com.brukb.zerotier.connection.resolveNetworkRuntime
import com.brukb.zerotier.connection.resolveNodeLifecycle
import com.brukb.zerotier.data.AppPreferences
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.LinkKind
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.LinkProfile
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.ProxyModeService
import com.brukb.zerotier.proxy.ProxyServiceState
import com.brukb.zerotier.system.BatteryOptimizationHelper
import com.brukb.zerotier.system.ProxyWatchdog
import com.brukb.zerotier.system.ShizukuPermissionHelper
import com.brukb.zerotier.vpn.VpnServiceState
import com.brukb.zerotier.vpn.ZerotierBVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val globalMode: GlobalMode = GlobalMode.OFF,
    val plan: RuntimePlan? = null,
    val lastLink: PhysicalLink? = null,
    val isApplying: Boolean = false,
    val orchestratorError: String? = null,
    val vpnConsentMissing: Boolean = false,
    val vpn: VpnServiceState = VpnServiceState(),
    val proxy: ProxyServiceState = ProxyServiceState(),
    val networks: List<ZerotierBNetwork> = emptyList(),
    val linkProfiles: List<LinkProfile> = emptyList(),
    val linkDebounceMs: Int = AppPreferences.DEFAULT_LINK_DEBOUNCE_MS,
    val startOnBoot: Boolean = false,
    val privilegedWatchdogEnabled: Boolean = false,
    val pauseNodeInDoze: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ZerotierBApplication

    val uiState: StateFlow<MainUiState> = combine(
        app.preferences.globalMode,
        app.orchestrator.state,
        ZerotierBVpnService.state,
        ProxyModeService.state,
        app.networkRepository.observeAll(),
        app.linkProfileRepository.observeAll(),
        app.preferences.linkDebounceMs,
        app.preferences.startOnBoot,
        app.preferences.privilegedWatchdogEnabled,
        app.preferences.pauseNodeInDoze,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val mode = values[0] as GlobalMode
        val orch = values[1] as OrchestratorState
        val vpn = values[2] as VpnServiceState
        val proxy = values[3] as ProxyServiceState
        val networks = values[4] as List<ZerotierBNetwork>
        val profiles = values[5] as List<LinkProfile>
        val debounce = values[6] as Int
        val boot = values[7] as Boolean
        val watchdog = values[8] as Boolean
        val pauseDoze = values[9] as Boolean
        MainUiState(
            globalMode = mode,
            plan = orch.plan,
            lastLink = orch.lastLink,
            isApplying = orch.isApplying,
            orchestratorError = orch.lastError,
            vpnConsentMissing = orch.plan?.vpnConsentMissing == true,
            vpn = vpn,
            proxy = proxy,
            networks = networks,
            linkProfiles = profiles,
            linkDebounceMs = debounce,
            startOnBoot = boot,
            privilegedWatchdogEnabled = watchdog,
            pauseNodeInDoze = pauseDoze,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private val _showAddNetwork = MutableStateFlow(false)
    val showAddNetwork: StateFlow<Boolean> = _showAddNetwork

    private val _selectedNetwork = MutableStateFlow<ZerotierBNetwork?>(null)
    val selectedNetwork: StateFlow<ZerotierBNetwork?> = _selectedNetwork

    private val _showLinks = MutableStateFlow(false)
    val showLinks: StateFlow<Boolean> = _showLinks

    private val _showBatteryOptDialog = MutableStateFlow(false)
    val showBatteryOptDialog: StateFlow<Boolean> = _showBatteryOptDialog

    private val _grantError = MutableStateFlow<String?>(null)
    val grantError: StateFlow<String?> = _grantError

    fun setShowLinks(show: Boolean) {
        _showLinks.value = show
    }

    fun setGlobalMode(mode: GlobalMode) {
        viewModelScope.launch {
            app.orchestrator.applyGlobalMode(mode)
            if (shouldPromptBatteryOpt(mode)) {
                _showBatteryOptDialog.value = true
            }
        }
    }

    fun dismissBatteryOptDialog() {
        viewModelScope.launch {
            app.preferences.setBatteryOptPrompted()
            _showBatteryOptDialog.value = false
        }
    }

    private suspend fun shouldPromptBatteryOpt(mode: GlobalMode): Boolean {
        if (mode != GlobalMode.PROXY && mode != GlobalMode.AUTO) return false
        if (app.preferences.hasBatteryOptPrompted()) return false
        return !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(getApplication())
    }

    fun togglePinnedMain(network: ZerotierBNetwork) {
        viewModelScope.launch {
            if (network.isPinnedMain) {
                app.networkRepository.clearPinnedMain()
            } else {
                app.networkRepository.setPinnedMain(network.networkId)
            }
            app.orchestrator.refresh()
        }
    }

    fun showAddNetworkDialog(show: Boolean) {
        _showAddNetwork.value = show
    }

    fun addNetwork(networkId: String, name: String = "") {
        if (!ZerotierBNetwork.isValidNetworkId(networkId)) return
        val normalized = ZerotierBNetwork.normalizeNetworkId(networkId)
        viewModelScope.launch {
            app.networkRepository.upsert(
                ZerotierBNetwork(networkId = normalized, name = name.ifBlank { normalized }),
            )
            app.orchestrator.refresh()
        }
        _showAddNetwork.value = false
    }

    fun deleteNetwork(networkId: String) {
        viewModelScope.launch {
            app.networkRepository.delete(networkId)
            app.orchestrator.refresh()
        }
    }

    fun toggleNetworkEnabled(network: ZerotierBNetwork, enabled: Boolean) {
        viewModelScope.launch {
            app.networkRepository.update(network.copy(isEnabled = enabled))
            app.orchestrator.refresh()
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            app.preferences.setStartOnBoot(enabled)
        }
    }

    fun setPrivilegedWatchdogEnabled(enabled: Boolean): Boolean {
        if (enabled && !ShizukuPermissionHelper.hasApiPermission()) {
            return false
        }
        viewModelScope.launch {
            app.preferences.setPrivilegedWatchdogEnabled(enabled)
            if (enabled) {
                ProxyWatchdog.startIfNeeded(getApplication())
            } else {
                ProxyWatchdog.stop(getApplication())
            }
        }
        return true
    }

    fun setPauseNodeInDoze(enabled: Boolean) {
        viewModelScope.launch {
            app.preferences.setPauseNodeInDoze(enabled)
        }
    }

    fun openNetworkDetail(network: ZerotierBNetwork) {
        _selectedNetwork.value = network
    }

    fun closeNetworkDetail() {
        _selectedNetwork.value = null
    }

    fun saveNetwork(network: ZerotierBNetwork) {
        viewModelScope.launch {
            if (network.isPinnedMain) {
                app.networkRepository.setPinnedMain(network.networkId)
            }
            app.networkRepository.update(network)
            _selectedNetwork.value = network
            app.orchestrator.refresh()
        }
    }

    fun setLinkMode(profile: LinkProfile, mode: LinkMode) {
        viewModelScope.launch {
            app.linkProfileRepository.upsert(profile.copy(mode = mode))
            app.orchestrator.refresh()
        }
    }

    fun deleteWifiProfile(profile: LinkProfile) {
        if (profile.kind != LinkKind.WIFI) return
        viewModelScope.launch {
            app.linkProfileRepository.delete(profile.id)
            app.orchestrator.refresh()
        }
    }

    fun saveCurrentSsid() {
        val ssid = unsavedWifiSsid(uiState.value.lastLink) ?: return
        viewModelScope.launch {
            app.linkProfileRepository.upsertWifi(ssid)
            app.orchestrator.refresh()
        }
    }

    fun setLinkDebounceMs(ms: Int) {
        viewModelScope.launch {
            app.preferences.setLinkDebounceMs(ms)
        }
    }

    fun grantSecureSettings() {
        viewModelScope.launch {
            _grantError.value = null
            when (val request = ShizukuPermissionHelper.requestPermission()) {
                is ShizukuPermissionHelper.PermissionRequest.Error -> {
                    _grantError.value = request.message
                }
                ShizukuPermissionHelper.PermissionRequest.Requested -> {
                    // Async: Shizuku dialog result listener in ZerotierBApplication
                    // runs the grant + orchestrator refresh.
                }
                ShizukuPermissionHelper.PermissionRequest.AlreadyGranted -> {
                    runGrant()
                }
            }
        }
    }

    private suspend fun runGrant() {
        val result = ShizukuPermissionHelper.grantWriteSecureSettings(getApplication())
        if (result.isSuccess) {
            app.orchestrator.invalidateAppliedPlan()
            app.orchestrator.refresh()
        } else {
            _grantError.value = result.exceptionOrNull()?.message ?: "Grant failed"
        }
    }

    fun activeRuntime(): Runtime? = uiState.value.plan?.runtime

    fun nodeLifecycle(): NodeLifecycleStatus =
        resolveNodeLifecycle(uiState.value.plan?.runtime, uiState.value.proxy, uiState.value.vpn)

    fun networkRuntime(networkId: String): NetworkRuntimeStatus? =
        resolveNetworkRuntime(
            uiState.value.plan?.runtime,
            uiState.value.proxy,
            uiState.value.vpn,
            networkId,
        )

    fun nodeId(): String? = when (uiState.value.plan?.runtime) {
        Runtime.PROXY -> uiState.value.proxy.nodeId
        Runtime.VPN -> uiState.value.vpn.nodeId.takeIf { it.isNotBlank() }
        else -> null
    }

    fun overlapWarning(state: VpnServiceState): String? {
        if (state.overlappingRoutes.isEmpty()) return null
        return "Overlapping routes (same priority): ${state.overlappingRoutes.joinToString()}"
    }
}
