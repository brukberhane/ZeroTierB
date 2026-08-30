package com.zerotier.pylon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
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
    ignoringBattery: Boolean,
    onRequestBatteryExemption: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onWatchdogEnabled: (Boolean) -> Boolean,
    onPauseNodeInDoze: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val prefs = (context.applicationContext as PylonApplication).preferences
    val socksEnabled by prefs.socks5Enabled.collectAsState(initial = false)
    val httpPort by prefs.httpProxyPort.collectAsState(initial = AppPreferences.DEFAULT_HTTP_PORT)
    val socksPort by prefs.socks5ProxyPort.collectAsState(initial = AppPreferences.DEFAULT_SOCKS5_PORT)
    val startOnBoot by prefs.startOnBoot.collectAsState(initial = false)
    val watchdogEnabled by prefs.privilegedWatchdogEnabled.collectAsState(initial = false)
    val pauseNodeInDoze by prefs.pauseNodeInDoze.collectAsState(initial = false)

    var httpPortText by remember(httpPort) { mutableStateOf(httpPort.toString()) }
    var socksPortText by remember(socksPort) { mutableStateOf(socksPort.toString()) }
    var watchdogError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = httpPortText,
                    onValueChange = {
                        httpPortText = it
                        it.toIntOrNull()?.let(onHttpPort)
                    },
                    label = { Text("HTTP proxy port") },
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingRow("Enable SOCKS5 proxy") {
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
                    style = MaterialTheme.typography.bodySmall,
                )
                SettingRow("Start on boot") {
                    Switch(checked = startOnBoot, onCheckedChange = onStartOnBoot)
                }
                Text(
                    "Battery: ${if (ignoringBattery) "Unrestricted" else "Optimized"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Unrestricted stops OEM from killing the service. It does not keep the CPU awake.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!ignoringBattery) {
                        TextButton(onClick = onRequestBatteryExemption) {
                            Text("Request exemption")
                        }
                    }
                    TextButton(onClick = onOpenBatterySettings) {
                        Text("Battery settings")
                    }
                }
                SettingRow("Clear dead system proxy (Shizuku/root)") {
                    Switch(
                        checked = watchdogEnabled,
                        onCheckedChange = { enabled ->
                            if (onWatchdogEnabled(enabled)) {
                                watchdogError = null
                            } else {
                                watchdogError = "Shizuku or root required"
                            }
                        },
                    )
                }
                Text(
                    "Screen-on only, once per minute. Force-stop during sleep is cleared on next screen-on. In-process path covers listen death with no extra TCP.",
                    style = MaterialTheme.typography.bodySmall,
                )
                watchdogError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                SettingRow("Pause ZeroTier in Doze") {
                    Switch(checked = pauseNodeInDoze, onCheckedChange = onPauseNodeInDoze)
                }
                Text(
                    "Stops the overlay while the device is idle to silence libzt keepalives. Overlay returns when Doze ends. Off by default.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        control()
    }
}
