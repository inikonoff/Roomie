package com.roomie.app.ui.screens.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateRotation
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roomie.app.data.media.MediaGroup
import com.roomie.app.data.settings.CardAnimationStyle
import com.roomie.app.ui.components.WindowBrightnessOverride
import com.roomie.app.ui.screens.settings.label
import com.roomie.app.ui.strings.AppStrings
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.ui.theme.SwipeLeftDelete
import com.roomie.app.ui.theme.SwipePostpone
import com.roomie.app.ui.theme.SwipeRightKeep
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SWIPE_THRESHOLD_DP = 120f

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

    // Only this screen: the override is dropped as soon as it leaves the composition.
    WindowBrightnessOverride(uiState.stackBrightnessEnabled, uiState.stackBrightnessLevel)

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
                // Only the field already loaded onto MediaItem when the folder's groups were
                // queried (see MediaRepository) — never a fresh MediaStore lookup from composition.
                // Absent entirely (not "no date") when the source has no DATE_TAKEN/DATE_ADDED at
                // all, which dateTakenMillis == 0 signals.
                uiState.currentGroup?.cover?.dateTakenMillis?.takeIf { it > 0 }?.let { millis ->
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
                        quarterTurnsByKey = uiState.quarterTurnsByKey,
                        onRotated = viewModel::setQuarterTurns,
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

/** A card exiting the stack after a committed swipe. The fly-out itself is driven by the card's own
 *  `SwipeCardSlot` (started straight from `onDragEnd`); this only keeps it in the stack, drawn on
 *  top, until that animation reports it has finished. */
private data class ExitingCardState(val group: MediaGroup)

private enum class CardRole { Warm, Behind, Top, Exiting }

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
    quarterTurnsByKey: Map<String, Int>,
    onRotated: (key: String, turns: Int) -> Unit,
    onSwiped: (SwipeDirection) -> Boolean,
) {
    var exiting by remember { mutableStateOf<ExitingCardState?>(null) }

    val top = stack.getOrNull(0)
    val behind = stack.getOrNull(1)
    val warm = stack.getOrNull(2)

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // stack[2] is composed below (role Warm, invisible) so its border is already in the tree
        // before it's ever promoted to Behind/Top — see the border-measure TZ. That same Warm slot
        // is also now stack[2]'s only decode source: its own AsyncImage (inside SwipeCard) requests
        // the same screenCacheKey that Behind/Top later read, so there's no separate prefetch to
        // keep in sync — a dedicated enqueue() here used to run concurrently with that AsyncImage's
        // own request for the same key, Coil 3.0 doesn't de-duplicate identical in-flight requests,
        // and the photo could end up decoded twice on the heaviest frame of the swipe.

        // A key must appear at most once per composition of this loop, or Compose throws at
        // runtime ("key was used multiple times"). The one case that could collide: Browse mode's
        // left-as-"go back" reinserts a group at the stack's front without removing it from where
        // it already was, so the very card just dragged past the threshold (now exiting) can also
        // still be sitting at `behind`/`top` a moment later. Exiting wins that collision — it's
        // already mid fly-out-animation — and the group naturally resumes as Behind/Top on its own
        // once that finishes and `exiting` clears.
        val exitingKey = exiting?.group?.key
        val slots = buildList {
            // Drawn first = underneath. Border is already on this node while the user is still
            // looking at `top`, so promoting it later (Warm -> Behind -> Top) never inserts a
            // Modifier.border into the chain on the release frame — see the border-measure TZ.
            if (warm != null && warm.key != exitingKey && warm.key != behind?.key && warm.key != top?.key) {
                add(warm to CardRole.Warm)
            }
            if (behind != null && behind.key != exitingKey) add(behind to CardRole.Behind)
            if (top != null && top.key != exitingKey) add(top to CardRole.Top)
            exiting?.let { add(it.group to CardRole.Exiting) }
        }

        for ((group, role) in slots) {
            key(group.key) {
                // A sideways turn re-fits the card to the photo's swapped shape (portrait <-> wide)
                // instead of leaving it letterboxed in its old frame. The other shape's width is
                // passed too so the slot can scale smoothly between the two while it turns.
                val turns = quarterTurnsByKey[group.key] ?: 0
                val ratio = group.cover.aspectRatio
                val sideways = Math.floorMod(turns, 4) % 2 == 1
                val (w, h) = fitSize(if (sideways) 1f / ratio else ratio, maxWidth, maxHeight)
                val (otherW, _) = fitSize(if (sideways) ratio else 1f / ratio, maxWidth, maxHeight)
                SwipeCardSlot(
                    group = group,
                    role = role,
                    cardWidth = w,
                    cardHeight = h,
                    otherShapeCardWidth = otherW,
                    quarterTurns = turns,
                    onRotationCommitted = { onRotated(group.key, it) },
                    animationStyle = animationStyle,
                    cardCornerRadiusDp = cardCornerRadiusDp,
                    cardBorderWidthDp = cardBorderWidthDp,
                    onCommitted = { direction ->
                        // Ask the ViewModel first: a rejected swipe (e.g. Move-to-folder with no
                        // target configured — see SwipeSessionViewModel.swipe) must never touch
                        // `exiting` at all, so the card can spring back to center exactly as if
                        // the threshold had never been crossed instead of flying off to nowhere.
                        val accepted = onSwiped(direction)
                        if (accepted) exiting = ExitingCardState(group)
                        accepted
                    },
                    // Guarded by group.key: a swipe committed before the previous card's own
                    // fly-out finished overwrites `exiting` with the new card before the old one's
                    // animation calls onExitFinished — its key() block simply stops being called on
                    // the next recomposition (it's no longer in `slots`) and gets disposed,
                    // cancelling that animation without running to completion. An unconditional
                    // `exiting = null` from that cancelled animation's own cleanup (the `finally`
                    // in SwipeCardSlot's exit) would then null out the *new* card's legitimate
                    // exiting state instead, aborting its fly-out mid-animation. Checking the key
                    // first means only the animation for whichever card `exiting` actually still
                    // refers to can clear it.
                    onExitFinished = { if (exiting?.group?.key == group.key) exiting = null },
                )
            }
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
    extraRotationDeg: Float,
) {
    val travelled = (hypot(offset.x, offset.y) / FLING_DISTANCE).coerceIn(0f, 1f)
    when (style) {
        CardAnimationStyle.CLASSIC -> {
            rotationZ = (offset.x / thresholdPx) * 12f + extraRotationDeg
            alpha = 1f
            scaleX = baseScale
            scaleY = baseScale
        }
        CardAnimationStyle.FADE -> {
            rotationZ = extraRotationDeg
            alpha = 1f - travelled
            scaleX = baseScale
            scaleY = baseScale
        }
        CardAnimationStyle.SHRINK -> {
            rotationZ = extraRotationDeg
            alpha = 1f
            val shrink = 1f - travelled * 0.4f
            scaleX = baseScale * shrink
            scaleY = baseScale * shrink
        }
    }
}

// Only the cancelled-swipe return-to-center — the committed exit is exitSpec() below. A spring's
// settling into place is still what a cancelled drag wants.
private val SWIPE_SPRING = spring<Offset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
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

private const val APPEAR_FADE_MS = 220

// How far the zoomed photo moves per unit of finger movement while a zoom is held. 1f would be
// the photo following the finger exactly, which at MAX_PEEK_ZOOM means sweeping across the card
// shows only 1/MAX_PEEK_ZOOM of it; (MAX_PEEK_ZOOM - 1) makes a sweep across the card width cover
// the whole pannable range. Raise it for a faster pan, lower for finer control.
private const val ZOOM_PAN_GAIN = MAX_PEEK_ZOOM - 1f

private val ROTATE_SETTLE_SPRING = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

// Distance at which Fade/Shrink finish (see applySwipeStyle). Not the exit distance — that comes
// from the window size, see flingTarget.
private const val FLING_DISTANCE = 1100f

// Exit pacing. The card leaves at the speed the finger had on release, so lifting off never stops
// it and restarts it. A spring can't do that here: toward a far target its pull (k * distance)
// dwarfs any release velocity, so a hard flick and a slow drag looked the same, and a tween eases in
// from rest — a full stop right after a moving finger. Instead the exit is a velocity-matched
// tween: it starts at exactly the release speed and accelerates just enough to clear the screen in
// a time proportional to the distance; a flick already fast enough keeps its speed and leaves
// sooner.
private const val EXIT_AVG_SPEED_DP_PER_S = 1400f
private const val EXIT_MIN_MS = 160
private const val EXIT_MAX_MS = 340
private const val EXIT_FLOOR_MS = 80

private fun exitSpec(distancePx: Float, releaseSpeedPxPerS: Float, density: Float): TweenSpec<Offset> {
    val distance = distancePx.coerceAtLeast(1f)
    val nominalMs = (distance / (EXIT_AVG_SPEED_DP_PER_S * density) * 1000f)
        .roundToInt()
        .coerceIn(EXIT_MIN_MS, EXIT_MAX_MS)
    // Share of the distance the card would cover in nominalMs just by keeping its release speed.
    val s = releaseSpeedPxPerS * nominalMs / 1000f / distance
    if (s >= 1f) {
        val ms = (distance / releaseSpeedPxPerS * 1000f).roundToInt().coerceAtLeast(EXIT_FLOOR_MS)
        return tween(ms, easing = LinearEasing)
    }
    // p(t) = s*t + (1-s)*t^2: slope s at t=0 (the release speed), reaching 1 at t=1.
    return tween(nominalMs, easing = Easing { t -> s * t + (1f - s) * t * t })
}

// Far enough past the window edge that a card clears it even rotated by Classic's tilt (which
// keeps growing with the offset) — a card that stops short would visibly pop out when removed.
private const val EXIT_HORIZONTAL_WINDOWS = 1.15f
private const val EXIT_VERTICAL_WINDOWS = 1.1f

private fun flingTarget(direction: SwipeDirection, current: Offset, window: Size): Offset = when (direction) {
    SwipeDirection.RIGHT -> Offset(window.width * EXIT_HORIZONTAL_WINDOWS, current.y)
    SwipeDirection.LEFT -> Offset(-window.width * EXIT_HORIZONTAL_WINDOWS, current.y)
    SwipeDirection.DOWN -> Offset(current.x, window.height * EXIT_VERTICAL_WINDOWS)
    SwipeDirection.UP -> Offset(current.x, -window.height * EXIT_VERTICAL_WINDOWS)
}

/**
 * One instance per photo for its whole life in the stack (see the [CardStack] doc for why that
 * matters). [role] switches what's drawn/interactive without ever recreating this composable:
 * - [CardRole.Warm]: composed (so its border exists ahead of time — see [CardStack]) and
 *   non-interactive. Invisible until its image has loaded, then fades in — so a card of another
 *   shape (a portrait one behind two wide ones) doesn't pop into the stack bare. The only role
 *   `pointerInput` isn't attached for — a Warm card is never mid-interaction when promoted, so
 *   there's nothing to hand off.
 * - [CardRole.Behind]: visible, static, but `pointerInput` is already attached (inert — see
 *   below) so promoting it straight to Top on the very frame a swipe commits never has to insert
 *   that modifier then. Promotion happens in the same recomposition as the committed card's own
 *   Top -> Exiting flip, so a cost paid there would land on the same already-heaviest frame as the
 *   fling starting — exactly where this was actually surfacing as a stutter.
 * - [CardRole.Top]: draggable/zoomable.
 * - [CardRole.Exiting]: plays the fly-out animation, which `onDragEnd` itself starts at the moment
 *   of release (not from a later recomposition, which left the card standing still for a couple of
 *   frames right after the finger lifted), then calls [onExitFinished]. `pointerInput` stays
 *   attached through the Top -> Exiting transition for the same reason as Behind above.
 *
 * Since `pointerInput` is attached for every role except Warm, the gesture callbacks themselves
 * check [role] and no-op whenever it isn't actually Top — that, not the modifier's presence, is
 * what keeps a Behind card inert and stops a stray touch on an already-exiting one from
 * double-committing it.
 */
@Composable
private fun SwipeCardSlot(
    group: MediaGroup,
    role: CardRole,
    cardWidth: Dp,
    cardHeight: Dp,
    otherShapeCardWidth: Dp,
    quarterTurns: Int,
    onRotationCommitted: (turns: Int) -> Unit,
    animationStyle: CardAnimationStyle,
    cardCornerRadiusDp: Int,
    cardBorderWidthDp: Float,
    onCommitted: (SwipeDirection) -> Boolean,
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
    // Written straight from each pointer-move while a zoom is held (like dragOffset for a swipe);
    // zoomPan only takes over for the spring back to center on release.
    var zoomPanLive by remember(group.key) { mutableStateOf(Offset.Zero) }
    var zoomOrigin by remember(group.key) { mutableStateOf(TransformOrigin.Center) }
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
    // Read by the long-lived pointerInput callbacks below (keyed on group.key, so the same
    // coroutine survives the Top -> Exiting role change) to tell a stray touch on an
    // already-committed card that it's too late, without needing to detach/reattach the modifier
    // itself to do it.
    val activeRole = rememberUpdatedState(role)

    // Rotation. [quarterTurns] is the turn count already baked into this card's layout and image
    // (what the ViewModel holds); what's drawn on top of that is the live twist plus the settle
    // animation, both relative to it. When a settle finishes and the ViewModel's count catches up,
    // the extra angle must drop by exactly the same amount in the same frame — so it's derived from
    // the difference against [appliedTurns] (the count the extra was last measured against) rather
    // than reset separately, which would show one frame of double rotation. The rebase below then
    // folds that difference in without changing what's drawn.
    val currentTurns = rememberUpdatedState(quarterTurns)
    var appliedTurns by remember(group.key) { mutableIntStateOf(quarterTurns) }
    var gestureDeg by remember(group.key) { mutableFloatStateOf(0f) }
    val settleDeg = remember(group.key) { Animatable(0f) }
    LaunchedEffect(quarterTurns) {
        val behind = quarterTurns - appliedTurns
        if (behind != 0) {
            settleDeg.snapTo(settleDeg.value - 90f * behind)
            appliedTurns = quarterTurns
        }
    }
    // Rigidly turning the card by 90 degrees gives the other shape at the wrong size; this is the
    // factor that maps one onto the other, blended in as the card turns so it ends at exactly the
    // other shape's re-fitted size and the layout swap at commit time is invisible.
    val turnedScale = if (cardHeight.value > 0f) otherShapeCardWidth.value / cardHeight.value else 1f
    fun extraDegNow(): Float = settleDeg.value + gestureDeg - 90f * (quarterTurns - appliedTurns)
    fun turnFitFor(extraDeg: Float): Float {
        val sinTurn = sin(extraDeg * (PI.toFloat() / 180f))
        return 1f + (turnedScale - 1f) * sinTurn * sinTurn
    }

    // Entrance. Behind/Warm cards stay invisible until their image has loaded, then fade in; Top and
    // Exiting are always shown (a card the user is already holding can't wait on a decode). Driven
    // from the image-ready signal, not from the role change, so for a card that finished loading in
    // the background the fade is long over by the time a swipe promotes it — nothing starts on the
    // release frame. (Before this the Warm card's own alpha = 0 was overwritten by applySwipeStyle's
    // alpha, so it was always fully visible and simply popped in.)
    var imageReady by remember(group.key) { mutableStateOf(false) }
    val appear = remember(group.key) {
        Animatable(if (role == CardRole.Top || role == CardRole.Exiting) 1f else 0f)
    }
    val shouldShow = imageReady || role == CardRole.Top || role == CardRole.Exiting
    LaunchedEffect(shouldShow) {
        if (shouldShow) appear.animateTo(1f, tween(APPEAR_FADE_MS, easing = LinearOutSlowInEasing))
    }

    val configuration = LocalConfiguration.current
    val windowSize = rememberUpdatedState(
        Size(
            with(density) { configuration.screenWidthDp.dp.toPx() },
            with(density) { configuration.screenHeightDp.dp.toPx() },
        ),
    )

    // Runs the committed fly-out. Started directly from onDragEnd, not from a LaunchedEffect keyed
    // on the role flipping to Exiting: that effect only started after CardStack's next
    // recomposition, leaving the card standing still at the release point for a couple of frames
    // right after the finger lifted — a visible stop in what should be one continuous motion.
    // [from] is the offset snapshot onDragEnd took at the instant it decided to commit, so a stray
    // leftover spring-back coroutine from a very quick re-grab can't race with it: snapTo() cancels
    // whatever animation flingOffset was still running, and the exit continues from this same
    // Animatable (rather than a fresh one) so there's no remount/blip.
    // The try/finally guarantees onExitFinished() still runs if this coroutine is cancelled instead
    // of completing — which happens whenever a second swipe lands before this card's own fly-out
    // finishes: `exiting` is reassigned to the new card, this card's key() block stops appearing in
    // CardStack's `slots`, and Compose disposes the slot (cancelling `scope`). Without the finally,
    // that left `exiting` referring to a group no longer in the stack, in the tree, or anywhere
    // reachable — CardStack's role-collision guards (`top.key != exitingKey`, etc.) then permanently
    // excluded whatever *should* have been top from `slots` too — the frozen-card symptom.
    // onExitFinished's own group.key check (see CardStack) keeps this cancellation path from
    // clobbering a legitimately newer exiting card instead.
    fun startExit(direction: SwipeDirection, from: Offset, releaseSpeedPxPerS: Float) {
        scope.launch {
            try {
                flingOffset.snapTo(from)
                dragOffset = Offset.Zero
                val target = flingTarget(direction, from, windowSize.value)
                val distance = when (direction) {
                    SwipeDirection.RIGHT, SwipeDirection.LEFT -> abs(target.x - from.x)
                    SwipeDirection.DOWN, SwipeDirection.UP -> abs(target.y - from.y)
                }
                flingOffset.animateTo(target, exitSpec(distance, releaseSpeedPxPerS, density.density))
            } finally {
                onExitFinished()
            }
        }
    }

    SwipeCard(
        group = group,
        isZoomed = isZoomed,
        // Always on, including Exiting — never gate this by role. Removing Modifier.border is the
        // same measure-pass cost as inserting it (see SwipeCard's own border comment), and gating
        // it off at Exiting landed that cost on the exact frame the exit fling starts, right next
        // to the pointerInput attach/detach stutter fixed just above for the same reason.
        showBorder = true,
        cornerRadiusDp = cardCornerRadiusDp,
        borderWidthDp = cardBorderWidthDp,
        quarterTurns = quarterTurns,
        layerScale = { turnFitFor(extraDegNow()) },
        onImageReady = { imageReady = true },
        modifier = Modifier
            .size(cardWidth, cardHeight)
            .graphicsLayer {
                transformOrigin = zoomOrigin
                val renderOffset = dragOffset + flingOffset.value
                translationX = renderOffset.x + zoomPan.value.x + zoomPanLive.x
                translationY = renderOffset.y + zoomPan.value.y + zoomPanLive.y
                val extraDeg = extraDegNow()
                val turnFit = turnFitFor(extraDeg)
                applySwipeStyle(
                    animationStyle,
                    renderOffset,
                    thresholdPx,
                    baseScale = scale.value * turnFit,
                    extraRotationDeg = extraDeg,
                )
                alpha *= appear.value
            }
            .then(
                if (role == CardRole.Warm) {
                    Modifier
                } else {
                    // Attached for Behind, Top, and Exiting — not just Top — so neither the
                    // Behind -> Top promotion nor the Top -> Exiting handoff ever has to insert or
                    // remove this node. Both transitions land in the very same recomposition a
                    // committed swipe triggers, right alongside the exit animation starting; paying
                    // a measure pass for either one there was landing on the heaviest frame of the
                    // swipe and showing up as a stutter exactly at that handoff. Compose keyed this
                    // on group.key, which doesn't change across any of these role changes, so it's
                    // the same coroutine continuing throughout, never restarted — the role check
                    // inside each callback below is what actually stops a Behind card or an
                    // already-exiting one from reacting to a stray touch, not the modifier's
                    // presence.
                    Modifier.pointerInput(group.key) {
                        detectSwipeOrLongPressZoom(
                            onDrag = { dragAmount ->
                                if (activeRole.value != CardRole.Top) return@detectSwipeOrLongPressZoom
                                dragOffset += dragAmount
                                val crossed = abs(dragOffset.x) > thresholdPx || abs(dragOffset.y) > thresholdPx
                                if (crossed != pastThreshold) {
                                    pastThreshold = crossed
                                    if (crossed) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDragEnd = { releaseVelocity ->
                                if (activeRole.value != CardRole.Top) return@detectSwipeOrLongPressZoom
                                // Includes any not-yet-settled flingOffset from a quick re-grab
                                // right after a previous non-swipe release, so the spring-back
                                // below picks up exactly where the card visually was instead of
                                // snapping to dragOffset alone.
                                val current = dragOffset + flingOffset.value
                                val horizontalCrossed = abs(current.x) > thresholdPx
                                val verticalCrossed = abs(current.y) > thresholdPx
                                val direction = when {
                                    horizontalCrossed && abs(current.x) >= abs(current.y) ->
                                        if (current.x > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                                    verticalCrossed -> if (current.y > 0) SwipeDirection.DOWN else SwipeDirection.UP
                                    else -> null
                                }
                                if (direction != null) {
                                    if (onCommitted(direction)) {
                                        // The exit carries on at the speed the finger had along
                                        // the swipe axis (never backwards, never the other axis),
                                        // starting from the snapshot taken above — see startExit.
                                        val releaseSpeed = when (direction) {
                                            SwipeDirection.RIGHT -> releaseVelocity.x
                                            SwipeDirection.LEFT -> -releaseVelocity.x
                                            SwipeDirection.DOWN -> releaseVelocity.y
                                            SwipeDirection.UP -> -releaseVelocity.y
                                        }.coerceAtLeast(0f)
                                        startExit(direction, current, releaseSpeed)
                                    } else {
                                        // Rejected by the ViewModel (e.g. no Move-to-folder target
                                        // configured): springs back to center exactly like a
                                        // below-threshold release below, as if the gesture had
                                        // not happened.
                                        scope.launch {
                                            flingOffset.snapTo(current)
                                            dragOffset = Offset.Zero
                                            flingOffset.animateTo(Offset.Zero, SWIPE_SPRING)
                                        }
                                    }
                                } else {
                                    // dragOffset must not reset to zero until flingOffset has
                                    // actually taken over the same value — doing it in the other
                                    // order rendered one frame at the visual center, then jumped
                                    // back out to the release point once the launched snapTo
                                    // caught up, a visible pop on every cancelled swipe.
                                    scope.launch {
                                        flingOffset.snapTo(current)
                                        dragOffset = Offset.Zero
                                        flingOffset.animateTo(Offset.Zero, SWIPE_SPRING)
                                    }
                                }
                            },
                            onZoomStart = { origin ->
                                if (activeRole.value != CardRole.Top) return@detectSwipeOrLongPressZoom
                                zoomOrigin = origin
                                isZoomed = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { scale.animateTo(MAX_PEEK_ZOOM, ZOOM_SPRING) }
                            },
                            onZoomPan = { delta ->
                                if (activeRole.value != CardRole.Top) return@detectSwipeOrLongPressZoom
                                // Zoom scales about the pivot (where the finger pressed), which moves
                                // every edge of the card away from it by (scale - 1) times its distance
                                // — so that is exactly how far the card can be shifted back before an
                                // edge would pull inside where it started. A pivot off to one side
                                // therefore gets a lopsided range, not a symmetric one.
                                val grow = (scale.value - 1f).coerceAtLeast(0f)
                                val fx = zoomOrigin.pivotFractionX
                                val fy = zoomOrigin.pivotFractionY
                                val wanted = zoomPan.value + zoomPanLive + delta * ZOOM_PAN_GAIN
                                val clamped = Offset(
                                    wanted.x.coerceIn(-(1f - fx) * cardWidthPx * grow, fx * cardWidthPx * grow),
                                    wanted.y.coerceIn(-(1f - fy) * cardHeightPx * grow, fy * cardHeightPx * grow),
                                )
                                zoomPanLive = clamped - zoomPan.value
                            },
                            onZoomEnd = {
                                if (activeRole.value != CardRole.Top) return@detectSwipeOrLongPressZoom
                                // A quick peek, not a decision: as soon as the finger lifts, the
                                // photo snaps straight back to its normal size and position. Reset
                                // the pivot back to center now too — otherwise it would stay
                                // wherever the last long-press happened and throw off this card's
                                // own swipe rotation later.
                                zoomOrigin = TransformOrigin.Center
                                isZoomed = false
                                scope.launch { scale.animateTo(1f, ZOOM_SPRING) }
                                scope.launch {
                                    // Animatable takes the value before the live part is cleared, or
                                    // one frame would draw the card back at center.
                                    zoomPan.snapTo(zoomPan.value + zoomPanLive)
                                    zoomPanLive = Offset.Zero
                                    zoomPan.animateTo(Offset.Zero, ZOOM_PAN_SPRING)
                                }
                            },
                            onRotate = { deltaDeg ->
                                if (activeRole.value != CardRole.Top || group.cover.isVideo) {
                                    return@detectSwipeOrLongPressZoom
                                }
                                gestureDeg += deltaDeg
                            },
                            onRotateEnd = {
                                if (activeRole.value != CardRole.Top) return@detectSwipeOrLongPressZoom
                                // Snap to the nearest quarter turn — any number of them, either way.
                                val turnsNow = currentTurns.value
                                val total = settleDeg.value + gestureDeg - 90f * (turnsNow - appliedTurns)
                                val deltaTurns = (total / 90f).roundToInt()
                                // Same hand-over order as the drag: the Animatable takes the value
                                // before the live twist is cleared, or one frame would draw 0.
                                scope.launch {
                                    appliedTurns = turnsNow
                                    settleDeg.snapTo(total)
                                    gestureDeg = 0f
                                    settleDeg.animateTo(deltaTurns * 90f, ROTATE_SETTLE_SPRING)
                                    if (deltaTurns != 0) onRotationCommitted(turnsNow + deltaTurns)
                                }
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
 * A second finger landing before either has resolved turns the gesture into a two-finger rotate
 * instead ([onRotate] gets each step's change in degrees, [onRotateEnd] fires when a finger lifts);
 * a second finger arriving after a swipe or zoom is already underway is ignored.
 */
private suspend fun PointerInputScope.detectSwipeOrLongPressZoom(
    onDrag: (Offset) -> Unit,
    onDragEnd: (releaseVelocity: Offset) -> Unit,
    onZoomStart: (TransformOrigin) -> Unit,
    onZoomPan: (Offset) -> Unit,
    onZoomEnd: () -> Unit,
    onRotate: (deltaDegrees: Float) -> Unit,
    onRotateEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Fed every event (not just moves, and including the lift itself) so a finger that paused
        // before lifting reads as stopped instead of reporting the speed from before the pause.
        // Only the swipe branch ever reads it.
        val velocityTracker = VelocityTracker()
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        var totalDrag = Offset.Zero
        var isZoom = false
        var isSwipe = false
        var isRotate = false

        while (!isZoom && !isSwipe && !isRotate) {
            val event = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() }
            if (event == null) {
                isZoom = true
                break
            }
            // A second finger before swipe or zoom has resolved makes this a rotate; once either
            // has, later fingers are ignored (a swipe already underway wins).
            if (event.changes.count { it.pressed } >= 2) {
                isRotate = true
                break
            }
            val change = event.changes.firstOrNull { it.positionChanged() }
            event.changes.firstOrNull()?.let { velocityTracker.addPosition(it.uptimeMillis, it.position) }
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

        if (isRotate) {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } < 2) {
                    onRotateEnd()
                    // Swallow what's left of the gesture: a finger still down must not start a swipe.
                    if (event.changes.any { it.pressed }) {
                        while (true) {
                            val rest = awaitPointerEvent()
                            rest.changes.forEach { it.consume() }
                            if (rest.changes.none { it.pressed }) break
                        }
                    }
                    break
                }
                val rotation = event.calculateRotation()
                if (rotation != 0f) onRotate(rotation)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } else if (isZoom) {
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
                event.changes.firstOrNull()?.let { velocityTracker.addPosition(it.uptimeMillis, it.position) }
                if (event.changes.none { it.pressed }) break
            }
            val velocity = velocityTracker.calculateVelocity()
            onDragEnd(Offset(velocity.x, velocity.y))
        }
    }
}
