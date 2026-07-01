package com.tdownload.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

val DarkColors = darkColorScheme(
    primary = ColorPrimary,
    onPrimary = ColorOnPrimary,
    background = ColorBackground,
    surface = ColorSurface,
    surfaceVariant = ColorSurfaceVariant,
    onSurface = ColorTextPrimary,
    onSurfaceVariant = ColorTextSecondary,
    error = ColorError,
    outline = ColorOutline,
)

@Composable
fun TDownloadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography,
        content = content,
    )
}
