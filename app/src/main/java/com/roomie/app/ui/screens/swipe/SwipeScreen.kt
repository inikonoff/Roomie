package com.roomie.app.ui.screens.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import com.roomie.app.data.media.MediaGroup
import com.roomie.app.data.settings.CardAnimationStyle
import com.roomie.app.ui.strings.AppStrings
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.ui.theme.SwipeLeftDelete
import com.roomie.app.ui.theme.SwipePostpone
import com.roomie.app.ui.theme.SwipeRightKeep
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot

/** While a card sits "behind" the top one, it's rendered at reduced opacity — full-opacity would
 *  otherwise show a hard, fully-formed duplicate photo peeking out around the top card whenever
 *  the two have different aspect ratios (e.g. a portrait photo behind a landscape one). It fades
 *  up to full opacity over [ENTRANCE_FADE_MS] once promoted to the top. */
private const val BEHIND_CARD_ALPHA = 0.45f
private const val ENTRANCE_FADE_MS = 200

private const val SWIPE_THRESHOLD_DP = 120f
private const val MAX_PEEK_ZOOM = 2.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(
    viewModel: SwipeSessionViewModel,
    onBack: () -> Unit,
    onStackExhausted: () -> Unit,
    onLimitReached: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalAppStrings.current

    LaunchedEffect(uiState.hasReachedLimit) {
        if (uiState.hasReachedLimit) onLimitReached()
    }

    LaunchedEffect(uiState.isStackExhausted, uiState.isLoading) {
        if (!uiState.isLoading && uiState.isStackExhausted) onStackExhausted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.folderName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        if (uiState.deletedCount > 0) {
                            BadgedBox(badge = { Badge { Text(uiState.deletedCount.toString()) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = strings.reviewTrash)
                            }
                        } else {
                            Icon(Icons.Filled.Delete, contentDescription = strings.reviewTrash)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.stack.isNotEmpty()) {
                GamifiedProgressBar(
                    deleted = uiState.deletedCount,
                    kept = uiState.keptCount,
                    postponed = uiState.postponedCount,
                    total = uiState.totalCount,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    strings.counterOfTotal(uiState.currentPosition, uiState.totalCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }

            if (uiState.monetizationEnabled) {
                SwipeLimitIndicator(
                    current = uiState.sessionSwipeCount,
                    limit = uiState.freeSwipeLimit,
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                when {
                    uiState.isLoading -> CircularProgressIndicator()
                    uiState.stack.isEmpty() -> Text(
                        strings.folderIsClean,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> CardStack(
                        stack = uiState.stack,
                        animationStyle = uiState.cardAnimationStyle,
                        onSwiped = viewModel::swipe,
                    )
                }
            }

            BottomActionBar(strings = strings, canUndo = uiState.canUndo, onUndo = viewModel::undo)
        }
    }
}

/** Small, secondary indicator for the free-swipe monetization cap — only shown when that cap is
 *  actually in effect, so it's never confused with (or crowding out) the [GamifiedProgressBar]
 *  above, which is what's actually interesting to look at while cleaning up a folder. */
@Composable
private fun SwipeLimitIndicator(current: Int, limit: Int) {
    if (limit <= 0) return
    LinearProgressIndicator(
        progress = { (current.toFloat() / limit).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * Replaces a plain "swipes used" bar with something that actually reflects what's happening to
 * this folder: two halves grow outward from a center line as you swipe — deleted to the left,
 * kept (including moved/browsed-past) and postponed to the right — so at a glance you can see the
 * split without reading any numbers.
 */
@Composable
private fun GamifiedProgressBar(
    deleted: Int,
    kept: Int,
    postponed: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    if (total <= 0) return
    val barHeight = 8.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .clip(RoundedCornerShape(barHeight / 2))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val halfWidth = maxWidth / 2
        val deletedWidth = halfWidth * (deleted.toFloat() / total).coerceIn(0f, 1f)
        val keptWidth = halfWidth * (kept.toFloat() / total).coerceIn(0f, 1f)
        val postponedWidth = halfWidth * (postponed.toFloat() / total).coerceIn(0f, 1f)

        // Deleted: hugs the center line, growing to the left.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = halfWidth - deletedWidth)
                .width(deletedWidth)
                .fillMaxHeight()
                .background(SwipeLeftDelete),
        )
        // Kept: hugs the center line, growing to the right.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = halfWidth)
                .width(keptWidth)
                .fillMaxHeight()
                .background(SwipeRightKeep),
        )
        // Postponed: continues right after kept.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = halfWidth + keptWidth)
                .width(postponedWidth)
                .fillMaxHeight()
                .background(SwipePostpone),
        )
    }
}

@Composable
private fun BottomActionBar(strings: AppStrings, canUndo: Boolean, onUndo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        ExtendedFloatingActionButton(
            onClick = { if (canUndo) onUndo() },
            icon = {
                Icon(
                    Icons.Filled.Undo,
                    contentDescription = null,
                    tint = if (canUndo) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            },
            text = {
                Text(
                    strings.undo,
                    color = if (canUndo) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            },
        )
    }
}

/** The card that just left the stack, still flying off-screen on its own timeline so the newly
 *  promoted top card underneath is interactive immediately instead of waiting for this to finish. */
private data class ExitingCardState(
    val group: MediaGroup,
    val startOffset: Offset,
    val direction: SwipeDirection,
)

/** Fits a card of [ratio] (width/height) inside a [maxWidth] x [maxHeight] box, like
 *  [androidx.compose.ui.layout.ContentScale.Fit] but sizing the composable itself rather than
 *  its content — so differently-oriented photos each get their own natural size on screen. */
private fun fitSize(ratio: Float, maxWidth: Dp, maxHeight: Dp): Pair<Dp, Dp> {
    val containerRatio = maxWidth / maxHeight
    return if (containerRatio > ratio) (maxHeight * ratio) to maxHeight else maxWidth to (maxWidth / ratio)
}

@Composable
private fun CardStack(
    stack: List<MediaGroup>,
    animationStyle: CardAnimationStyle,
    onSwiped: (SwipeDirection) -> Unit,
) {
    var exiting by remember { mutableStateOf<ExitingCardState?>(null) }

    val top = stack.getOrNull(0)
    val behind = stack.getOrNull(1)
    val prefetch = stack.getOrNull(2)

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Only stack[0] and stack[1] are actually composed (below) — by the time a fast swiper
        // reaches stack[2] it would otherwise not have started decoding at all. Warming Coil's
        // cache for it one card early removes that gap without paying for a third on-screen card.
        val context = LocalContext.current
        val density = LocalDensity.current
        LaunchedEffect(prefetch?.key, maxWidth, maxHeight) {
            val group = prefetch ?: return@LaunchedEffect
            val (w, h) = fitSize(group.cover.aspectRatio, maxWidth, maxHeight)
            val widthPx = with(density) { w.roundToPx() }.coerceAtLeast(1)
            val heightPx = with(density) { h.roundToPx() }.coerceAtLeast(1)
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(group.cover.uri)
                    .size(widthPx, heightPx)
                    .build(),
            )
        }

        if (behind != null) {
            // Rendered at the exact same size/opacity it will have once promoted to the top spot
            // (no scale-down/dim "peek" look) — anything different between the two would show up
            // as a pop/jump the instant the card above it is swiped away.
            val (w, h) = fitSize(behind.cover.aspectRatio, maxWidth, maxHeight)
            SwipeCard(
                group = behind,
                modifier = Modifier.size(w, h).alpha(BEHIND_CARD_ALPHA),
            )
        }

        if (top != null) {
            val (w, h) = fitSize(top.cover.aspectRatio, maxWidth, maxHeight)
            DraggableCard(
                group = top,
                cardWidth = w,
                cardHeight = h,
                animationStyle = animationStyle,
                onSwiped = { direction, releaseOffset ->
                    exiting = ExitingCardState(
                        group = top,
                        startOffset = releaseOffset,
                        direction = direction,
                    )
                    onSwiped(direction)
                },
            )
        }

        exiting?.let { ex ->
            val (w, h) = fitSize(ex.group.cover.aspectRatio, maxWidth, maxHeight)
            ExitingCard(
                state = ex,
                cardWidth = w,
                cardHeight = h,
                animationStyle = animationStyle,
                onFinished = { exiting = null },
            )
        }
    }
}

/**
 * Applies the user's chosen swipe-card look for the current drag/fling [offset]: Classic rotates
 * like a card pivoting on a table, Fade and Shrink both drop the rotation and instead ease out via
 * opacity or size as the card travels toward (and past) the fling distance.
 */
private fun GraphicsLayerScope.applySwipeStyle(
    style: CardAnimationStyle,
    offset: Offset,
    thresholdPx: Float,
    baseScale: Float,
    entranceAlpha: Float = 1f,
) {
    val travelled = (hypot(offset.x, offset.y) / FLING_DISTANCE).coerceIn(0f, 1f)
    when (style) {
        CardAnimationStyle.CLASSIC -> {
            rotationZ = (offset.x / thresholdPx) * 12f
            alpha = entranceAlpha
            scaleX = baseScale
            scaleY = baseScale
        }
        CardAnimationStyle.FADE -> {
            rotationZ = 0f
            alpha = (1f - travelled) * entranceAlpha
            scaleX = baseScale
            scaleY = baseScale
        }
        CardAnimationStyle.SHRINK -> {
            rotationZ = 0f
            alpha = entranceAlpha
            val shrink = 1f - travelled * 0.4f
            scaleX = baseScale * shrink
            scaleY = baseScale * shrink
        }
    }
}

private val SWIPE_SPRING = spring<Offset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessLow,
)

private val ZOOM_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

private val ZOOM_PAN_SPRING = spring<Offset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

private const val FLING_DISTANCE = 1600f

private fun flingTarget(direction: SwipeDirection, current: Offset): Offset = when (direction) {
    SwipeDirection.RIGHT -> Offset(FLING_DISTANCE, current.y)
    SwipeDirection.LEFT -> Offset(-FLING_DISTANCE, current.y)
    SwipeDirection.DOWN -> Offset(current.x, FLING_DISTANCE)
    SwipeDirection.UP -> Offset(current.x, -FLING_DISTANCE)
}

@Composable
private fun ExitingCard(
    state: ExitingCardState,
    cardWidth: Dp,
    cardHeight: Dp,
    animationStyle: CardAnimationStyle,
    onFinished: () -> Unit,
) {
    val offset = remember(state) { Animatable(state.startOffset, Offset.VectorConverter) }
    val thresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD_DP.dp.toPx() }

    LaunchedEffect(state) {
        offset.animateTo(flingTarget(state.direction, state.startOffset), SWIPE_SPRING)
        onFinished()
    }

    SwipeCard(
        group = state.group,
        modifier = Modifier
            .size(cardWidth, cardHeight)
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                applySwipeStyle(animationStyle, offset.value, thresholdPx, baseScale = 1f)
            },
    )
}

