package com.roomie.app.ui.screens.trash

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roomie.app.data.db.TrashEntry
import com.roomie.app.data.trash.TrashRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrashDeleteRequest(val intentSender: IntentSender, val entries: List<TrashEntry>)

/**
 * Backs the persistent "Trash" folder reachable from the main screen — everything Roomie has
 * trashed and not yet permanently deleted, independent of any particular swipe session.
 */
class TrashFolderViewModel(private val trashRepository: TrashRepository) : ViewModel() {

    val entries: StateFlow<List<TrashEntry>> = trashRepository.observeTrash().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _deleteConfirmationEvents = MutableSharedFlow<TrashDeleteRequest>()
    val deleteConfirmationEvents: SharedFlow<TrashDeleteRequest> = _deleteConfirmationEvents

    /** Guards against two overlapping delete requests — e.g. the expired-entries auto-trigger and
     *  a manual "empty trash" tap both firing around the same Room update — which would otherwise
     *  call `deleteLauncher.launch()` a second time before the first system dialog has returned a
     *  result, crashing the ActivityResultLauncher. Cleared in [onDeleteConfirmed] and by
     *  [onDeleteFlowCancelled] when the caller's launcher gets a non-OK result (e.g. cancelled). */
    private var deleteInFlight = false

    /** (done, total) while a permanent delete is running, null otherwise — lets the screen show a
     *  live counter and, more importantly, refuse to let the user navigate away mid-delete: since
     *  the delete runs in [viewModelScope], leaving the screen would tear down this ViewModel and
     *  abandon the loop partway through. */
    private val _deleteProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val deleteProgress: StateFlow<Pair<Int, Int>?> = _deleteProgress

    fun restore(entry: TrashEntry) {
        viewModelScope.launch { trashRepository.restoreFromTrash(listOf(entry)) }
    }

    /** Permanently deletes [entries] now instead of waiting out the retention countdown — the
     *  "empty trash" action passes every current entry. */
    fun requestDeleteForever(entries: List<TrashEntry>) {
        if (entries.isEmpty() || deleteInFlight) return
        deleteInFlight = true
        viewModelScope.launch {
            try {
                // valid excludes anything that no longer resolves in MediaStore (already deleted
                // or replaced outside Roomie) — buildDeleteRequest drops those from Room itself,
                // so confirmation/deletion below only ever sees entries that still exist.
                val (valid, intentSender) = trashRepository.buildDeleteRequest(entries)
                if (intentSender != null) {
                    _deleteConfirmationEvents.emit(TrashDeleteRequest(intentSender, valid))
                } else if (valid.isNotEmpty()) {
                    _deleteProgress.value = 0 to valid.size
                    trashRepository.permanentlyDelete(valid) { done, total -> _deleteProgress.value = done to total }
                    _deleteProgress.value = null
                    deleteInFlight = false
                } else {
                    deleteInFlight = false
                }
            } catch (e: Throwable) {
                _deleteProgress.value = null
                deleteInFlight = false
                throw e
            }
        }
    }

    /** Called once the system delete-confirmation dialog (API 30+) has been confirmed. */
    fun onDeleteConfirmed(entries: List<TrashEntry>) {
        viewModelScope.launch {
            _deleteProgress.value = 0 to entries.size
            trashRepository.permanentlyDelete(entries) { done, total -> _deleteProgress.value = done to total }
            _deleteProgress.value = null
            deleteInFlight = false
        }
    }

    /** Called when the delete-confirmation dialog was dismissed/cancelled instead of confirmed,
     *  so a cancelled dialog doesn't block every future delete attempt forever. */
    fun onDeleteFlowCancelled() {
        deleteInFlight = false
    }
}
