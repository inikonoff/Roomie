package com.roomie.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The app is designed around one warm, light palette. Dark theme reuses the same hues at reduced
// contrast rather than a separate design pass, since this is an MVP.
//
// Every ColorScheme role is set explicitly below, even ones nothing currently visibly uses — this
// is the third time an unset role silently fell back to Material3's base (lavender) scheme instead
// of this app's own palette (secondaryContainer on the selected SegmentedButton, surfaceContainer
// on DropdownMenu, now onPrimary on Switch's thumb). Closing every role at once means the next
// component that happens to read an unset one (a dialog, a FAB, a snackbar) doesn't reopen the same
// bug in a fourth place.
private val LightColors = lightColorScheme(
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = Primary,
    surfaceBright = Surface,
    surfaceDim = SurfaceMuted,
    surfaceContainerLowest = Background,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceMuted,
    surfaceContainerHighest = SurfaceMuted,
    outline = Outline,
    outlineVariant = Color(0xFFEDE4D8),

    primary = Primary,
    onPrimary = Color(0xFFFFFBF7),
    primaryContainer = SelectedContainer,
    onPrimaryContainer = OnSelectedContainer,
    inversePrimary = SelectedContainer,

    secondary = Secondary,
    onSecondary = Color(0xFFFFFBF7),
    secondaryContainer = SelectedContainer,
    onSecondaryContainer = OnSelectedContainer,

    tertiary = FolderAction,
    onTertiary = Color(0xFFFFFBF7),
    tertiaryContainer = FolderContainer,
    onTertiaryContainer = Color(0xFF35495C),

    error = SwipeLeftDelete,
    onError = Color(0xFFFFFBF7),
    errorContainer = DeleteContainer,
    onErrorContainer = Color(0xFF6E3B2C),

    inverseSurface = OnSurface,
    inverseOnSurface = Background,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    background = Color(0xFF1C1815),
    onBackground = Color(0xFFF3EBE0),
    surface = Color(0xFF2A241F),
    onSurface = Color(0xFFF3EBE0),
    surfaceVariant = Color(0xFF352D26),
    onSurfaceVariant = Color(0xFFB8AFA3),
    surfaceTint = Primary,
    surfaceBright = Color(0xFF352D26),
    surfaceDim = Color(0xFF1C1815),
    surfaceContainerLowest = Color(0xFF1C1815),
    surfaceContainerLow = Color(0xFF2A241F),
    surfaceContainer = Color(0xFF2A241F),
    surfaceContainerHigh = Color(0xFF352D26),
    surfaceContainerHighest = Color(0xFF352D26),
    outline = Color(0xFF3F372F),
    outlineVariant = Color(0xFF4A4038),

    // primary/secondary/error keep the same lightness as in the light scheme rather than
    // inverting, so their "on" colors stay dark (not light) for the same reason
    // onSecondaryContainer already did before this pass — dark text still reads best on them.
    primary = Primary,
    onPrimary = Color(0xFF2A1712),
    primaryContainer = Color(0xFF4A362C),
    onPrimaryContainer = Color(0xFFF0D0C8),
    inversePrimary = Color(0xFF4A362C),

    secondary = Secondary,
    onSecondary = Color(0xFF1C211C),
    secondaryContainer = Color(0xFF4A362C),
    onSecondaryContainer = Color(0xFFF0D0C8),

    tertiary = FolderAction,
    onTertiary = Color(0xFF14202B),
    tertiaryContainer = Color(0xFF2A3F4E),
    onTertiaryContainer = Color(0xFFD9E3EA),

    error = SwipeLeftDelete,
    onError = Color(0xFF2A1712),
    errorContainer = Color(0xFF5A2E24),
    onErrorContainer = Color(0xFFF0D0C8),

    inverseSurface = Color(0xFFF3EBE0),
    inverseOnSurface = Color(0xFF1C1815),
    scrim = Color.Black,
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
