package com.roomie.app.ui.screens.swipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.roomie.app.data.media.MediaGroup
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.ui.theme.CardShape
import java.util.concurrent.TimeUnit

@Composable
fun SwipeCard(
    group: MediaGroup,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape),
    ) {
        AsyncImage(
            // Requesting the source's own resolution instead of letting Coil downsample to this
            // card's on-screen size — otherwise the long-press peek zoom just upscales an
            // already-shrunk bitmap and looks like a blown-up thumbnail instead of a sharp photo.
            model = ImageRequest.Builder(LocalContext.current)
                .data(group.cover.uri)
                .size(Size.ORIGINAL)
                .build(),
            contentDescription = group.cover.displayName,
            // The card is already sized to this item's own aspect ratio by the caller, so Fit
            // fills it exactly — showing photos in their native orientation instead of cropping
            // portrait/landscape shots to a fixed card shape.
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        if (group.cover.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = strings.videoContentDescription, tint = Color.White)
            }
            CardBadge(
                text = formatDuration(group.cover.durationMillis),
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun BoxScope.CardBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMillis)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
