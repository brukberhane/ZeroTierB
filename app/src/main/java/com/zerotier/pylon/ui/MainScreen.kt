package com.zerotier.pylon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zerotier.pylon.data.model.PylonNetwork
import com.zerotier.pylon.service.NetworkRuntimeStatus
import com.zerotier.pylon.ui.theme.ZeroTierPylonTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val showAdd by viewModel.showAddNetwork.collectAsState()
    val selected by viewModel.selectedNetwork.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    ZeroTierPylonTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ZeroTier Pylon") },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                StatusCard(
                    isRunning = uiState.serviceState.isRunning,
                    onToggle = viewModel::toggleRunning,
                    proxyEnabled = uiState.serviceState.proxyEnabled,
                    onProxyToggle = viewModel::toggleProxy,
                    nodeId = uiState.serviceState.nodeId,
                    status = viewModel.statusLabel(uiState.serviceState),
                    httpPort = uiState.serviceState.httpProxyPort,
                    systemProxyActive = uiState.serviceState.systemProxyActive,
                    socks5Enabled = uiState.serviceState.socks5Enabled,
                    socks5Port = uiState.serviceState.socks5ProxyPort,
                )

                PermissionCard(
                    hasPermission = uiState.serviceState.hasSecureSettingsPermission,
                    adbCommand = uiState.adbGrantCommand,
                    onGrantShizuku = viewModel::grantViaShizuku,
                )

                NetworksCard(
                    networks = uiState.networks,
                    networkStatuses = uiState.serviceState.networkStatuses,
                    serviceRunning = uiState.serviceState.isRunning,
                    onAdd = { viewModel.showAddNetworkDialog(true) },
                    onOpen = viewModel::openNetworkDetail,
                    onDelete = viewModel::deleteNetwork,
                    onJoin = viewModel::joinNetwork,
                    onLeave = viewModel::leaveNetwork,
                )

                LogsCard(logs = uiState.serviceState.logs)
            }
        }
    }

    if (showAdd) {
        AddNetworkDialog(
            onDismiss = { viewModel.showAddNetworkDialog(false) },
            onConfirm = viewModel::addNetwork,
        )
    }

    selected?.let { network ->
        NetworkDetailScreen(
            network = network,
            onDismiss = viewModel::closeNetworkDetail,
            onSave = viewModel::saveNetwork,
        )
    }

    if (showSettings) {
        SettingsScreen(
            onDismiss = { showSettings = false },
            onSocks5Enabled = viewModel::setSocks5Enabled,
            onHttpPort = viewModel::setHttpPort,
            onSocks5Port = viewModel::setSocks5Port,
            onStartOnBoot = viewModel::setStartOnBoot,
        )
    }
}

@Composable
private fun StatusCard(
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    proxyEnabled: Boolean,
    onProxyToggle: (Boolean) -> Unit,
    nodeId: String?,
    status: String,
    httpPort: Int?,
    systemProxyActive: Boolean,
    socks5Enabled: Boolean,
    socks5Port: Int?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pylon", style = MaterialTheme.typography.titleMedium)
                Switch(checked = isRunning, onCheckedChange = onToggle)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("HTTP proxy")
                Switch(
                    checked = proxyEnabled,
                    onCheckedChange = onProxyToggle,
                    enabled = isRunning,
                )
            }
            Text("Status: $status")
            nodeId?.let { Text("Node ID: $it") }
            httpPort?.let { Text("HTTP proxy: 127.0.0.1:$it") }
            Text("System proxy: ${if (systemProxyActive) "active" else "inactive"}")
            if (socks5Enabled) {
                Text("SOCKS5 proxy: 127.0.0.1:${socks5Port ?: "-"}")
            }
        }
    }
}

@Composable
private fun PermissionCard(
    hasPermission: Boolean,
    adbCommand: String,
    onGrantShizuku: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("System proxy permission", style = MaterialTheme.typography.titleMedium)
            Text(if (hasPermission) "WRITE_SECURE_SETTINGS granted" else "Permission not granted")
            if (!hasPermission) {
                Text("Grant once via ADB or Shizuku:")
                Text(adbCommand, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(adbCommand)) }) {
                        Text("Copy ADB")
                    }
                    TextButton(onClick = onGrantShizuku) {
                        Text("Grant via Shizuku")
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworksCard(
    networks: List<PylonNetwork>,
    networkStatuses: Map<String, NetworkRuntimeStatus>,
    serviceRunning: Boolean,
    onAdd: () -> Unit,
    onOpen: (PylonNetwork) -> Unit,
    onDelete: (String) -> Unit,
    onJoin: (String) -> Unit,
    onLeave: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Networks", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Add network")
                }
            }
            if (networks.isEmpty()) {
                Text("No networks configured")
            } else {
                networks.forEach { network ->
                    val runtime = networkStatuses[network.networkId]
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { onOpen(network) }) {
                                Text(network.name.ifBlank { network.networkId })
                            }
                            Row {
                                if (serviceRunning) {
                                    TextButton(onClick = { onJoin(network.networkId) }) {
                                        Text("Join")
                                    }
                                    TextButton(onClick = { onLeave(network.networkId) }) {
                                        Text("Leave")
                                    }
                                }
                                IconButton(onClick = { onDelete(network.networkId) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        }
                        runtime?.let {
                            Text(
                                "Status: ${it.joinStatus.name}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (it.assignedAddresses.isNotEmpty()) {
                                Text(
                                    "Addrs: ${it.assignedAddresses.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (it.routes.isNotEmpty()) {
                                Text(
                                    "Routes: ${it.routes.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (it.dnsServers.isNotEmpty() || it.dnsDomain.isNotBlank()) {
                                Text(
                                    "DNS: ${it.dnsDomain.ifBlank { "-" }} ${it.dnsServers.joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsCard(logs: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Logs", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(160.dp)) {
                items(logs.reversed()) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AddNetworkDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var networkId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join network") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = networkId,
                    onValueChange = { networkId = it },
                    label = { Text("Network ID (hex)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(networkId, name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
