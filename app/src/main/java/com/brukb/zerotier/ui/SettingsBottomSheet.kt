package com.brukb.zerotier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.brukb.zerotier.data.AppPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    startOnBoot: Boolean,
    watchdogEnabled: Boolean,
    pauseNodeInDoze: Boolean,
    reinitNodeOnDozeResume: Boolean,
    skipUplinkDnsProbe: Boolean,
    uplinkDnsHeal: Boolean,
    preferWifiDns: Boolean,
    dnsFailOpen: Boolean,
    dnsFallbackServers: List<String>,
    verboseFileLog: Boolean,
    batteryUnrestricted: Boolean,
    linkDebounceSec: Int,
    showGrantHint: Boolean,
    packageName: String,
    nodeId: String?,
    onDismiss: () -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
    onWatchdogEnabled: (Boolean) -> Boolean,
    onPauseNodeInDoze: (Boolean) -> Unit,
    onReinitNodeOnDozeResume: (Boolean) -> Unit,
    onSkipUplinkDnsProbe: (Boolean) -> Unit,
    onUplinkDnsHeal: (Boolean) -> Unit,
    onPreferWifiDns: (Boolean) -> Unit,
    onDnsFailOpen: (Boolean) -> Unit,
    onDnsFallbackServers: (List<String>) -> Unit,
    onVerboseFileLog: (Boolean) -> Unit,
    onExportLogs: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenLinks: () -> Unit,
    onGrantHint: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var watchdogError by remember { mutableStateOf<String?>(null) }
    val shizukuRequired = stringResource(R.string.watchdog_needs_shizuku)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.copied)
    var fallbackDraft by remember { mutableStateOf("") }
    var fallbackError by remember { mutableStateOf<String?>(null) }
    val maxFallback = AppPreferences.MAX_DNS_FALLBACK_SERVERS
    val fallbackMaxMessage = stringResource(R.string.dns_fallback_max, maxFallback)
    val fallbackInvalidMessage = stringResource(R.string.dns_fallback_invalid)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                )

                SettingsSection(title = stringResource(R.string.settings_section_reliability)) {
                    SettingsSwitchRow(
                        label = stringResource(R.string.start_on_boot),
                        checked = startOnBoot,
                        onCheckedChange = onStartOnBoot,
                    )
                    Text(
                        stringResource(R.string.start_on_boot_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsSwitchRow(
                        label = stringResource(R.string.watchdog_title),
                        checked = watchdogEnabled,
                        onCheckedChange = { enabled ->
                            if (onWatchdogEnabled(enabled)) {
                                watchdogError = null
                            } else {
                                watchdogError = shizukuRequired
                            }
                        },
                    )
                    Text(
                        stringResource(R.string.watchdog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    watchdogError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    SettingsSwitchRow(
                        label = stringResource(R.string.pause_doze_title),
                        checked = pauseNodeInDoze,
                        onCheckedChange = onPauseNodeInDoze,
                    )
                    Text(
                        stringResource(R.string.pause_doze_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pauseNodeInDoze) {
                        SettingsSwitchRow(
                            label = stringResource(R.string.doze_reinit_title),
                            checked = reinitNodeOnDozeResume,
                            onCheckedChange = onReinitNodeOnDozeResume,
                        )
                        Text(
                            stringResource(R.string.doze_reinit_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showGrantHint) {
                        TextButton(onClick = onGrantHint) {
                            Text(stringResource(R.string.settings_grant_hint))
                        }
                    }
                }

                SettingsSection(title = stringResource(R.string.settings_section_battery)) {
                    Text(
                        stringResource(
                            if (batteryUnrestricted) {
                                R.string.battery_opt_status_ok
                            } else {
                                R.string.battery_opt_status_restricted
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (batteryUnrestricted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    TextButton(onClick = onRequestBatteryExemption) {
                        Text(stringResource(R.string.battery_opt_request))
                    }
                    TextButton(onClick = onOpenBatterySettings) {
                        Text(stringResource(R.string.battery_opt_list))
                    }
                }

                SettingsSection(title = stringResource(R.string.settings_section_links)) {
                    TextButton(onClick = onOpenLinks) {
                        Text(stringResource(R.string.settings_manage_links))
                    }
                }

                SettingsSection(title = stringResource(R.string.settings_section_advanced)) {
                    SystemProxyDnsPolicyControls(
                        skipUplinkDnsProbe = skipUplinkDnsProbe,
                        healEnabled = uplinkDnsHeal,
                        preferWifiDns = preferWifiDns,
                        onSkip = onSkipUplinkDnsProbe,
                        onHeal = onUplinkDnsHeal,
                        onPreferWifi = onPreferWifiDns,
                        showScopeHint = true,
                    )
                    SettingsSwitchRow(
                        label = stringResource(R.string.dns_fail_open_title),
                        checked = dnsFailOpen,
                        onCheckedChange = onDnsFailOpen,
                    )
                    Text(
                        stringResource(R.string.dns_fail_open_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.dns_fallback_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.dns_fallback_hint, maxFallback),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = fallbackDraft,
                            onValueChange = {
                                fallbackDraft = it
                                fallbackError = null
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text(stringResource(R.string.dns_fallback_title)) },
                            isError = fallbackError != null,
                            supportingText = fallbackError?.let { { Text(it) } },
                        )
                        TextButton(
                            onClick = {
                                val trimmed = fallbackDraft.trim()
                                when {
                                    dnsFallbackServers.size >= maxFallback -> {
                                        fallbackError = fallbackMaxMessage
                                    }
                                    AppPreferences.sanitizeDnsFallbackServers(listOf(trimmed)).isEmpty() -> {
                                        fallbackError = fallbackInvalidMessage
                                    }
                                    dnsFallbackServers.contains(trimmed) -> {
                                        fallbackDraft = ""
                                    }
                                    else -> {
                                        onDnsFallbackServers(dnsFallbackServers + trimmed)
                                        fallbackDraft = ""
                                        fallbackError = null
                                    }
                                }
                            },
                            enabled = fallbackDraft.isNotBlank() && dnsFallbackServers.size < maxFallback,
                        ) {
                            Text(stringResource(R.string.dns_fallback_add))
                        }
                    }
                    dnsFallbackServers.forEach { server ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(server, fontFamily = FontFamily.Monospace)
                            TextButton(
                                onClick = {
                                    onDnsFallbackServers(dnsFallbackServers.filter { it != server })
                                },
                            ) {
                                Text(stringResource(R.string.dns_fallback_remove))
                            }
                        }
                    }
                    SettingsSwitchRow(
                        label = stringResource(R.string.verbose_file_log_title),
                        checked = verboseFileLog,
                        onCheckedChange = onVerboseFileLog,
                    )
                    Text(
                        stringResource(R.string.verbose_file_log_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onExportLogs) {
                        Text(stringResource(R.string.export_logs))
                    }
                    Text(
                        stringResource(R.string.settings_debounce_summary, linkDebounceSec),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onOpenLinks) {
                        Text(stringResource(R.string.settings_edit_in_links))
                    }
                    Text(
                        stringResource(R.string.settings_debug_package, packageName),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    nodeId?.let {
                        CopyableMonoText(
                            value = it,
                            display = stringResource(R.string.settings_debug_node_id, it),
                            contentDescription = stringResource(R.string.copy_node_id),
                            onCopied = {
                                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                            },
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun SystemProxyDnsPolicyControls(
    skipUplinkDnsProbe: Boolean,
    healEnabled: Boolean,
    preferWifiDns: Boolean,
    onSkip: (Boolean) -> Unit,
    onHeal: (Boolean) -> Unit,
    onPreferWifi: (Boolean) -> Unit,
    showScopeHint: Boolean,
) {
    SettingsSwitchRow(
        label = stringResource(R.string.dns_skip_probe_title),
        checked = skipUplinkDnsProbe,
        onCheckedChange = onSkip,
    )
    Text(
        stringResource(R.string.dns_skip_probe_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (showScopeHint) {
        Text(
            stringResource(R.string.dns_proxy_scope_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (skipUplinkDnsProbe) {
        SettingsSwitchRow(
            label = stringResource(R.string.dns_heal_title),
            checked = healEnabled,
            onCheckedChange = onHeal,
        )
        Text(
            stringResource(R.string.dns_heal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSwitchRow(
            label = stringResource(R.string.dns_prefer_wifi_title),
            checked = preferWifiDns,
            onCheckedChange = onPreferWifi,
            enabled = healEnabled,
        )
        Text(
            stringResource(R.string.dns_prefer_wifi_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
