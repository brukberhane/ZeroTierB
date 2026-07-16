package com.zerotier.pylon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zerotier.pylon.data.model.PylonNetwork

@Composable
fun NetworkDetailScreen(
    network: PylonNetwork,
    onDismiss: () -> Unit,
    onSave: (PylonNetwork) -> Unit,
) {
    var edited by remember(network) { mutableStateOf(network) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network ${network.networkId}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = edited.name,
                    onValueChange = { edited = edited.copy(name = it) },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                ToggleRow("Enabled", edited.isEnabled) { edited = edited.copy(isEnabled = it) }
                ToggleRow("Enabled in proxy", edited.enabledInProxy) { edited = edited.copy(enabledInProxy = it) }
                ToggleRow("Allow managed", edited.allowManaged) { edited = edited.copy(allowManaged = it) }
                ToggleRow("Allow default", edited.allowDefault) { edited = edited.copy(allowDefault = it) }
                ToggleRow("Allow global", edited.allowGlobal) { edited = edited.copy(allowGlobal = it) }
                ToggleRow("Allow DNS", edited.allowDns) { edited = edited.copy(allowDns = it) }
                ToggleRow("Block outside", edited.blockOutside) { edited = edited.copy(blockOutside = it) }
                OutlinedTextField(
                    value = edited.customDnsServers,
                    onValueChange = { edited = edited.copy(customDnsServers = it) },
                    label = { Text("Custom DNS (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = edited.allowRules,
                    onValueChange = { edited = edited.copy(allowRules = it) },
                    label = { Text("Allow rules (host or host:port per line)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = edited.denyRules,
                    onValueChange = { edited = edited.copy(denyRules = it) },
                    label = { Text("Deny rules (host or host:port per line)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(edited); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
