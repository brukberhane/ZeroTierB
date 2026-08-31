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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    startOnBoot: Boolean,
    watchdogEnabled: Boolean,
    pauseNodeInDoze: Boolean,
    batteryUnrestricted: Boolean,
    linkDebounceSec: Int,
    showGrantHint: Boolean,
    packageName: String,
    nodeId: String?,
    onDismiss: () -> Unit,
    onStartOnBoot: (Boolean) -> Unit,
    onWatchdogEnabled: (Boolean) -> Boolean,
    onPauseNodeInDoze: (Boolean) -> Unit,
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
