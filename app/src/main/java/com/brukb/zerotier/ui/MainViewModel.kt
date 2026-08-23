package com.brukb.zerotier.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brukb.zerotier.ZerotierBApplication
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.vpn.VpnServiceState
import com.brukb.zerotier.vpn.ZerotierBVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val serviceState: VpnServiceState = VpnServiceState(),
    val networks: List<ZerotierBNetwork> = emptyList(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ZerotierBApplication

    private val networks = app.networkRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<MainUiState> = combine(
        ZerotierBVpnService.state,
        networks,
    ) { service, networkList ->
        MainUiState(serviceState = service, networks = networkList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private val _showAddNetwork = MutableStateFlow(false)
    val showAddNetwork: StateFlow<Boolean> = _showAddNetwork

    private val _selectedNetwork = MutableStateFlow<ZerotierBNetwork?>(null)
    val selectedNetwork: StateFlow<ZerotierBNetwork?> = _selectedNetwork

    fun toggleRunning(enabled: Boolean, requestVpn: () -> Unit) {
        if (enabled) {
            requestVpn()
        } else {
            ZerotierBVpnService.stop(getApplication())
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
            if (ZerotierBVpnService.state.value.isRunning) {
                ZerotierBVpnService.joinNetwork(getApplication(), normalized)
            }
        }
        _showAddNetwork.value = false
    }

    fun deleteNetwork(networkId: String) {
        viewModelScope.launch {
            if (ZerotierBVpnService.state.value.isRunning) {
                ZerotierBVpnService.leaveNetwork(getApplication(), networkId)
            }
            app.networkRepository.delete(networkId)
        }
    }

    fun toggleNetworkEnabled(network: ZerotierBNetwork, enabled: Boolean) {
        viewModelScope.launch {
            val updated = network.copy(isEnabled = enabled)
            app.networkRepository.update(updated)
            if (ZerotierBVpnService.state.value.isRunning) {
                if (enabled) {
                    ZerotierBVpnService.joinNetwork(getApplication(), network.networkId)
                } else {
                    ZerotierBVpnService.leaveNetwork(getApplication(), network.networkId)
                }
            }
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            app.preferences.setStartOnBoot(enabled)
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
            app.networkRepository.update(network)
            _selectedNetwork.value = network
            if (ZerotierBVpnService.state.value.isRunning && network.isEnabled) {
                ZerotierBVpnService.joinNetwork(getApplication(), network.networkId)
            }
        }
    }

    fun runtimeStatus(networkId: String, state: VpnServiceState): String {
        val normalized = ZerotierBNetwork.normalizeNetworkId(networkId)
        return state.networkStatuses.firstOrNull {
            ZerotierBNetwork.normalizeNetworkId(it.networkId) == normalized
        }?.status ?: "—"
    }

    fun overlapWarning(state: VpnServiceState): String? {
        if (state.overlappingRoutes.isEmpty()) return null
        return "Overlapping routes (same priority): ${state.overlappingRoutes.joinToString()}"
    }
}
