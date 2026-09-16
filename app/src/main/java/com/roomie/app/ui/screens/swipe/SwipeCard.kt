package com.roomie.app.ui.screens.swipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.roomie.app.data.media.MediaGroup
import com.roomie.app.ui.components.InlineVideoPlayer
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.ui.theme.CardShape
import java.util.concurrent.TimeUnit

@Composable
fun SwipeCard(
    group: MediaGroup,
    modifier: Modifier = Modifier,
    isZoomed: Boolean = false,
) {
    val strings = LocalAppStrings.current
    // Resets to the thumbnail whenever the card changes, so a new photo/video never inherits the
    // previous one's "currently playing" state.
    var isPlayingVideo by remember(group.key) { mutableStateOf(false) }

    // The normal (unzoomed) view only requests a screen-sized image — fast, but it means the
    // source's own full resolution isn't in Coil's cache yet when a long-press zoom actually asks
    // for it, so the first zoom on a freshly-arrived card visibly waits on a decode. Warm that
    // cache in the background, once, while the card is just sitting there unzoomed and on screen —
    // by the time a real long-press happens it's very likely already done. Fire-and-forget: if the
    // card gets swiped away before this finishes, the LaunchedEffect (and the coroutine it started)
    // is simply cancelled along with it, no cleanup needed.
    val prefetchContext = LocalContext.current
    LaunchedEffect(group.cover.uri, isZoomed) {
        if (!isZoomed && !group.cover.isVideo) {
            prefetchContext.imageLoader.enqueue(
                ImageRequest.Builder(prefetchContext)
                    .data(group.cover.uri)
                    .size(Size.ORIGINAL)
                    .build(),
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape),
    ) {
        if (group.cover.isVideo && isPlayingVideo) {
            InlineVideoPlayer(
                uri = group.cover.uri,
                modifier = Modifier.fillMaxSize(),
                onClose = { isPlayingVideo = false },
            )
        } else {
            val context = LocalContext.current
            val density = LocalDensity.current
            val requestBuilder = ImageRequest.Builder(context).data(group.cover.uri)
            if (isZoomed) {
                // Only while actually zoomed in does the extra detail of the source's own
                // resolution matter — requesting it for every ordinary card was what made rapid
                // swiping feel laggy (a 12+MP photo takes real time to decode).
                requestBuilder.size(Size.ORIGINAL)
            } else {
                val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
                val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
                requestBuilder.size(widthPx, heightPx)
            }
            AsyncImage(
                model = requestBuilder.build(),
                contentDescription = group.cover.displayName,
                // The card is already sized to this item's own aspect ratio by the caller, so Fit
                // fills it exactly — showing photos in their native orientation instead of
                // cropping portrait/landscape shots to a fixed card shape.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            if (group.cover.isVideo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { isPlayingVideo = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = strings.playVideo,
                        tint = Color.White,
                    )
                }
                CardBadge(
                    text = formatDuration(group.cover.durationMillis),
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
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
