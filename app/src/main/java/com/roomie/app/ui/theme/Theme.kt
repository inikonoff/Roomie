package com.roomie.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The app is designed around one warm, light palette. Dark theme reuses the same
// hues at reduced contrast rather than a separate design pass, since this is an MVP.
private val LightColors = lightColorScheme(
    background = Background,
    surface = Surface,
    primary = Primary,
    secondary = Secondary,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceVariant = SurfaceMuted,
    outline = Outline,
    secondaryContainer = SelectedContainer,
    onSecondaryContainer = OnSelectedContainer,
    error = SwipeLeftDelete,
)

private val DarkColors = darkColorScheme(
    background = Color(0xFF1C1815),
    surface = Color(0xFF2A241F),
    primary = Primary,
    secondary = Secondary,
    onBackground = Color(0xFFF3EBE0),
    onSurface = Color(0xFFF3EBE0),
    onSurfaceVariant = Color(0xFFB8AFA3),
    surfaceVariant = Color(0xFF352D26),
    outline = Color(0xFF3F372F),
    secondaryContainer = Color(0xFF4A362C),
    onSecondaryContainer = Color(0xFFF0D0C8),
    error = SwipeLeftDelete,
)

@Composable
fun RoomieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        shapes = RoomieShapes,
        content = content,
    )
}
