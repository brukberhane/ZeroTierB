package com.brukb.zerotier.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CopyableMonoText(
    value: String,
    contentDescription: String,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
    display: String = value,
) {
    val clipboard = LocalClipboardManager.current
    val copy = {
        clipboard.setText(AnnotatedString(value))
        onCopied()
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            display,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = {}, onLongClick = copy),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        IconButton(onClick = copy) {
            Icon(Icons.Default.ContentCopy, contentDescription = contentDescription)
        }
    }
}
