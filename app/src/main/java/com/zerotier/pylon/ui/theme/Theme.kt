package com.zerotier.pylon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = PylonBlue,
    secondary = PylonAccent,
    background = PylonSurface,
    surface = Color.White,
)

@Composable
fun ZeroTierPylonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
