package com.roomie.app.ui.screens.swipe

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roomie.app.data.media.MediaGroup
import com.roomie.app.data.media.MediaRepository
import com.roomie.app.data.media.PeriodFilter
import com.roomie.app.data.monetization.MonetizationGateway
import com.roomie.app.data.monetization.PurchaseResult
import com.roomie.app.data.monetization.RewardResult
import com.roomie.app.data.settings.CardAnimationStyle
import com.roomie.app.data.settings.RoomieSettings
import com.roomie.app.data.settings.SettingsRepository
import com.roomie.app.data.settings.SwipeCardAction
import com.roomie.app.data.trash.TrashRepository
import com.roomie.app.ui.screens.settings.SwipeGesturePreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

private const val MAX_UNDO_HISTORY = 10

/** How many swipes accumulate in memory before [SwipeSessionViewModel.flushSwipeCount] writes them
 *  to DataStore in one go, instead of a real disk write on every single swipe (the heaviest frame
 *  already, between the stack update and the exit-fling animation starting). */
private const val SWIPE_COUNT_FLUSH_INTERVAL = 5

private data class SwipeAction(val group: MediaGroup, val action: SwipeCardAction)

data class SwipeUiState(
    val folderName: String = "",
    val stack: List<MediaGroup> = emptyList(),
    val isLoading: Boolean = true,
    val sessionSwipeCount: Int = 0,
    val freeSwipeLimit: Int = 100,
    val hasReachedLimit: Boolean = false,
    val canUndo: Boolean = false,
    val isStackExhausted: Boolean = false,
    val cardAnimationStyle: CardAnimationStyle = CardAnimationStyle.CLASSIC,
    val edgePaddingDp: Int = 24,
    /** Size of the whole folder this session started from (including anything already skipped
     *  past via "start at this photo"), for the "12 of 345" position counter. */
    val totalCount: Int = 0,
    val deletedCount: Int = 0,
    val deletedBytes: Long = 0L,
    /** Left untouched, whether by an explicit Keep or a "do nothing" browse swipe — both leave the
     *  file exactly as it was, so they share one bucket in the progress bar. */
    val keptCount: Int = 0,
    val postponedCount: Int = 0,
    val monetizationEnabled: Boolean = false,
    /** Live count of everything currently in the trash, independent of this session — see
     *  [com.roomie.app.data.trash.TrashRepository.observeTrashedCount]. Distinct from
     *  [deletedCount], which is scoped to just this swipe session for the progress bar/summary. */
    val trashedCount: Int = 0,
    /** Which gesture preset (if any) the current swipe settings match, shown as a subtitle on this
     *  screen so the active mode is visible without opening Settings. Null under a custom (mixed)
     *  configuration that matches neither preset. */
    val gesturePreset: SwipeGesturePreset? = null,
) {
    val currentGroup: MediaGroup? get() = stack.firstOrNull()

    /** 1-based position of [currentGroup] within the original folder ordering. */
    val currentPosition: Int get() = totalCount - stack.size + 1
}

data class SummaryUiState(val itemCount: Int, val freedBytes: Long)

data class MoveConfirmationRequest(val intentSender: IntentSender, val group: MediaGroup, val targetRelativePath: String)

