package com.zerotier.pylon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zerotier.pylon.PylonApplication
import com.zerotier.pylon.data.AppPreferences

@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    onSocks5Enabled: (Boolean) -> Unit,
    onHttpPort: (Int) -> Unit,
    onSocks5Port: (Int) -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = (context.applicationContext as PylonApplication).preferences
    val socksEnabled by prefs.socks5Enabled.collectAsState(initial = false)
    val httpPort by prefs.httpProxyPort.collectAsState(initial = AppPreferences.DEFAULT_HTTP_PORT)
    val socksPort by prefs.socks5ProxyPort.collectAsState(initial = AppPreferences.DEFAULT_SOCKS5_PORT)
    val startOnBoot by prefs.startOnBoot.collectAsState(initial = false)

    var httpPortText by remember(httpPort) { mutableStateOf(httpPort.toString()) }
    var socksPortText by remember(socksPort) { mutableStateOf(socksPort.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = httpPortText,
                    onValueChange = {
                        httpPortText = it
                        it.toIntOrNull()?.let(onHttpPort)
                    },
                    label = { Text("HTTP proxy port") },
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enable SOCKS5 proxy")
                    Switch(
                        checked = socksEnabled,
                        onCheckedChange = onSocks5Enabled,
                    )
                }
                OutlinedTextField(
                    value = socksPortText,
                    onValueChange = {
                        socksPortText = it
                        it.toIntOrNull()?.let(onSocks5Port)
                    },
                    label = { Text("SOCKS5 proxy port") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = socksEnabled,
                )
                Text(
                    "SOCKS5 is disabled by default. Enable only if you need manual app proxy configuration.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Start on boot")
                    Switch(checked = startOnBoot, onCheckedChange = onStartOnBoot)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
