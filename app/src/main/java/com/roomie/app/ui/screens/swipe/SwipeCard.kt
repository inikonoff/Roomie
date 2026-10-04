package com.roomie.app.ui.screens.swipe

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.size.Precision
import com.roomie.app.data.media.MediaGroup
import com.roomie.app.ui.components.InlineVideoPlayer
import com.roomie.app.ui.strings.LocalAppStrings
import java.util.concurrent.TimeUnit

/** Long-press zoom never asks for more than this times the on-screen card. A 50 MP original is
 *  ~200 MB of decoded heap and nothing on screen can show it. (3x a 1080x1440 card is ~56 MB.) */
internal const val MAX_PEEK_ZOOM = 3.0f

/** Screen-sized decode key. CardStack's prefetch of stack[2] must write this same string. */
internal fun screenCacheKey(uri: Uri, widthPx: Int, heightPx: Int): String =
    "$uri|screen|${widthPx}x$heightPx"

internal fun zoomCacheKey(uri: Uri, widthPx: Int, heightPx: Int): String =
    "$uri|zoom|${widthPx}x$heightPx"

@Composable
fun SwipeCard(
    group: MediaGroup,
    modifier: Modifier = Modifier,
    isZoomed: Boolean = false,
    showBorder: Boolean = false,
    cornerRadiusDp: Int = 32,
    borderWidthDp: Float = 1f,
    /** Session-only 90-degree turns of the photo (any integer; only the value mod 4 shows). The
     *  caller sizes this card for the turned shape — see [SwipeCardSlot]'s `quarterTurns`. */
    quarterTurns: Int = 0,
) {
    val strings = LocalAppStrings.current
    var isPlayingVideo by remember(group.key) { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadiusDp.dp)

    BoxWithConstraints(
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
        // Centered so a turned image (requiredSize, below) sits on the card's center: requiredSize
        // centers its content within the size it reports to the parent, which is coerced to the
        // card's own bounds, so top-start alignment would leave it off-center by the difference.
        contentAlignment = Alignment.Center,
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        // The photo is always decoded and laid out as itself — a box with its own, unturned aspect
        // ratio — and then turned. A sideways turn makes the card the box's width/height swapped,
        // so the box is the card's dimensions swapped back; turned by 90 degrees it fills the card
        // exactly. Sizing the request for the box keeps the decode matched to what's drawn.
        val turns = Math.floorMod(quarterTurns, 4)
        val sideways = turns % 2 == 1
        val imageWidthPx = if (sideways) heightPx else widthPx
        val imageHeightPx = if (sideways) widthPx else heightPx
        val imageModifier = if (turns == 0) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .requiredSize(
                    width = if (sideways) maxHeight else maxWidth,
                    height = if (sideways) maxWidth else maxHeight,
                )
                .graphicsLayer { rotationZ = 90f * turns }
        }
        val screenKey = screenCacheKey(group.cover.uri, imageWidthPx, imageHeightPx)
        val zoomWidthPx = (imageWidthPx * MAX_PEEK_ZOOM).toInt().coerceAtLeast(1)
        val zoomHeightPx = (imageHeightPx * MAX_PEEK_ZOOM).toInt().coerceAtLeast(1)
        val zoomKey = zoomCacheKey(group.cover.uri, zoomWidthPx, zoomHeightPx)
        // Not state on purpose: a request that changed because this changed would just reload
        // itself. It's only read when the request is rebuilt for another reason (a turn changes
        // the decode size), so the previous bitmap can stand in until the new one is ready
        // instead of the card flashing its bare background.
        val lastShownKey = remember { arrayOfNulls<String>(1) }

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
            // Precision.EXACT makes Coil resize to exactly the requested size at decode time instead
            // of possibly leaving a leftover inSampleSize-rounded bitmap for Compose to scale later.
            // Confirmed on-device this alone does NOT fix the diagonal-edge staircase below — kept
            // anyway since decoding at the exact size we're about to draw is correct regardless.
            // filterQuality = High is the actual fix for the staircase: stock Gallery (View-based,
            // hardware-accelerated ImageView) shows the same photo at the same on-screen scale with
            // a clean diagonal; Compose's default FilterQuality.Low is a single-tap bilinear sample
            // with no mip chain behind it, which aliases a strong minification the way a plain
            // bilinear (as opposed to trilinear/mipmapped) minify always does. High asks Skia for
            // its higher-quality sampling instead.
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(group.cover.uri)
                    .size(imageWidthPx, imageHeightPx)
                    .precision(Precision.EXACT)
                    .allowHardware(false)
                    .memoryCacheKey(screenKey)
                    .placeholderMemoryCacheKey(lastShownKey[0]?.takeIf { it != screenKey })
                    .crossfade(false)
                    .build(),
                contentDescription = group.cover.displayName,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                onSuccess = { lastShownKey[0] = screenKey },
                modifier = imageModifier,
            )
            if (isZoomed && !group.cover.isVideo) {
                SubcomposeAsyncImage(
                    // No Precision.EXACT here, unlike the screen request above: EXACT upscales a
                    // source smaller than the target, and at 3x that would allocate a huge bitmap
                    // for a small image with nothing to show for it.
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
                    filterQuality = FilterQuality.High,
                    modifier = imageModifier,
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
