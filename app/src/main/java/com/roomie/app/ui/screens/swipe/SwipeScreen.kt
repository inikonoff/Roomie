package com.roomie.app.ui.screens.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.roomie.app.ui.screens.settings.label
import com.roomie.app.ui.strings.AppStrings
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.ui.theme.SwipeLeftDelete
import com.roomie.app.ui.theme.SwipePostpone
import com.roomie.app.ui.theme.SwipeRightKeep
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

private const val SWIPE_THRESHOLD_DP = 120f
private const val MAX_PEEK_ZOOM = 2.5f

/** A fast flick can commit a swipe even when released well short of [SWIPE_THRESHOLD_DP] — see
 *  [dominantSign] and its call site in [SwipeCardSlot]'s `onDragEnd`. */
private const val FLING_VELOCITY_THRESHOLD_DP = 1000f

/** Floor applied to the exit decay's initial velocity so a slow drag that crossed the threshold by
 *  offset alone (near-zero release velocity) still visibly flies off instead of drifting to a stop
 *  mid-stack. */
private const val MIN_DECAY_VELOCITY_DP = 400f

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
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.hasReachedLimit) {
        if (uiState.hasReachedLimit) onLimitReached()
    }

    LaunchedEffect(uiState.isStackExhausted, uiState.isLoading) {
        if (!uiState.isLoading && uiState.isStackExhausted) onStackExhausted()
    }

    LaunchedEffect(viewModel) {
        viewModel.browseHistoryExhaustedEvents.collect {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.moveTargetMissingEvents.collect {
            snackbarHostState.showSnackbar(strings.selectMoveFolderPrompt)
        }
    }

    // A deferred-by-one-frame mirror of the header's own numbers — see the release-hitch TZ.
    // GamifiedProgressBar/counterOfTotal/dateTaken used to read uiState directly, which put their
    // recomposition in the exact same frame as the stack/top swap, the exit decay starting, and
    // the next card's prefetch kicking off — already the single heaviest frame in the whole
    // gesture. Holding the previous card's numbers for one more frame while it's already visibly
    // flying off is unnoticeable; redoing all four at once is the hitch. hasShownHeaderOnce skips
    // the delay on the very first population (entering the screen) so the header isn't blank for
    // a frame when there was never any "release" to spread work away from.
    var displayedPosition by remember { mutableStateOf(uiState.currentPosition) }
    var displayedTotal by remember { mutableStateOf(uiState.totalCount) }
    var displayedDeleted by remember { mutableStateOf(uiState.deletedCount) }
    var displayedKept by remember { mutableStateOf(uiState.keptCount) }
    var displayedPostponed by remember { mutableStateOf(uiState.postponedCount) }
    var displayedDateMillis by remember { mutableStateOf(uiState.currentGroup?.cover?.dateTakenMillis ?: 0L) }
    var hasShownHeaderOnce by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.currentGroup?.key, uiState.deletedCount, uiState.keptCount, uiState.postponedCount) {
        if (hasShownHeaderOnce) delay(32) else hasShownHeaderOnce = true
        displayedPosition = uiState.currentPosition
        displayedTotal = uiState.totalCount
        displayedDeleted = uiState.deletedCount
        displayedKept = uiState.keptCount
        displayedPostponed = uiState.postponedCount
        displayedDateMillis = uiState.currentGroup?.cover?.dateTakenMillis ?: 0L
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(uiState.folderName, style = MaterialTheme.typography.titleLarge)
                        uiState.gesturePreset?.let {
                            Text(
                                it.label(strings),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        if (uiState.trashedCount > 0) {
                            BadgedBox(badge = { Badge { Text(uiState.trashedCount.toString()) } }) {
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
                    deleted = displayedDeleted,
                    kept = displayedKept,
                    postponed = displayedPostponed,
                    total = displayedTotal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    strings.counterOfTotal(displayedPosition, displayedTotal),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    textAlign = TextAlign.Center,
                )
                // Only the field already loaded onto MediaItem when the folder's groups were
                // queried (see MediaRepository) — never a fresh MediaStore lookup from composition.
                // Absent entirely (not "no date") when the source has no DATE_TAKEN/DATE_ADDED at
                // all, which dateTakenMillis == 0 signals.
                displayedDateMillis.takeIf { it > 0 }?.let { millis ->
                    Text(
                        strings.dateTaken(millis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (uiState.monetizationEnabled) {
                SwipeLimitIndicator(
                    current = uiState.sessionSwipeCount,
                    limit = uiState.freeSwipeLimit,
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(uiState.edgePaddingDp.dp), contentAlignment = Alignment.Center) {
                when {
                    uiState.isLoading -> CircularProgressIndicator()
                    uiState.stack.isEmpty() -> Text(
                        strings.folderIsClean,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> CardStack(
                        stack = uiState.stack,
                        animationStyle = uiState.cardAnimationStyle,
                        cardCornerRadiusDp = uiState.cardCornerRadiusDp,
                        cardBorderWidthDp = uiState.cardBorderWidthDp,
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

/** A card exiting the stack after a committed swipe. [offset] is a snapshot taken by
 *  `SwipeCardSlot`'s `onDragEnd` at the exact instant it decided to commit — passed through rather
 *  than re-read later from the (still-mutable) drag/fling state, so a stray leftover spring-back
 *  coroutine from a very quick re-grab can't race with it (see [SwipeCardSlot]'s `LaunchedEffect`).
 *  [velocity] is the release velocity along the exit axis only (px/s, already signed to match
 *  [direction] and floored to [MIN_DECAY_VELOCITY_DP]) — the perpendicular component is dropped so
 *  the card never drifts off on a diagonal. */
private data class ExitingCardState(val group: MediaGroup, val direction: SwipeDirection, val offset: Offset, val velocity: Float)

private enum class CardRole { Behind, Top, Exiting }

/** Fits a card of [ratio] (width/height) inside a [maxWidth] x [maxHeight] box, like
 *  [androidx.compose.ui.layout.ContentScale.Fit] but sizing the composable itself rather than
 *  its content — so differently-oriented photos each get their own natural size on screen. */
private fun fitSize(ratio: Float, maxWidth: Dp, maxHeight: Dp): Pair<Dp, Dp> {
    val containerRatio = maxWidth / maxHeight
    return if (containerRatio > ratio) (maxHeight * ratio) to maxHeight else maxWidth to (maxWidth / ratio)
}

/**
 * A photo's journey through this stack is behind -> top -> exiting -> gone, but until this pass
 * that was rendered by three entirely separate composable call sites (a bare [SwipeCard], the old
 * `DraggableCard`, the old `ExitingCard`) with no shared identity between them — so every role
 * change threw away and recreated a fresh instance (a new [AsyncImage][coil3.compose.AsyncImage]
 * decode/layout/measure pass, even on a Coil cache hit), landing right on the frame that was
 * already the heaviest one (the stack changing). The fix is exactly one call site
 * (`for (slot in slots)` below) producing a single [SwipeCardSlot] per photo, wrapped in
 * `key(group.key)`: Compose's keyed-children diffing (the same mechanism `LazyColumn`'s
 * `items(list, key = ...)` relies on) then recognizes the *same* key reappearing at a new list
 * position with a new [CardRole] as a continuation of the same composable instance, not a new one
 * — its remembered drag/fling/zoom state survives the role change untouched. Three independent
 * `key(group.key) { ... }` calls written at three different source locations would NOT achieve
 * this (each `key()` call site is its own identity to the compose compiler regardless of the
 * runtime key value), which is why this needed restructuring rather than just adding `key()` calls
 * to the previous three call sites.
 */
@Composable
private fun CardStack(
    stack: List<MediaGroup>,
    animationStyle: CardAnimationStyle,
    cardCornerRadiusDp: Int,
    cardBorderWidthDp: Float,
    onSwiped: (SwipeDirection) -> Boolean,
) {
    var exiting by remember { mutableStateOf<ExitingCardState?>(null) }

    val top = stack.getOrNull(0)
    val behind = stack.getOrNull(1)
    val prefetch = stack.getOrNull(2)

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Only stack[0] and stack[1] are actually composed (below) — by the time a fast swiper
        // reaches stack[2] it would otherwise not have started decoding at all. Warming Coil's
        // cache for it one card early removes that gap without paying for a third on-screen card.
        // Delayed past the release frame on purpose — see the release-hitch TZ: starting this
        // enqueue() in the very same recomposition that drops the stack and flips the new top card
        // piled a fourth expensive thing onto the one frame already doing a stack/head swap and
        // starting the exit decay. Keyed on prefetch?.key alone (not maxWidth/maxHeight too) so a
        // transient layout pass during that same transition can't cancel and restart the delay —
        // only the stack actually advancing to a new third card does.
        val context = LocalContext.current
        val density = LocalDensity.current
        LaunchedEffect(prefetch?.key) {
            val group = prefetch ?: return@LaunchedEffect
            delay(48)
            val (w, h) = fitSize(group.cover.aspectRatio, maxWidth, maxHeight)
            val widthPx = with(density) { w.roundToPx() }.coerceAtLeast(1)
            val heightPx = with(density) { h.roundToPx() }.coerceAtLeast(1)
            // Hardware bitmaps are fine here — this is a background cache warm, never read back on
            // the CPU. Only a live pinch-zoom (SwipeCard's isZoomed branch) needs software.
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(group.cover.uri)
                    .size(widthPx, heightPx)
                    .build(),
            )
        }

        // A key must appear at most once per composition of this loop, or Compose throws at
        // runtime ("key was used multiple times"). The one case that could collide: Browse mode's
        // left-as-"go back" reinserts a group at the stack's front without removing it from where
        // it already was, so the very card just dragged past the threshold (now exiting) can also
        // still be sitting at `behind`/`top` a moment later. Exiting wins that collision — it's
        // already mid fly-out-animation — and the group naturally resumes as Behind/Top on its own
        // once that finishes and `exiting` clears.
        val exitingKey = exiting?.group?.key
        val slots = buildList {
            if (behind != null && behind.key != exitingKey) add(behind to CardRole.Behind)
            if (top != null && top.key != exitingKey) add(top to CardRole.Top)
            exiting?.let { add(it.group to CardRole.Exiting) }
        }

        for ((group, role) in slots) {
            key(group.key) {
                val (w, h) = fitSize(group.cover.aspectRatio, maxWidth, maxHeight)
                SwipeCardSlot(
                    group = group,
                    role = role,
                    cardWidth = w,
                    cardHeight = h,
                    animationStyle = animationStyle,
                    cardCornerRadiusDp = cardCornerRadiusDp,
                    cardBorderWidthDp = cardBorderWidthDp,
                    exitDirection = if (role == CardRole.Exiting) exiting?.direction else null,
                    exitOffset = if (role == CardRole.Exiting) exiting?.offset else null,
                    exitVelocity = if (role == CardRole.Exiting) exiting?.velocity else null,
                    onCommitted = { direction, releaseOffset, axisVelocity ->
                        // Ask the ViewModel first: a rejected swipe (e.g. Move-to-folder with no
                        // target configured — see SwipeSessionViewModel.swipe) must never touch
                        // `exiting` at all, so the card can spring back to center exactly as if
                        // the threshold had never been crossed instead of flying off to nowhere.
                        val accepted = onSwiped(direction)
                        if (accepted) {
                            // Snapshot offset first, mark this slot as exiting second — so the
                            // exiting state (and the offset it needs) is already set before
                            // anything downstream could observe the stack without it, which is
                            // what a successful-swipe skip/pop would look like.
                            exiting = ExitingCardState(group, direction, releaseOffset, axisVelocity)
                        }
                        accepted
                    },
                    // Guarded by group.key: a swipe committed before the previous card's own
                    // fly-out finished overwrites `exiting` with the new card before the old one's
                    // LaunchedEffect(exitDirection) below ever calls onExitFinished — its key()
                    // block simply stops being called on the next recomposition (it's no longer in
                    // `slots`) and gets disposed, cancelling that effect without running to
                    // completion. An unconditional `exiting = null` from that cancelled effect's own
                    // cleanup (see the `finally` below) would then null out the *new* card's
                    // legitimate exiting state instead, aborting its fly-out mid-animation. Checking
                    // the key first means only the effect for whichever card `exiting` actually
                    // still refers to can clear it.
                    onExitFinished = { if (exiting?.group?.key == group.key) exiting = null },
                )
            }
        }
    }
}

/**
 * Applies the user's chosen swipe-card look for the current drag/fling [offset]: Classic rotates
 * like a card pivoting on a table, Fade and Shrink both drop the rotation and instead ease out via
 * opacity or size as the card travels toward (and past) the edge of its own footprint — [cardWidthPx]/
 * [cardHeightPx], not a fixed fling distance, so "travelled" means the same thing during a live drag
 * and during the exit decay alike, and Fade/Shrink never finish easing out before the card is
 * actually off-screen.
 */
private fun GraphicsLayerScope.applySwipeStyle(
    style: CardAnimationStyle,
    offset: Offset,
    thresholdPx: Float,
    cardWidthPx: Float,
    cardHeightPx: Float,
    baseScale: Float,
) {
    val travelled = max(abs(offset.x) / cardWidthPx, abs(offset.y) / cardHeightPx).coerceIn(0f, 1f)
    when (style) {
        CardAnimationStyle.CLASSIC -> {
            rotationZ = (offset.x / thresholdPx) * 12f
            alpha = 1f
            scaleX = baseScale
            scaleY = baseScale
        }
        CardAnimationStyle.FADE -> {
            rotationZ = 0f
            alpha = 1f - travelled
            scaleX = baseScale
            scaleY = baseScale
        }
        CardAnimationStyle.SHRINK -> {
            rotationZ = 0f
            alpha = 1f
            val shrink = 1f - travelled * 0.4f
            scaleX = baseScale * shrink
            scaleY = baseScale * shrink
        }
    }
}

private val SWIPE_SPRING = spring<Offset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

private val ZOOM_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

private val ZOOM_PAN_SPRING = spring<Offset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** Picks which of an offset/velocity pair's signs decides a commit's direction, for the (rare) case
 *  the offset component is exactly zero — a pure-velocity commit on an axis the finger never
 *  actually displaced along. Real gestures almost always agree on sign between the two already. */
private fun dominantSign(offsetComponent: Float, velocityComponent: Float): Float =
    if (offsetComponent != 0f) offsetComponent else velocityComponent

/**
 * One instance per photo for its whole life in the stack (see the [CardStack] doc for why that
 * matters). [role] switches what's drawn/interactive without ever recreating this composable:
 * - [CardRole.Behind]: static, non-interactive, no gesture attached.
 * - [CardRole.Top]: draggable/zoomable, the only role with `pointerInput` attached.
 * - [CardRole.Exiting]: no gesture; plays the fly-out animation once via [exitDirection], then
 *   calls [onExitFinished].
 */
@Composable
private fun SwipeCardSlot(
    group: MediaGroup,
    role: CardRole,
    cardWidth: Dp,
    cardHeight: Dp,
    animationStyle: CardAnimationStyle,
    cardCornerRadiusDp: Int,
    cardBorderWidthDp: Float,
    exitDirection: SwipeDirection?,
    exitOffset: Offset?,
    exitVelocity: Float?,
    onCommitted: (SwipeDirection, Offset, Float) -> Boolean,
    onExitFinished: () -> Unit,
) {
    // Written to directly on every pointer-move event instead of through a suspend Animatable —
    // launching a fresh coroutine per touch event (the previous approach, via snapTo) queues each
    // update on the dispatcher instead of applying it immediately, and under a fast swipe with
    // dozens of move events per second that queue falls a frame or two behind the finger, making
    // the card visibly "catch up" in jumps rather than track it 1:1. flingOffset below is the
    // suspend/spring side: easing back to center on a cancelled swipe (Top role) and flying off
    // screen on a committed one (Exiting role) both animate this same Animatable, continuing from
    // whatever it already holds rather than starting a fresh one at a copied position.
    var dragOffset by remember(group.key) { mutableStateOf(Offset.Zero) }
    val flingOffset = remember(group.key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scale = remember(group.key) { Animatable(1f) }
    val zoomPan = remember(group.key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var zoomOrigin by remember(group.key) { mutableStateOf(TransformOrigin.Center) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pastThreshold by remember(group.key) { mutableStateOf(false) }
    // While zoomed, SwipeCard switches to requesting the source's full resolution instead of a
    // screen-sized one — worth the extra decode time only for this deliberate, held-down peek.
    var isZoomed by remember(group.key) { mutableStateOf(false) }
    val density = LocalDensity.current
    val thresholdPx = with(density) { SWIPE_THRESHOLD_DP.dp.toPx() }
    val flingVelocityThresholdPx = with(density) { FLING_VELOCITY_THRESHOLD_DP.dp.toPx() }
    val minDecayVelocityPx = with(density) { MIN_DECAY_VELOCITY_DP.dp.toPx() }
    val cardWidthPx = with(density) { cardWidth.toPx() }
    val cardHeightPx = with(density) { cardHeight.toPx() }
    // Native-feeling fling physics (the same curve Android's own scroll/fling uses) for the exit
    // decay — not keyed on group.key: it only depends on density, so one instance serves every
    // card this slot ever plays host to.
    val decayAnimationSpec = rememberSplineBasedDecay<Offset>()

    // Fires once per commit — role has already become Exiting with a non-null exitDirection/
    // exitOffset by the time this runs (CardStack sets all three together). Uses exitOffset — the
    // value onDragEnd captured at the exact instant it decided to commit — rather than re-reading
    // dragOffset + flingOffset.value here: a quick re-grab right after a previous cancelled swipe on
    // this same card can leave that cancelled swipe's own spring-back coroutine still running
    // (nothing cancels it just because a new gesture started), so a live re-read at this later point
    // could race with it and use a value that no longer matches what was actually on screen at
    // release. Continuing the *animation* from this same remembered flingOffset (rather than a
    // fresh Animatable) is still what avoids a remount/blip — only the starting value is now an
    // explicit snapshot instead of a live re-read.
    // Keyed on group.key too (not just exitDirection): this slot can be reused for a *different*
    // card without exitDirection itself changing value (e.g. two commits in the same direction in
    // a row land on the same key() slot only if the group is reused, which doesn't normally happen
    // here — but the guard costs nothing and removes the assumption). More importantly, wrapping in
    // try/finally guarantees onExitFinished() still runs if this effect gets cancelled instead of
    // completing normally — which happens whenever a second swipe lands before this card's own
    // fly-out finishes: `exiting` is reassigned to the new card, this card's key() block stops
    // appearing in CardStack's `slots`, and Compose disposes it (cancelling this coroutine) without
    // ever reaching the `onExitFinished()` call at the end of the block. Without the finally, that
    // left `exiting` referring to a group no longer in the stack, in the tree, or anywhere reachable
    // — CardStack's role-collision guards (`top.key != exitingKey`, etc.) then permanently excluded
    // whatever *should* have been top from `slots` too, since its key matched the orphaned
    // exitingKey — the frozen-card symptom. onExitFinished's own group.key check (see CardStack)
    // keeps this cancellation path from clobbering a legitimately newer exiting card instead.
    LaunchedEffect(group.key, exitDirection, exitOffset, exitVelocity) {
        if (exitDirection == null || exitOffset == null || exitVelocity == null) return@LaunchedEffect
        try {
            flingOffset.snapTo(exitOffset)
            dragOffset = Offset.Zero
            // Perpendicular component dropped entirely (not just left at its release value) so the
            // decay travels straight along the swiped axis instead of drifting diagonally.
            val initialVelocity = when (exitDirection) {
                SwipeDirection.LEFT, SwipeDirection.RIGHT -> Offset(exitVelocity, 0f)
                SwipeDirection.UP, SwipeDirection.DOWN -> Offset(0f, exitVelocity)
            }
            // animateDecay's own natural end (velocity decayed to ~0) is not a reliable finish
            // line here — exitVelocity is floored (see MIN_DECAY_VELOCITY_DP) so this almost always
            // reaches the edge well before that, but nothing guarantees it, and waiting for it
            // anyway would leave Exiting sitting in the stack, still eating touches, long after the
            // card has visually left. Race a watcher against the decay instead: whichever notices
            // the card is now fully past its own width/height along the exit axis stops the other.
            // watcher.cancel() after animateDecay returns is what prevents the opposite case (decay
            // finishing or getting stopped) from leaving an orphaned watcher awaiting a condition
            // that will now never arrive, since flingOffset.value stops changing once decay ends.
            coroutineScope {
                val watcher = launch {
                    snapshotFlow { flingOffset.value }
                        .first { value ->
                            when (exitDirection) {
                                SwipeDirection.LEFT, SwipeDirection.RIGHT -> abs(value.x) > cardWidthPx
                                SwipeDirection.UP, SwipeDirection.DOWN -> abs(value.y) > cardHeightPx
                            }
                        }
                    flingOffset.stop()
                }
                flingOffset.animateDecay(initialVelocity, decayAnimationSpec)
                watcher.cancel()
            }
        } finally {
            onExitFinished()
        }
    }

    SwipeCard(
        group = group,
        isZoomed = isZoomed,
        showBorder = role == CardRole.Top,
        cornerRadiusDp = cardCornerRadiusDp,
        borderWidthDp = cardBorderWidthDp,
        modifier = Modifier
            .size(cardWidth, cardHeight)
            .graphicsLayer {
                transformOrigin = zoomOrigin
                val renderOffset = dragOffset + flingOffset.value
                translationX = renderOffset.x + zoomPan.value.x
                translationY = renderOffset.y + zoomPan.value.y
                applySwipeStyle(animationStyle, renderOffset, thresholdPx, cardWidthPx, cardHeightPx, baseScale = scale.value)
            }
            .then(
                if (role != CardRole.Top) {
                    Modifier
                } else {
                    Modifier.pointerInput(group.key) {
                        detectSwipeOrLongPressZoom(
                            onDrag = { dragAmount ->
                                dragOffset += dragAmount
                                val crossed = abs(dragOffset.x) > thresholdPx || abs(dragOffset.y) > thresholdPx
                                if (crossed != pastThreshold) {
                                    pastThreshold = crossed
                                    if (crossed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDragEnd = { velocityPxPerSec ->
                                // Includes any not-yet-settled flingOffset from a quick re-grab
                                // right after a previous non-swipe release, so the commit decision
                                // below picks up exactly where the card visually was instead of
                                // reading dragOffset alone.
                                val current = dragOffset + flingOffset.value
                                // Two independent routes to a commit on each axis — crossing the
                                // offset threshold (as before) or a fast-enough release velocity
                                // even short of it — and the axis that wins is whichever scores
                                // higher on *either* measure, so the action stays tied to one side
                                // instead of a free diagonal throw.
                                val xScore = max(abs(current.x) / thresholdPx, abs(velocityPxPerSec.x) / flingVelocityThresholdPx)
                                val yScore = max(abs(current.y) / thresholdPx, abs(velocityPxPerSec.y) / flingVelocityThresholdPx)
                                val direction = when {
                                    xScore < 1f && yScore < 1f -> null
                                    xScore >= yScore ->
                                        if (dominantSign(current.x, velocityPxPerSec.x) >= 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
                                    else ->
                                        if (dominantSign(current.y, velocityPxPerSec.y) >= 0f) SwipeDirection.DOWN else SwipeDirection.UP
                                }
                                // dragOffset must not reset to zero until flingOffset has actually
                                // taken over the same value — doing it in the other order rendered
                                // one frame at the visual center, then jumped back out to the
                                // release point once the launched snapTo caught up, a visible pop.
                                suspend fun springBackToCenter() {
                                    flingOffset.snapTo(current)
                                    dragOffset = Offset.Zero
                                    flingOffset.animateTo(Offset.Zero, SWIPE_SPRING)
                                }
                                if (direction == null) {
                                    scope.launch { springBackToCenter() }
                                } else {
                                    // A velocity-only commit (never crossed the offset threshold,
                                    // so the drag haptic in onDrag above never fired) still gets
                                    // one click, same feedback as crossing the threshold would have.
                                    if (!pastThreshold) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val axisVelocity = if (direction == SwipeDirection.LEFT || direction == SwipeDirection.RIGHT) {
                                        velocityPxPerSec.x
                                    } else {
                                        velocityPxPerSec.y
                                    }
                                    val directionSign = if (direction == SwipeDirection.RIGHT || direction == SwipeDirection.DOWN) 1f else -1f
                                    // Floored so a slow drag that only barely crossed the offset
                                    // threshold (near-zero release velocity) still visibly flies off
                                    // during the exit decay instead of drifting to a stop mid-stack.
                                    // Named distinctly from this composable's own exitVelocity
                                    // parameter (used by the LaunchedEffect above) purely to avoid
                                    // shadowing it — this is the value a future exit, if accepted,
                                    // will eventually be handed back as that very parameter.
                                    val axisExitVelocity = directionSign * max(abs(axisVelocity), minDecayVelocityPx)
                                    // Pass the snapshot taken above (current) through explicitly
                                    // instead of letting the exit animation re-read dragOffset/
                                    // flingOffset later — see the LaunchedEffect above for why a
                                    // live re-read at that later point can race with a stray
                                    // leftover spring-back coroutine. The actual fly-out animation
                                    // is driven by this same instance's own LaunchedEffect, once
                                    // CardStack's next recomposition flips this slot's role to
                                    // Exiting with this direction/offset/velocity — but only if the
                                    // ViewModel actually accepted the swipe; a rejected one (e.g. no
                                    // Move-to-folder target configured) springs back to center
                                    // exactly like a below-threshold release, as if the gesture had
                                    // not happened.
                                    if (!onCommitted(direction, current, axisExitVelocity)) {
                                        scope.launch { springBackToCenter() }
                                    }
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
                                // scope.launch is required here, not just an optimization detail:
                                // this callback runs inside awaitEachGesture's restricted-suspension
                                // coroutine (AwaitPointerEventScope), which the Kotlin compiler only
                                // allows to call suspend functions on that same receiver type —
                                // Animatable.snapTo is a suspend member of an unrelated type, so
                                // calling it directly here is a compile error ("Restricted suspending
                                // functions can only invoke member or extension suspending functions
                                // on their restricted coroutine scope"), not just a style choice.
                                // launch{} starts a genuinely separate coroutine to escape that
                                // restriction, same as this code did before.
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
                                // A quick peek, not a decision: as soon as the finger lifts, the
                                // photo snaps straight back to its normal size and position. Reset
                                // the pivot back to center now too — otherwise it would stay
                                // wherever the last long-press happened and throw off this card's
                                // own swipe rotation later.
                                zoomOrigin = TransformOrigin.Center
                                isZoomed = false
                                scope.launch { scale.animateTo(1f, ZOOM_SPRING) }
                                scope.launch { zoomPan.animateTo(Offset.Zero, ZOOM_PAN_SPRING) }
                            },
                        )
                    }
                },
            ),
    )
}

/**
 * Routes a touch gesture to either the swipe-to-decide drag or a one-finger long-press zoom, for
 * the whole duration of a single continuous gesture: whichever resolves first — the finger moving
 * past touch slop (swipe) or holding still past the long-press timeout (zoom) — wins, and the
 * gesture stays in that mode until the finger lifts. This is why a deliberate hold-and-drag to
 * look around a zoomed photo can never suddenly turn into a swipe.
 *
 * [onDragEnd] receives the release velocity in px/s, measured only across this gesture's swipe
 * portion (the pre-resolution exploring phase counts, a resolved zoom never does — nothing reads
 * the tracker in that branch) — so a long-press-to-zoom gesture never feeds its own, unrelated
 * motion into a swipe decision.
 */
private suspend fun PointerInputScope.detectSwipeOrLongPressZoom(
    onDrag: (Offset) -> Unit,
    onDragEnd: (velocity: Offset) -> Unit,
    onZoomStart: (TransformOrigin) -> Unit,
    onZoomPan: (Offset) -> Unit,
    onZoomEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
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
                velocityTracker.addPosition(change.uptimeMillis, change.position)
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
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                }
                if (event.changes.none { it.pressed }) break
            }
            val velocity = velocityTracker.calculateVelocity()
            onDragEnd(Offset(velocity.x, velocity.y))
        }
    }
}
