package com.brukb.zerotier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brukb.zerotier.R

@Composable
fun GrantSecureSettingsCard(
    shizukuAvailable: Boolean,
    adbCommand: String,
    error: String? = null,
    onShizukuGrant: () -> Unit,
    onCopied: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.grant_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.grant_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = onShizukuGrant, enabled = shizukuAvailable) {
                Text(stringResource(R.string.grant_shizuku))
            }
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            CopyableMonoText(
                value = adbCommand,
                contentDescription = stringResource(R.string.copy_adb),
                onCopied = onCopied,
            )
        }
    }
}
