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
import kotlinx.coroutines.Job
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
    /** Size of the whole folder this session started from (including anything already skipped
     *  past via "start at this photo"), for the "12 of 345" position counter. */
    val totalCount: Int = 0,
    val deletedCount: Int = 0,
    /** Left untouched, whether by an explicit Keep or a "do nothing" browse swipe — both leave the
     *  file exactly as it was, so they share one bucket in the progress bar. */
    val keptCount: Int = 0,
    val postponedCount: Int = 0,
    val monetizationEnabled: Boolean = false,
) {
    val currentGroup: MediaGroup? get() = stack.firstOrNull()

    /** 1-based position of [currentGroup] within the original folder ordering. */
    val currentPosition: Int get() = totalCount - stack.size + 1
}

data class SummaryUiState(val itemCount: Int, val freedBytes: Long)

data class TrashConfirmationRequest(val intentSender: IntentSender, val groups: List<MediaGroup>)

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

    private val _trashConfirmationEvents = MutableSharedFlow<TrashConfirmationRequest>()
    val trashConfirmationEvents: SharedFlow<TrashConfirmationRequest> = _trashConfirmationEvents

    private val _moveConfirmationEvents = MutableSharedFlow<MoveConfirmationRequest>()
    val moveConfirmationEvents: SharedFlow<MoveConfirmationRequest> = _moveConfirmationEvents

    /** Items swiped left this session, awaiting review on the trash-preview screen. */
    private val _pendingTrash = MutableStateFlow<List<MediaGroup>>(emptyList())
    val pendingTrash: StateFlow<List<MediaGroup>> = _pendingTrash.asStateFlow()

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

    /** Identifies the folder/period/start-point [loadFolder] last actually loaded. Navigating to
     *  the trash-preview screen and back disposes and recomposes the swipe screen, which re-runs
     *  its `LaunchedEffect(...) { loadFolder(...) }` with the same arguments — without this guard,
     *  that re-ran the query against MediaStore (nothing has actually been deleted yet at that
     *  point) and wiped [pendingTrash] and the delete/keep/postpone counters back to empty, making
     *  swiped-away cards reappear and the progress bar reset. Cleared in [completeTrashing] since
     *  that's the point a re-entry into the same folder should actually see fresh (smaller) data. */
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
                        monetizationEnabled = settings.monetizationEnabled,
                    )
                }
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
            _pendingTrash.value = emptyList()
            undoHistory.clear()
            browseHistory.clear()
            pendingMoveJobs.clear()
            val sortOrder = settingsRepository.settings.first().sortOrder
            val groups = mediaRepository.getMediaGroups(bucketId, period, sortOrder)
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
        // just advancing like every other direction/action does. Falls through to a normal forward
        // advance if there's nothing to go back to yet, so the gesture never just does nothing.
        if (direction == SwipeDirection.LEFT && action == SwipeCardAction.NONE) {
            val previous = browseHistory.removeLastOrNull()
            if (previous != null) {
                _uiState.update { it.copy(stack = listOf(previous) + it.stack, isStackExhausted = false) }
                return
            }
        }

        when (action) {
            SwipeCardAction.DELETE -> _pendingTrash.update { it + group }
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

        viewModelScope.launch { settingsRepository.incrementSessionSwipeCount() }
    }

    fun undo() {
        val action = undoHistory.removeLastOrNull() ?: return
        when (action.action) {
            SwipeCardAction.DELETE -> _pendingTrash.update { it - action.group }
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

    /** Trash-preview screen: exclude an item the user un-checked (it will be kept, not deleted). */
    fun restoreFromPendingTrash(group: MediaGroup) {
        _pendingTrash.update { it - group }
    }

    fun confirmDeleteSelected(selectedGroups: List<MediaGroup>) {
        if (selectedGroups.isEmpty()) return
        viewModelScope.launch {
            val intentSender = trashRepository.buildSystemTrashRequest(selectedGroups)
            if (intentSender != null) {
                _trashConfirmationEvents.emit(TrashConfirmationRequest(intentSender, selectedGroups))
            } else {
                completeTrashing(selectedGroups)
            }
        }
    }

    fun onSystemTrashConfirmed(groups: List<MediaGroup>) {
        viewModelScope.launch { completeTrashing(groups) }
    }

    private suspend fun completeTrashing(groups: List<MediaGroup>) {
        // The folder's actual contents just changed on disk — a later re-entry (even with the
        // exact same bucket/period/start-point) needs a real reload, not the stale-guard skip.
        loadedSessionKey = null
        val retentionDays = settingsRepository.settings.first().trashRetentionDays
        trashRepository.recordTrashed(groups, retentionDays)
        _summaryState.value = SummaryUiState(
            itemCount = groups.sumOf { it.items.size },
            freedBytes = groups.sumOf { it.totalSizeBytes },
        )
        _pendingTrash.update { it - groups.toSet() }
        settingsRepository.resetSessionSwipeCount()
    }

    fun clearSummary() {
        _summaryState.value = null
    }

    suspend fun unlockViaRewardedAd(): Boolean {
        val result = monetizationGateway.showRewardedAd()
        if (result == RewardResult.GRANTED) settingsRepository.resetSessionSwipeCount()
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