@Composable
private fun DraggableCard(
    group: MediaGroup,
    cardWidth: Dp,
    cardHeight: Dp,
    animationStyle: CardAnimationStyle,
    onSwiped: (SwipeDirection, Offset) -> Unit,
) {
    val offset = remember(group.key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scale = remember(group.key) { Animatable(1f) }
    val zoomPan = remember(group.key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var zoomOrigin by remember(group.key) { mutableStateOf(TransformOrigin.Center) }
    // Every newly-promoted top card starts at BEHIND_CARD_ALPHA (matching how it was just
    // rendered as the "behind" card) and eases up to fully opaque, instead of snapping instantly
    // — that snap is what made a mismatched-aspect-ratio neighbor look like it was "sticking out".
    val entranceAlpha = remember(group.key) { Animatable(BEHIND_CARD_ALPHA) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pastThreshold by remember(group.key) { mutableStateOf(false) }
    // While zoomed, SwipeCard switches to requesting the source's full resolution instead of a
    // screen-sized one — worth the extra decode time only for this deliberate, held-down peek.
    var isZoomed by remember(group.key) { mutableStateOf(false) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val cardWidthPx = with(density) { cardWidth.toPx() }
    val cardHeightPx = with(density) { cardHeight.toPx() }

    LaunchedEffect(group.key) {
        entranceAlpha.animateTo(1f, tween(ENTRANCE_FADE_MS))
    }

    SwipeCard(
        group = group,
        isZoomed = isZoomed,
        modifier = Modifier
            .size(cardWidth, cardHeight)
            .graphicsLayer {
                transformOrigin = zoomOrigin
                translationX = offset.value.x + zoomPan.value.x
                translationY = offset.value.y + zoomPan.value.y
                applySwipeStyle(
                    animationStyle,
                    offset.value,
                    thresholdPx,
                    baseScale = scale.value,
                    entranceAlpha = entranceAlpha.value,
                )
            }
            .pointerInput(group.key) {
                detectSwipeOrLongPressZoom(
                    onDrag = { dragAmount ->
                        val newValue = offset.value + dragAmount
                        scope.launch { offset.snapTo(newValue) }
                        val crossed = abs(newValue.x) > thresholdPx || abs(newValue.y) > thresholdPx
                        if (crossed != pastThreshold) {
                            pastThreshold = crossed
                            if (crossed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDragEnd = {
                        val current = offset.value
                        val horizontalCrossed = abs(current.x) > thresholdPx
                        val verticalCrossed = abs(current.y) > thresholdPx
                        val direction = when {
                            horizontalCrossed && abs(current.x) >= abs(current.y) ->
                                if (current.x > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                            verticalCrossed -> if (current.y > 0) SwipeDirection.DOWN else SwipeDirection.UP
                            else -> null
                        }
                        if (direction != null) {
                            // Hand off to the caller immediately — advancing to the next card
                            // doesn't wait on this card's own fly-out animation, which continues
                            // independently as an overlay (see ExitingCard).
                            onSwiped(direction, current)
                        } else {
                            scope.launch { offset.animateTo(Offset.Zero, SWIPE_SPRING) }
                        }
                    },
                    onZoomStart = { origin ->
                        zoomOrigin = origin
                        isZoomed = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { scale.animateTo(MAX_PEEK_ZOOM, ZOOM_SPRING) }
                    },
                    onZoomPan = { delta ->
                        val maxPanX = cardWidthPx * (scale.value - 1f) / 2f
                        val maxPanY = cardHeightPx * (scale.value - 1f) / 2f
                        val newPan = zoomPan.value + delta
                        scope.launch {
                            zoomPan.snapTo(
                                Offset(
                                    newPan.x.coerceIn(-maxPanX, maxPanX),
                                    newPan.y.coerceIn(-maxPanY, maxPanY),
                                ),
                            )
                        }
                    },
                    onZoomEnd = {
                        // A quick peek, not a decision: as soon as the finger lifts, the photo
                        // snaps straight back to its normal size and position. Reset the pivot
                        // back to center now too — otherwise it would stay wherever the last
                        // long-press happened and throw off this card's own swipe rotation later.
                        zoomOrigin = TransformOrigin.Center
                        isZoomed = false
                        scope.launch { scale.animateTo(1f, ZOOM_SPRING) }
                        scope.launch { zoomPan.animateTo(Offset.Zero, ZOOM_PAN_SPRING) }
                    },
                )
            },
    )
}

/**
 * Routes a touch gesture to either the swipe-to-decide drag or a one-finger long-press zoom, for
 * the whole duration of a single continuous gesture: whichever resolves first — the finger moving
 * past touch slop (swipe) or holding still past the long-press timeout (zoom) — wins, and the
 * gesture stays in that mode until the finger lifts. This is why a deliberate hold-and-drag to
 * look around a zoomed photo can never suddenly turn into a swipe.
 */
private suspend fun PointerInputScope.detectSwipeOrLongPressZoom(
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onZoomStart: (TransformOrigin) -> Unit,
    onZoomPan: (Offset) -> Unit,
    onZoomEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalDrag = Offset.Zero
        var isZoom = false
        var isSwipe = false

        while (!isZoom && !isSwipe) {
            val event = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() }
            if (event == null) {
                isZoom = true
                break
            }
            val change = event.changes.firstOrNull { it.positionChanged() }
            if (change != null) {
                totalDrag += change.positionChange()
                change.consume()
                if (hypot(totalDrag.x, totalDrag.y) > viewConfiguration.touchSlop) {
                    isSwipe = true
                }
            }
            if (event.changes.none { it.pressed }) {
                // Released before either resolved (a plain tap): nothing to do.
                return@awaitEachGesture
            }
        }

        if (isZoom) {
            // Zoom expands from right under the finger, not the card's center, so whatever the
            // user pressed on is what stays put as the photo grows.
            val origin = TransformOrigin(
                (down.position.x / size.width).coerceIn(0f, 1f),
                (down.position.y / size.height).coerceIn(0f, 1f),
            )
            onZoomStart(origin)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.positionChanged() }
                if (change != null) {
                    onZoomPan(change.positionChange())
                    change.consume()
                }
                if (event.changes.none { it.pressed }) break
            }
            onZoomEnd()
        } else {
            onDrag(totalDrag)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.positionChanged() }
                if (change != null) {
                    onDrag(change.positionChange())
                    change.consume()
                }
                if (event.changes.none { it.pressed }) break
            }
            onDragEnd()
        }
    }
}
