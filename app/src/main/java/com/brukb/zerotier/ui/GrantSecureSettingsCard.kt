package com.brukb.zerotier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brukb.zerotier.R

@Composable
fun GrantSecureSettingsCard(
    shizukuAvailable: Boolean,
    adbCommand: String,
    onShizukuGrant: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    adbCommand,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                IconButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(adbCommand))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy_adb))
                }
            }
        }
    }
}
