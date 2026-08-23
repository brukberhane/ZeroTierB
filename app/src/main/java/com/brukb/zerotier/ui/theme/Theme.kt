package com.brukb.zerotier.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ZerotierBBlue,
    secondary = ZerotierBAccent,
    background = ZerotierBSurface,
    surface = Color.White,
)

@Composable
fun ZerotierBTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
