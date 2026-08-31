@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.brukb.zerotier.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable

/** Compile-only: proves Expressive types exist. Not used by MainScreen. */
@Composable
internal fun ExpressiveApiSmoke(content: @Composable () -> Unit) {
    MaterialExpressiveTheme {
        LoadingIndicator()
        content()
    }
}
