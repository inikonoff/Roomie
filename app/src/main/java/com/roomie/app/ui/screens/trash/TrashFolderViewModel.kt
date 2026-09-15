package com.roomie.app.ui.screens.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roomie.app.data.db.TrashEntry
import com.roomie.app.data.trash.TrashRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun restore(entry: TrashEntry) {
        viewModelScope.launch { trashRepository.restoreFromTrash(listOf(entry)) }
    }
}
