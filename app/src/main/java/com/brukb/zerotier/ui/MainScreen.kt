package com.brukb.zerotier.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brukb.zerotier.data.model.ZerotierBNetwork
import com.brukb.zerotier.ui.theme.ZerotierBTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val showAdd by viewModel.showAddNetwork.collectAsState()
    val selected by viewModel.selectedNetwork.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? MainActivity

    ZerotierBTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("ZerotierB") },
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
                    onToggle = { enabled ->
                        viewModel.toggleRunning(enabled) {
                            activity?.requestVpnAndStart()
                        }
                    },
                    nodeId = uiState.serviceState.nodeId,
                    status = uiState.serviceState.statusMessage,
                    overlapWarning = viewModel.overlapWarning(uiState.serviceState),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Networks", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { viewModel.showAddNetworkDialog(true) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add network")
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.networks, key = { it.networkId }) { network ->
                        NetworkRow(
                            network = network,
                            runtimeStatus = viewModel.runtimeStatus(network.networkId, uiState.serviceState),
                            onOpen = { viewModel.openNetworkDetail(network) },
                            onToggle = { viewModel.toggleNetworkEnabled(network, it) },
                            onDelete = { viewModel.deleteNetwork(network.networkId) },
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

        if (showSettings) {
            SettingsDialog(
                onDismiss = { showSettings = false },
                onStartOnBoot = viewModel::setStartOnBoot,
            )
        }
    }
}

@Composable
private fun StatusCard(
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    nodeId: String,
    status: String,
    overlapWarning: String?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("VPN", style = MaterialTheme.typography.titleMedium)
                Switch(checked = isRunning, onCheckedChange = onToggle)
            }
            if (nodeId.isNotBlank()) {
                Text("Node: $nodeId", style = MaterialTheme.typography.bodySmall)
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)
            overlapWarning?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NetworkRow(
    network: ZerotierBNetwork,
    runtimeStatus: String,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
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
                Text("Status: $runtimeStatus", style = MaterialTheme.typography.bodySmall)
                Text("Route priority: ${network.routePriority}", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = network.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
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
        title = { Text("Join network") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = networkId,
                    onValueChange = { networkId = it },
                    label = { Text("Network ID (16 hex)") },
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
            TextButton(onClick = { onAdd(networkId, name) }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SettingsDialog(
    onDismiss: () -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
) {
    var startOnBoot by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Start VPN on boot")
                Switch(checked = startOnBoot, onCheckedChange = {
                    startOnBoot = it
                    onStartOnBoot(it)
                })
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
