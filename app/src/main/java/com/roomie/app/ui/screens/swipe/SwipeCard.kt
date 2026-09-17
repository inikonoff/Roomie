package com.roomie.app.ui.screens.swipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.memoryCacheKey
import coil3.request.placeholderMemoryCacheKey
import coil3.size.Size
import com.roomie.app.data.media.MediaGroup
import com.roomie.app.ui.components.InlineVideoPlayer
import com.roomie.app.ui.strings.LocalAppStrings
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/** Noticeably more detail than the screen-sized request, but a decode an order of magnitude
 *  cheaper than the source's own full resolution — see the warm-up LaunchedEffect below. */
private const val ZOOM_WARM_SIZE_PX = 1600

/** Only warm up a card that's been sitting on screen this long — a fast flip through the stack
 *  cancels this (along with the rest of the composition) before it ever fires, so quick browsing
 *  never pays for a warm-up decode it won't use. */
private const val ZOOM_WARM_LINGER_MS = 450L

@Composable
fun SwipeCard(
    group: MediaGroup,
    modifier: Modifier = Modifier,
    isZoomed: Boolean = false,
    showBorder: Boolean = false,
    cornerRadiusDp: Int = 32,
    borderWidthDp: Float = 1f,
) {
    val strings = LocalAppStrings.current
    // Resets to the thumbnail whenever the card changes, so a new photo/video never inherits the
    // previous one's "currently playing" state.
    var isPlayingVideo by remember(group.key) { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            // A thin seam between the top card and whatever's behind it — appears the instant a
            // card becomes top, no fade/thickness animation. colorScheme.background (not a bright
            // accent) reads as a gap between cards, not a decorative frame.
            .then(
                if (showBorder && borderWidthDp > 0f) {
                    Modifier.border(borderWidthDp.dp, MaterialTheme.colorScheme.background, shape)
                } else {
                    Modifier
                },
            ),
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        // Explicit on both branches, not left to Coil's own request-derived key: isZoomed changes
        // the request's size, which changes Coil's computed cache key unpredictably between the
        // two — placeholderMemoryCacheKey below can only find a match if both branches agree on a
        // stable key for "the other" resolution ahead of time.
        val screenCacheKey = "${group.cover.uri}|screen|${widthPx}x$heightPx"
        val originalCacheKey = "${group.cover.uri}|original"

        // Warms a mid-resolution decode for a card that's lingered on screen a while, so a later
        // long-press zoom has *something* better than the screen-sized thumbnail to show as a
        // placeholder while the full original decodes — see ZOOM_WARM_LINGER_MS/ZOOM_WARM_SIZE_PX.
        // Not Size.ORIGINAL: framestats on a real device traced that full-resolution warm-up as the
        // main source of p99 350-500ms frame spikes during ordinary swiping.
        LaunchedEffect(group.cover.uri, isZoomed) {
            if (isZoomed || group.cover.isVideo) return@LaunchedEffect
            delay(ZOOM_WARM_LINGER_MS)
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(group.cover.uri)
                    .size(ZOOM_WARM_SIZE_PX, ZOOM_WARM_SIZE_PX)
                    .allowHardware(false)
                    .memoryCacheKey("${group.cover.uri}|zoomwarm|$ZOOM_WARM_SIZE_PX")
                    .build(),
            )
        }

        if (group.cover.isVideo && isPlayingVideo) {
            InlineVideoPlayer(
                uri = group.cover.uri,
                modifier = Modifier.fillMaxSize(),
                onClose = { isPlayingVideo = false },
            )
        } else {
            val requestBuilder = ImageRequest.Builder(context).data(group.cover.uri).crossfade(true)
            if (isZoomed) {
                // Only while actually zoomed in does the extra detail of the source's own
                // resolution matter — requesting it for every ordinary card was what made rapid
                // swiping feel laggy (a 12+MP photo takes real time to decode). There used to also
                // be a background warm-up of this exact request while the card just sat on screen
                // unzoomed, to make a later long-press instant — framestats on a real device traced
                // that prefetch itself as the main source of p99 frame-time spikes during ordinary
                // swiping, so it's gone; a long-press now decodes on demand again, same as before
                // that warm-up existed. A single rare zoom's decode delay beats system-wide jank on
                // every swipe.
                // memoryCacheKey/placeholderMemoryCacheKey: keep showing the screen-sized decode
                // (already on screen a moment ago) while the full original loads, instead of
                // AsyncImage falling into Loading and showing the bare card background — the two
                // branches use different request sizes, so without an explicit shared key Coil has
                // no way to know the screen-sized decode is a usable placeholder for this one.
                requestBuilder
                    .size(Size.ORIGINAL)
                    .memoryCacheKey(originalCacheKey)
                    .placeholderMemoryCacheKey(screenCacheKey)
            } else {
                // Same reasoning as MediaThumbnail's grid tiles: cards cycle through quickly during
                // a swipe session, and a HARDWARE bitmap's GPU-buffer allocate/free cost (via
                // gralloc IPC) is paid on every single one of them.
                requestBuilder
                    .size(widthPx, heightPx)
                    .allowHardware(false)
                    .memoryCacheKey(screenCacheKey)
                    // Symmetric with the isZoomed branch above: zooming back out shouldn't flash
                    // blank while this decodes if the full-resolution version is already cached.
                    .placeholderMemoryCacheKey(originalCacheKey)
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