class SwipeSessionViewModel(
    private val mediaRepository: MediaRepository,
    private val trashRepository: TrashRepository,
    private val settingsRepository: SettingsRepository,
    private val monetizationGateway: MonetizationGateway,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SwipeUiState())
    val uiState: StateFlow<SwipeUiState> = _uiState.asStateFlow()

    private val _summaryState = MutableStateFlow<SummaryUiState?>(null)
    val summaryState: StateFlow<SummaryUiState?> = _summaryState.asStateFlow()

    private val _moveConfirmationEvents = MutableSharedFlow<MoveConfirmationRequest>()
    val moveConfirmationEvents: SharedFlow<MoveConfirmationRequest> = _moveConfirmationEvents

    /** Fired when a Browse left-swipe (go back) has nothing left in [browseHistory] to return to —
     *  SwipeScreen turns this into the same light haptic click already used for a swipe crossing
     *  its threshold, as a "you've hit the start" cue instead of silently doing nothing. */
    private val _browseHistoryExhaustedEvents = MutableSharedFlow<Unit>()
    val browseHistoryExhaustedEvents: SharedFlow<Unit> = _browseHistoryExhaustedEvents

    private val undoHistory = ArrayDeque<SwipeAction>(MAX_UNDO_HISTORY)

    /** Cards passed with a "do nothing" (browsing) swipe, so a left-swipe-to-go-back has something
     *  to return to. Separate from [undoHistory] on purpose: a plain browse-back must never risk
     *  un-deleting or un-moving something a *different* direction actually acted on. */
    private val browseHistory = ArrayDeque<MediaGroup>(MAX_UNDO_HISTORY)

    /** Jobs performing an in-flight "move to folder", keyed by group, so undo can cancel one that
     *  hasn't applied yet. A move that already completed can't be reversed by undo (accepted MVP
     *  limitation — see requestMove). */
    private val pendingMoveJobs = mutableMapOf<String, Job>()

    private var currentSettings: RoomieSettings = RoomieSettings()

    /** Swipes counted locally since the last [flushSwipeCount], not yet written to DataStore. Only
     *  matters for [RoomieSettings.hasReachedSwipeLimit], which is itself only ever reachable while
     *  monetization is enabled (currently unreachable from the UI — see Settings) — batching means
     *  that gate can lag by up to [SWIPE_COUNT_FLUSH_INTERVAL] swipes behind the true count, an
     *  accepted tradeoff for not hitting disk on every single swipe. */
    private var pendingSwipeIncrement = 0

    /** Identifies the folder/period/start-point [loadFolder] last actually loaded. Navigating to
     *  another screen (e.g. the Trash folder) and back disposes and recomposes the swipe screen,
     *  which re-runs its `LaunchedEffect(...) { loadFolder(...) }` with the same arguments — without
     *  this guard, that re-ran the MediaStore query and reset the position back to the start of the
     *  (filtered) folder instead of resuming where the user left off. */
    private var loadedSessionKey: String? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collectLatest { settings ->
                currentSettings = settings
                _uiState.update {
                    it.copy(
                        sessionSwipeCount = settings.sessionSwipeCount,
                        freeSwipeLimit = settings.freeSwipeLimit,
                        hasReachedLimit = settings.hasReachedSwipeLimit,
                        cardAnimationStyle = settings.cardAnimationStyle,
                        edgePaddingDp = settings.edgePaddingDp,
                        monetizationEnabled = settings.monetizationEnabled,
                        gesturePreset = SwipeGesturePreset.matching(settings),
                    )
                }
            }
        }
        viewModelScope.launch {
            trashRepository.observeTrashedCount().collectLatest { count ->
                _uiState.update { it.copy(trashedCount = count) }
            }
        }
    }

    /**
     * [startAtStableId], when given, skips straight to that item (and everything after it) instead
     * of the beginning — used when the user picks a specific photo off the folder's full grid
     * rather than starting the swipe session cold.
     */
    fun loadFolder(bucketId: Long?, displayName: String, period: PeriodFilter, startAtStableId: String? = null) {
        val sessionKey = "$bucketId|$displayName|$period|$startAtStableId"
        if (sessionKey == loadedSessionKey) return
        loadedSessionKey = sessionKey
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, folderName = displayName, isStackExhausted = false) }
            undoHistory.clear()
            browseHistory.clear()
            pendingMoveJobs.clear()
            val sortOrder = settingsRepository.settings.first().sortOrder
            // A swipe-deleted photo is still physically on disk (see TrashRepository) until the
            // user empties the trash, so it must be filtered out here or it would just show back
            // up the next time this folder is browsed.
            val trashedIds = trashRepository.getTrashedStableIds()
            val groups = mediaRepository.getMediaGroups(bucketId, period, sortOrder)
                .filterNot { group -> group.items.any { it.stableId in trashedIds } }
            val startIndex = startAtStableId
                ?.let { id -> groups.indexOfFirst { group -> group.items.any { it.stableId == id } } }
                ?.takeIf { it >= 0 }
                ?: 0
            val stack = groups.drop(startIndex)
            _uiState.update {
                it.copy(
                    stack = stack,
                    isLoading = false,
                    canUndo = false,
                    isStackExhausted = stack.isEmpty(),
                    totalCount = groups.size,
                    deletedCount = 0,
                    deletedBytes = 0L,
                    keptCount = 0,
                    postponedCount = 0,
                )
            }
        }
    }

    private fun actionFor(direction: SwipeDirection): SwipeCardAction = when (direction) {
        SwipeDirection.LEFT -> currentSettings.swipeLeftAction
        SwipeDirection.RIGHT -> currentSettings.swipeRightAction
        SwipeDirection.UP -> currentSettings.swipeUpAction
        SwipeDirection.DOWN -> currentSettings.swipeDownAction
    }

    fun swipe(direction: SwipeDirection) {
        val state = _uiState.value
        val group = state.currentGroup ?: return
        if (state.hasReachedLimit) return

        val action = actionFor(direction)

        // Plain browsing (no decision either way): left goes back to what you just saw instead of
        // advancing like every other direction/action does. Unconditionally returns either way —
        // falling through to the forward-advance code below when there's nothing to go back to was
        // the bug: it counted as a "do nothing" swipe on the *current* card, pushing it onto
        // browseHistory and advancing past it, so the very next left-swipe pulled that same card
        // right back — an A/B loop instead of just stopping at the start of the session.
        if (direction == SwipeDirection.LEFT && action == SwipeCardAction.NONE) {
            val previous = browseHistory.removeLastOrNull()
            if (previous != null) {
                _uiState.update { it.copy(stack = listOf(previous) + it.stack, isStackExhausted = false) }
            } else {
                viewModelScope.launch { _browseHistoryExhaustedEvents.emit(Unit) }
            }
            return
        }

        when (action) {
            // Trashed immediately, no confirmation needed — see TrashRepository's class doc for
            // why this is safe (it never touches the real file or MediaStore).
            SwipeCardAction.DELETE -> {
                viewModelScope.launch { trashRepository.recordTrashed(listOf(group), currentSettings.trashRetentionDays) }
            }
            SwipeCardAction.MOVE_TO_FOLDER -> requestMove(group)
            SwipeCardAction.NONE -> {
                if (browseHistory.size >= MAX_UNDO_HISTORY) browseHistory.removeFirst()
                browseHistory.addLast(group)
            }
            SwipeCardAction.KEEP, SwipeCardAction.POSTPONE -> Unit
        }

        pushUndo(SwipeAction(group, action))

        _uiState.update {
            val rest = it.stack.drop(1)
            // Postponing keeps the card in this session's queue, just at the back of it.
            val newStack = if (action == SwipeCardAction.POSTPONE) rest + group else rest
            it.copy(
                stack = newStack,
                canUndo = undoHistory.isNotEmpty(),
                isStackExhausted = newStack.isEmpty(),
                deletedCount = it.deletedCount + if (action == SwipeCardAction.DELETE) 1 else 0,
                deletedBytes = it.deletedBytes + if (action == SwipeCardAction.DELETE) group.totalSizeBytes else 0L,
                keptCount = it.keptCount + if (action == SwipeCardAction.KEEP ||
                    action == SwipeCardAction.NONE ||
                    action == SwipeCardAction.MOVE_TO_FOLDER
                ) {
                    1
                } else {
                    0
                },
                postponedCount = it.postponedCount + if (action == SwipeCardAction.POSTPONE) 1 else 0,
            )
        }

        pendingSwipeIncrement++
        if (pendingSwipeIncrement >= SWIPE_COUNT_FLUSH_INTERVAL) flushSwipeCount()
    }

    /** Writes accumulated swipes to DataStore in one edit and resets the local counter. Uses
     *  [NonCancellable] so the final flush from [onCleared] still lands even though by the time
     *  that runs, viewModelScope's own Job has already been cancelled (cancelling it is part of how
     *  ViewModel.clear() works, and happens before onCleared() is even called) — a plain
     *  viewModelScope.launch there would be a child of an already-cancelled Job and never run. */
    private fun flushSwipeCount() {
        val toFlush = pendingSwipeIncrement
        if (toFlush <= 0) return
        pendingSwipeIncrement = 0
        viewModelScope.launch(NonCancellable) { settingsRepository.incrementSessionSwipeCount(toFlush) }
    }

    override fun onCleared() {
        flushSwipeCount()
    }

    fun undo() {
        val action = undoHistory.removeLastOrNull() ?: return
        when (action.action) {
            SwipeCardAction.DELETE -> viewModelScope.launch { trashRepository.cancelPendingTrash(action.group.items) }
            SwipeCardAction.MOVE_TO_FOLDER -> pendingMoveJobs.remove(action.group.key)?.cancel()
            SwipeCardAction.KEEP, SwipeCardAction.NONE, SwipeCardAction.POSTPONE -> Unit
        }
        _uiState.update {
            // A postponed card is already somewhere in the stack (at the back); drop that copy
            // before reinserting it at the front so undo doesn't leave it in the queue twice.
            val withoutPostponedCopy = if (action.action == SwipeCardAction.POSTPONE) {
                it.stack.filterNot { group -> group.key == action.group.key }
            } else {
                it.stack
            }
            it.copy(
                stack = listOf(action.group) + withoutPostponedCopy,
                canUndo = undoHistory.isNotEmpty(),
                isStackExhausted = false,
                deletedCount = it.deletedCount - if (action.action == SwipeCardAction.DELETE) 1 else 0,
                deletedBytes = it.deletedBytes - if (action.action == SwipeCardAction.DELETE) action.group.totalSizeBytes else 0L,
                keptCount = it.keptCount - if (action.action == SwipeCardAction.KEEP ||
                    action.action == SwipeCardAction.NONE ||
                    action.action == SwipeCardAction.MOVE_TO_FOLDER
                ) {
                    1
                } else {
                    0
                },
                postponedCount = it.postponedCount - if (action.action == SwipeCardAction.POSTPONE) 1 else 0,
            )
        }
    }

    /**
     * Requests the "move to folder" write access (API 30+ needs one system dialog per group, like
     * the trash flow) and applies it once granted. Runs in its own tracked [Job] so [undo] can
     * cancel it if the user changes their mind before it lands.
     */
    private fun requestMove(group: MediaGroup) {
        val job = viewModelScope.launch {
            val bucketId = currentSettings.moveToFolderBucketId ?: return@launch
            val targetPath = mediaRepository.getRelativePathForBucket(bucketId) ?: return@launch
            val intentSender = mediaRepository.buildMoveRequest(group.allUris)
            if (intentSender != null) {
                _moveConfirmationEvents.emit(MoveConfirmationRequest(intentSender, group, targetPath))
            } else {
                mediaRepository.applyMove(group.allUris, targetPath)
            }
        }
        pendingMoveJobs[group.key] = job
        job.invokeOnCompletion { pendingMoveJobs.remove(group.key, job) }
    }

    /** Called once the system write-access dialog (if any) has been confirmed. */
    fun onMoveConfirmed(request: MoveConfirmationRequest) {
        viewModelScope.launch { mediaRepository.applyMove(request.group.allUris, request.targetRelativePath) }
    }

    /** Builds the end-of-session summary from what actually happened this pass over the folder —
     *  called right as the stack empties out, before navigating to the Summary screen. */
    fun prepareSessionSummary() {
        val state = _uiState.value
        _summaryState.value = SummaryUiState(itemCount = state.deletedCount, freedBytes = state.deletedBytes)
        // Any not-yet-flushed swipes from this same session must not survive this reset — otherwise
        // a later flush (e.g. onCleared() once the user leaves the summary screen) would add them
        // back on top of the 0 this just wrote.
        pendingSwipeIncrement = 0
        viewModelScope.launch { settingsRepository.resetSessionSwipeCount() }
    }

    fun clearSummary() {
        _summaryState.value = null
    }

    suspend fun unlockViaRewardedAd(): Boolean {
        val result = monetizationGateway.showRewardedAd()
        if (result == RewardResult.GRANTED) {
            pendingSwipeIncrement = 0
            settingsRepository.resetSessionSwipeCount()
        }
        return result == RewardResult.GRANTED
    }

    suspend fun unlockViaPurchase(): Boolean {
        val result = monetizationGateway.launchOneTimePurchase()
        if (result == PurchaseResult.PURCHASED) settingsRepository.unlockPremium()
        return result == PurchaseResult.PURCHASED
    }

    private fun pushUndo(action: SwipeAction) {
        if (undoHistory.size >= MAX_UNDO_HISTORY) undoHistory.removeFirst()
        undoHistory.addLast(action)
    }
}
