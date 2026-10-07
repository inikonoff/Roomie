package com.cullect.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * The app's slider: a thin 4dp track (filled part in the accent colour) and a round 24dp thumb with
 * a soft shadow. Material 3's own Slider draws a 16dp-thick track with a gap around the thumb,
 * which reads as a heavy bar next to the rest of the settings screen. Behaviour (drag, tap, focus,
 * accessibility) is still Material's [Slider]; only the thumb and track are replaced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CullectSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    // White on a light theme; a warm light grey on a dark one, so the thumb never glares.
    val thumbColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color.White
    }

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        thumb = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .shadow(elevation = 3.dp, shape = CircleShape)
                    .background(thumbColor, CircleShape)
                    .border(0.5.dp, Color.Black.copy(alpha = 0.12f), CircleShape),
            )
        },
        track = { sliderState ->
            val span = sliderState.valueRange.endInclusive - sliderState.valueRange.start
            val fraction = if (span > 0f) {
                ((sliderState.value - sliderState.valueRange.start) / span).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(inactive),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(active),
                )
            }
        },
    )
}
