package com.brukb.zerotier.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brukb.zerotier.R
import com.brukb.zerotier.connection.JoinStatus
import com.brukb.zerotier.connection.NodeLifecycleStatus
import com.brukb.zerotier.connection.Runtime
import com.brukb.zerotier.data.model.GlobalMode
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.proxy.SystemProxyManager
import com.brukb.zerotier.system.BatteryOptimizationHelper
import com.brukb.zerotier.system.ShizukuPermissionHelper
import com.brukb.zerotier.ui.theme.ZerotierBTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val showAdd by viewModel.showAddNetwork.collectAsState()
    val selected by viewModel.selectedNetwork.collectAsState()
    val showLinks by viewModel.showLinks.collectAsState()
    val showBatteryOpt by viewModel.showBatteryOptDialog.collectAsState()
    val grantError by viewModel.grantError.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? MainActivity
    val showGrant = settingsGrantHintVisible(
        uiState.globalMode,
        uiState.plan?.runtime,
        uiState.proxy.hasSecureSettingsPermission,
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        activity?.refreshAfterPermission()
    }

    LaunchedEffect(uiState.globalMode, showLinks) {
        if (uiState.globalMode == GlobalMode.AUTO || showLinks) {
            requestLinkPermissions(permissionLauncher)
        }
    }

    ZerotierBTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = {
                            requestLinkPermissions(permissionLauncher)
                            viewModel.setShowLinks(true)
                        }) {
                            Icon(Icons.Default.Wifi, contentDescription = stringResource(R.string.links_title))
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val lifecycle = viewModel.nodeLifecycle()
                val runtime = uiState.plan?.runtime
                RuntimeHeroCard(
                    globalMode = uiState.globalMode,
                    onMode = { mode ->
                        if (mode == GlobalMode.AUTO) {
                            requestLinkPermissions(permissionLauncher)
                        }
                        if (mode == GlobalMode.VPN) {
                            activity?.requestVpnAndStart()
                        } else {
                            viewModel.setGlobalMode(mode)
                        }
                    },
                    runtimeHeadline = runtimeHeadline(uiState.globalMode, runtime),
                    reason = uiState.plan?.reason,
                    lifecycle = lifecycle,
                    nodeId = viewModel.nodeId().orEmpty(),
                    linkLine = formatLinkLine(uiState.lastLink),
                    proxyLine = proxyStatusText(uiState.proxy),
                    isApplying = uiState.isApplying,
                    overlapWarning = viewModel.overlapWarning(uiState.vpn),
                    error = uiState.orchestratorError,
                )

                if (uiState.vpnConsentMissing) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.vpn_consent_banner),
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { activity?.requestVpnConsent() }) {
                                Text(stringResource(R.string.vpn_consent_action))
                            }
                        }
                    }
                }

                val showGrantCard = showGrant
                if (showGrantCard) {
                    GrantSecureSettingsCard(
                        shizukuAvailable = ShizukuPermissionHelper.isAvailable(),
                        adbCommand = SystemProxyManager.adbGrantCommand(context.packageName),
                        error = grantError,
                        onShizukuGrant = { viewModel.grantSecureSettings() },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.networks_title), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { viewModel.showAddNetworkDialog(true) }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_network))
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.networks, key = { it.networkId }) { network ->
                        NetworkRow(
                            network = network,
                            joinStatus = joinChipStatus(
                                lifecycle,
                                runtime,
                                network.isEnabled,
                                viewModel.networkRuntime(network.networkId),
                            ),
                            onOpen = { viewModel.openNetworkDetail(network) },
                            onToggle = { viewModel.toggleNetworkEnabled(network, it) },
                            onDelete = { viewModel.deleteNetwork(network.networkId) },
                            onPin = { viewModel.togglePinnedMain(network) },
                        )
                    }
                }
            }
        }

        if (showAdd) {
            AddNetworkDialog(
                onDismiss = { viewModel.showAddNetworkDialog(false) },
                onAdd = viewModel::addNetwork,
            )
        }

        selected?.let { network ->
            NetworkDetailScreen(
                network = network,
                onDismiss = viewModel::closeNetworkDetail,
                onSave = viewModel::saveNetwork,
            )
        }

        if (showLinks) {
            LinksScreen(
                viewModel = viewModel,
                onDismiss = { viewModel.setShowLinks(false) },
            )
        }

        if (showSettings) {
            SettingsBottomSheet(
                startOnBoot = uiState.startOnBoot,
                watchdogEnabled = uiState.privilegedWatchdogEnabled,
                pauseNodeInDoze = uiState.pauseNodeInDoze,
                batteryUnrestricted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context),
                linkDebounceSec = uiState.linkDebounceMs / 1000,
                showGrantHint = showGrant,
                packageName = context.packageName,
                nodeId = viewModel.nodeId(),
                onDismiss = { showSettings = false },
                onStartOnBoot = viewModel::setStartOnBoot,
                onWatchdogEnabled = viewModel::setPrivilegedWatchdogEnabled,
                onPauseNodeInDoze = viewModel::setPauseNodeInDoze,
                onRequestBatteryExemption = { activity?.openBatteryOptimizationSettings() },
                onOpenBatterySettings = { activity?.openBatteryOptimizationSettingsPage() },
                onOpenLinks = {
                    showSettings = false
                    requestLinkPermissions(permissionLauncher)
                    viewModel.setShowLinks(true)
                },
                onGrantHint = { showSettings = false },
            )
        }
        if (showBatteryOpt) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissBatteryOptDialog() },
                title = { Text(stringResource(R.string.battery_opt_title)) },
                text = { Text(stringResource(R.string.battery_opt_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dismissBatteryOptDialog()
                        activity?.openBatteryOptimizationSettings()
                    }) {
                        Text(stringResource(R.string.battery_opt_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissBatteryOptDialog() }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

private fun requestLinkPermissions(
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
) {
    // SSID is location-derivable: NEARBY_WIFI_DEVICES never unredacts
    // WifiInfo.getSSID() (verified on Samsung Android 16). Fine location required.
    val perms = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    launcher.launch(perms)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RuntimeHeroCard(
    globalMode: GlobalMode,
    onMode: (GlobalMode) -> Unit,
    runtimeHeadline: String,
    reason: String?,
    lifecycle: NodeLifecycleStatus,
    nodeId: String,
    linkLine: String,
    proxyLine: String?,
    isApplying: Boolean,
    overlapWarning: String?,
    error: String?,
) {
    val modes = GlobalMode.entries
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.mode_title), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = globalMode == mode,
                        onClick = { onMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) {
                        Text(mode.name)
                    }
                }
            }
            if (isApplying) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.applying), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                stringResource(R.string.runtime_line, runtimeHeadline, reason ?: "—"),
                style = MaterialTheme.typography.bodySmall,
            )
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(stringResource(nodeLifecycleLabelRes(lifecycle))) },
                modifier = Modifier,
            )
            Text(linkLine, style = MaterialTheme.typography.bodyMedium)
            if (nodeId.isNotBlank()) {
                Text(
                    stringResource(R.string.node_line, nodeId),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            proxyLine?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            overlapWarning?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NetworkRow(
    network: ZerotierBNetwork,
    joinStatus: JoinStatus?,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(network.name.ifBlank { network.networkId }, style = MaterialTheme.typography.titleSmall)
                Text(network.networkId, style = MaterialTheme.typography.bodySmall)
                joinStatus?.let { JoinStatusChip(it) }
                Text(
                    stringResource(R.string.route_priority_line, network.routePriority),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            FilterChip(
                selected = network.isPinnedMain,
                onClick = onPin,
                label = { Text(stringResource(R.string.main_chip)) },
                leadingIcon = {
                    Icon(
                        if (network.isPinnedMain) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(R.string.pin_main),
                    )
                },
            )
            Switch(checked = network.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_network))
            }
        }
    }
}

@Composable
private fun AddNetworkDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var networkId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_network)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = networkId,
                    onValueChange = { networkId = it },
                    label = { Text(stringResource(R.string.network_id_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.network_name_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(networkId, name) }) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
