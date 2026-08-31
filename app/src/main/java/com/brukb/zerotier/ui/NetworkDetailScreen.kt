package com.brukb.zerotier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.brukb.zerotier.R
import com.brukb.zerotier.connection.JoinStatus
import com.brukb.zerotier.connection.NetworkRuntimeStatus
import com.brukb.zerotier.connection.Runtime
import com.brukb.zerotier.connection.filterDisplayRoutes
import com.brukb.zerotier.data.model.ZerotierBNetwork
import kotlinx.coroutines.launch

@Composable
fun NetworkDetailScreen(
    network: ZerotierBNetwork,
    joinStatus: JoinStatus?,
    runtimeStatus: NetworkRuntimeStatus?,
    activeRuntime: Runtime?,
    onDismiss: () -> Unit,
    onSave: (ZerotierBNetwork) -> Unit,
) {
    var edited by remember(network) { mutableStateOf(network) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network settings") },
        text = {
            Box {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CopyableMonoText(
                        value = network.networkId,
                        contentDescription = stringResource(R.string.copy_network_id),
                        onCopied = {
                            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                        },
                    )
                    joinStatus?.let { JoinStatusChip(it) }

                    RuntimeSection(
                        network = edited,
                        runtimeStatus = runtimeStatus,
                        activeRuntime = activeRuntime,
                    )

                    OutlinedTextField(
                        value = edited.name,
                        onValueChange = { edited = edited.copy(name = it) },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = edited.routePriority.toString(),
                        onValueChange = { value ->
                            edited = edited.copy(routePriority = value.toIntOrNull() ?: 0)
                        },
                        label = { Text("Route priority (lower wins on overlap)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    ToggleRow("Allow managed", edited.allowManaged) {
                        edited = edited.copy(allowManaged = it)
                    }
                    ToggleRow("Allow default route", edited.allowDefault) {
                        edited = edited.copy(allowDefault = it)
                    }
                    ToggleRow("Allow global", edited.allowGlobal) {
                        edited = edited.copy(allowGlobal = it)
                    }
                    ToggleRow("Allow DNS", edited.allowDns) {
                        edited = edited.copy(allowDns = it)
                    }
                    ToggleRow("Main network", edited.isPinnedMain) {
                        edited = edited.copy(isPinnedMain = it)
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(edited) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun RuntimeSection(
    network: ZerotierBNetwork,
    runtimeStatus: NetworkRuntimeStatus?,
    activeRuntime: Runtime?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.detail_runtime_section),
            style = MaterialTheme.typography.titleSmall,
        )
        when {
            !network.isEnabled || activeRuntime == null || activeRuntime == Runtime.OFF -> {
                Text(
                    stringResource(R.string.detail_not_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            runtimeStatus == null && activeRuntime == Runtime.VPN -> {
                Text(
                    stringResource(R.string.detail_vpn_main_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            runtimeStatus == null -> {
                Text(
                    stringResource(R.string.detail_not_connected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                RuntimeListSection(
                    title = stringResource(R.string.detail_addresses),
                    lines = runtimeStatus.assignedAddresses,
                )
                RuntimeListSection(
                    title = stringResource(R.string.detail_routes),
                    lines = filterDisplayRoutes(
                        runtimeStatus.routes,
                        network.allowManaged,
                        network.allowDefault,
                        network.allowGlobal,
                    ),
                )
                RuntimeListSection(
                    title = stringResource(R.string.detail_dns),
                    lines = runtimeStatus.dnsServers,
                )
            }
        }
    }
}

@Composable
private fun RuntimeListSection(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (lines.isEmpty()) {
            Text(
                stringResource(R.string.detail_none),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
