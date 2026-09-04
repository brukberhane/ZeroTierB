package com.brukb.zerotier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brukb.zerotier.R
import com.brukb.zerotier.data.AppPreferences
import com.brukb.zerotier.data.model.LinkKind
import com.brukb.zerotier.data.model.LinkMode
import com.brukb.zerotier.data.model.LinkProfile
import com.brukb.zerotier.data.model.UplinkDnsPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinksScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsState()
    val wifi = ui.linkProfiles.filter { it.kind == LinkKind.WIFI }
    val sims = ui.linkProfiles.filter { it.kind == LinkKind.MOBILE }
    val other = ui.linkProfiles.filter { it.kind == LinkKind.OTHER }
    val canSave = canSaveSsid(ui.lastLink)
    var debounceMs by remember(ui.linkDebounceMs) { mutableFloatStateOf(ui.linkDebounceMs.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.links_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.current_link), style = MaterialTheme.typography.titleSmall)
                        Text(formatLinkLine(ui.lastLink))
                        Button(
                            onClick = viewModel::saveCurrentSsid,
                            enabled = canSave,
                        ) {
                            Text(stringResource(R.string.save_ssid))
                        }
                    }
                }
                Text(stringResource(R.string.wifi_section), style = MaterialTheme.typography.titleSmall)
                if (wifi.isEmpty()) {
                    Text(stringResource(R.string.no_saved_wifi), style = MaterialTheme.typography.bodySmall)
                }
                wifi.forEach { profile ->
                    LinkProfileRow(
                        title = profile.ssid ?: profile.label.ifBlank { profile.id },
                        profile = profile,
                        onMode = { viewModel.setLinkMode(profile, it) },
                        onDnsPolicy = { skip, heal, wifi ->
                            viewModel.setLinkDnsPolicy(profile, skip, heal, wifi)
                        },
                        onDelete = { viewModel.deleteWifiProfile(profile) },
                    )
                }
                Text(stringResource(R.string.sim_section), style = MaterialTheme.typography.titleSmall)
                sims.forEach { profile ->
                    LinkProfileRow(
                        title = profile.label.ifBlank { "SIM ${profile.subscriptionId}" },
                        profile = profile,
                        onMode = { viewModel.setLinkMode(profile, it) },
                        onDnsPolicy = { skip, heal, wifi ->
                            viewModel.setLinkDnsPolicy(profile, skip, heal, wifi)
                        },
                        onDelete = null,
                    )
                }
                Text(stringResource(R.string.other_section), style = MaterialTheme.typography.titleSmall)
                other.forEach { profile ->
                    LinkProfileRow(
                        title = profile.label.ifBlank { stringResource(R.string.other_section) },
                        profile = profile,
                        onMode = { viewModel.setLinkMode(profile, it) },
                        onDnsPolicy = { skip, heal, wifi ->
                            viewModel.setLinkDnsPolicy(profile, skip, heal, wifi)
                        },
                        onDelete = null,
                    )
                }
                Text(
                    stringResource(R.string.debounce_label, (debounceMs / 1000).toInt()),
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = debounceMs,
                    onValueChange = { debounceMs = it },
                    onValueChangeFinished = { viewModel.setLinkDebounceMs(debounceMs.toInt()) },
                    valueRange = AppPreferences.MIN_LINK_DEBOUNCE_MS.toFloat()..
                        AppPreferences.MAX_LINK_DEBOUNCE_MS.toFloat(),
                    steps = 11,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkProfileRow(
    title: String,
    profile: LinkProfile,
    onMode: (LinkMode) -> Unit,
    onDnsPolicy: (skip: Boolean, heal: Boolean, preferWifi: Boolean) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val modes = LinkMode.entries
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_wifi))
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, m ->
                    SegmentedButton(
                        selected = profile.mode == m,
                        onClick = { onMode(m) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) {
                        Text(m.name)
                    }
                }
            }
            if (profile.mode == LinkMode.PROXY) {
                Text(
                    stringResource(R.string.dns_link_proxy_options),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SystemProxyDnsPolicyControls(
                    skipUplinkDnsProbe = profile.skipUplinkDnsProbe,
                    healEnabled = profile.uplinkDnsHealEnabled,
                    preferWifiDns = profile.uplinkDnsPreference == UplinkDnsPreference.WIFI_FIRST,
                    onSkip = {
                        onDnsPolicy(
                            it,
                            profile.uplinkDnsHealEnabled,
                            profile.uplinkDnsPreference == UplinkDnsPreference.WIFI_FIRST,
                        )
                    },
                    onHeal = {
                        onDnsPolicy(
                            profile.skipUplinkDnsProbe,
                            it,
                            profile.uplinkDnsPreference == UplinkDnsPreference.WIFI_FIRST,
                        )
                    },
                    onPreferWifi = {
                        onDnsPolicy(profile.skipUplinkDnsProbe, profile.uplinkDnsHealEnabled, it)
                    },
                    showScopeHint = false,
                )
            }
        }
    }
}
