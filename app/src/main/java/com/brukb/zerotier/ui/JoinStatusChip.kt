package com.brukb.zerotier.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.brukb.zerotier.connection.JoinStatus

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun JoinStatusChip(status: JoinStatus, modifier: Modifier = Modifier) {
    val role = joinStatusChipRole(status)
    val scheme = MaterialTheme.colorScheme
    val container = when (role) {
        JoinStatusChipRole.SUCCESS -> scheme.primaryContainer
        JoinStatusChipRole.NEUTRAL -> scheme.tertiaryContainer
        JoinStatusChipRole.ERROR -> scheme.errorContainer
    }
    val labelColor = when (role) {
        JoinStatusChipRole.SUCCESS -> scheme.onPrimaryContainer
        JoinStatusChipRole.NEUTRAL -> scheme.onTertiaryContainer
        JoinStatusChipRole.ERROR -> scheme.onErrorContainer
    }
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = { Text(stringResource(joinStatusLabelRes(status))) },
        leadingIcon = if (status == JoinStatus.JOINING ||
            status == JoinStatus.REQUESTING_CONFIG
        ) {
            {
                LoadingIndicator(modifier = Modifier.size(16.dp))
            }
        } else {
            null
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = labelColor,
            disabledLeadingIconContentColor = labelColor,
        ),
    )
}
