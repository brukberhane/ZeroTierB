package com.brukb.zerotier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brukb.zerotier.data.model.ZerotierBNetwork

@Composable
fun NetworkDetailScreen(
    network: ZerotierBNetwork,
    onDismiss: () -> Unit,
    onSave: (ZerotierBNetwork) -> Unit,
) {
    var edited by remember(network) { mutableStateOf(network) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Network settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(edited) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
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
