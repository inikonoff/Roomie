package com.roomie.app.ui.screens.swipe

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import com.roomie.app.data.media.MediaGroup
import com.roomie.app.ui.components.InlineVideoPlayer
import com.roomie.app.ui.strings.LocalAppStrings
import java.util.concurrent.TimeUnit

/** Long-press zoom never asks for more than this times the on-screen card. A 50 MP original is
 *  ~200 MB of decoded heap and nothing on screen can show it. */
internal const val MAX_PEEK_ZOOM = 2.5f

/** Screen-sized decode key. Every role (Warm/Behind/Top) requests the same uri at the same
 *  widthPx/heightPx, so stack[2]'s own decode (as a Warm slot) already sits under the key
 *  Behind/Top will look up later — no separate prefetch needs to agree with this. */
internal fun screenCacheKey(uri: Uri, widthPx: Int, heightPx: Int): String =
    "$uri|screen|${widthPx}x$heightPx"

internal fun zoomCacheKey(uri: Uri, widthPx: Int, heightPx: Int): String =
    "$uri|zoom|${widthPx}x$heightPx"

@Composable
fun SwipeCard(
    group: MediaGroup,
    widthPx: Int,
    heightPx: Int,
    modifier: Modifier = Modifier,
    isZoomed: Boolean = false,
    showBorder: Boolean = false,
    cornerRadiusDp: Int = 32,
    borderWidthDp: Float = 1f,
) {
    val strings = LocalAppStrings.current
    var isPlayingVideo by remember(group.key) { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            // Seam between stacked cards. Drawn for every composed slot (top, next, and the one
            // after that), not only once a card becomes top — inserting Modifier.border on the
            // release frame was a measure pass on the heaviest frame of the swipe.
            .then(
                if (showBorder && borderWidthDp > 0f) {
                    Modifier.border(borderWidthDp.dp, MaterialTheme.colorScheme.background, shape)
                } else {
                    Modifier
                },
            ),
    ) {
        val context = LocalContext.current
        val screenKey = screenCacheKey(group.cover.uri, widthPx, heightPx)
        val zoomWidthPx = (widthPx * MAX_PEEK_ZOOM).toInt().coerceAtLeast(1)
        val zoomHeightPx = (heightPx * MAX_PEEK_ZOOM).toInt().coerceAtLeast(1)
        val zoomKey = zoomCacheKey(group.cover.uri, zoomWidthPx, zoomHeightPx)

        if (group.cover.isVideo && isPlayingVideo) {
            InlineVideoPlayer(
                uri = group.cover.uri,
                modifier = Modifier.fillMaxSize(),
                onClose = { isPlayingVideo = false },
            )
        } else {
            // Screen-sized image stays mounted for the whole life of the card. Zoom used to swap
            // this request for a different size/key; Coil dropped the current bitmap, painted the
            // surface background, then faded the new decode in.
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(group.cover.uri)
                    .size(widthPx, heightPx)
                    .allowHardware(false)
                    .memoryCacheKey(screenKey)
                    .crossfade(false)
                    .build(),
                contentDescription = group.cover.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (isZoomed && !group.cover.isVideo) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(group.cover.uri)
                        .size(zoomWidthPx, zoomHeightPx)
                        .allowHardware(false)
                        .memoryCacheKey(zoomKey)
                        .placeholderMemoryCacheKey(screenKey)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Draw nothing until the sharper bitmap is actually ready. The screen image
                    // underneath keeps showing, scaled by the slot's graphicsLayer. painter.state
                    // is a StateFlow in this Coil version, not a Compose State, so it has to be
                    // collected to both read its current value and recompose when it changes.
                    val zoomState by painter.state.collectAsState()
                    if (zoomState is AsyncImagePainter.State.Success) {
                        SubcomposeAsyncImageContent()
                    }
                }
            }

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
