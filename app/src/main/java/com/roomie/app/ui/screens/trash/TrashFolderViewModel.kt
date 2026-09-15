package com.roomie.app.ui.screens.trash

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roomie.app.data.db.TrashEntry
import com.roomie.app.data.trash.TrashRepository
import kotlinx.coroutines.flow.MutableSharedFlow
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

    fun restore(entry: TrashEntry) {
        viewModelScope.launch { trashRepository.restoreFromTrash(listOf(entry)) }
    }

    /** Permanently deletes [entries] now instead of waiting out the retention countdown — the
     *  "empty trash" action passes every current entry. */
    fun requestDeleteForever(entries: List<TrashEntry>) {
        if (entries.isEmpty()) return
        viewModelScope.launch {
            val intentSender = trashRepository.buildDeleteRequest(entries)
            if (intentSender != null) {
                _deleteConfirmationEvents.emit(TrashDeleteRequest(intentSender, entries))
            } else {
                trashRepository.permanentlyDelete(entries)
            }
        }
    }

    /** Called once the system delete-confirmation dialog (API 30+) has been confirmed. */
    fun onDeleteConfirmed(entries: List<TrashEntry>) {
        viewModelScope.launch { trashRepository.permanentlyDelete(entries) }
    }
}
