package com.roomie.app.ui.screens.foldergrid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roomie.app.data.media.MediaGroup
import com.roomie.app.data.media.MediaRepository
import com.roomie.app.data.media.PeriodFilter
import com.roomie.app.data.settings.SettingsRepository
import com.roomie.app.data.trash.TrashRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderGridUiState(
    val groups: List<MediaGroup> = emptyList(),
    val isLoading: Boolean = true,
)

/** Backs the "see everything first" grid shown when entering a folder, before committing to a
 *  swipe session starting at whichever photo the user taps. */
class FolderGridViewModel(
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderGridUiState())
    val uiState: StateFlow<FolderGridUiState> = _uiState.asStateFlow()

    fun load(bucketId: Long?, period: PeriodFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val sortOrder = settingsRepository.settings.first().sortOrder
            // A swipe-deleted photo stays on disk until the trash is emptied, so it must be
            // excluded here too or it would still show up when browsing the folder grid.
            val trashedIds = trashRepository.getTrashedStableIds()
            val groups = mediaRepository.getMediaGroups(bucketId, period, sortOrder)
                .filterNot { group -> group.items.any { it.stableId in trashedIds } }
            _uiState.update { it.copy(groups = groups, isLoading = false) }
        }
    }
}
