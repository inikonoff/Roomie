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
    val period: PeriodFilter = PeriodFilter.ALL,
    val isLoading: Boolean = true,
)

/** Backs the "see everything first" grid shown when entering a folder, before committing to a
 *  swipe session starting at whichever photo the user taps. The period filter lives here (not on
 *  the folder list) and is remembered per folder via [SettingsRepository] — each folder keeps its
 *  own last-used filter across app restarts. */
class FolderGridViewModel(
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderGridUiState())
    val uiState: StateFlow<FolderGridUiState> = _uiState.asStateFlow()

    // Navigation Compose recreates this screen's composition on every return to it, re-firing
    // LaunchedEffect(bucketId, displayName) → load() even for the same folder. Reloading the full
    // (expensive) media query unconditionally on every such re-entry unmounted the grid to a
    // spinner and back, scrolled to the top — so it's still guarded by loadedKey. But comparing
    // only the key meant a Trash visit mid-session (delete/empty, still the same folder) never
    // refreshed the grid at all: it stayed on the item list from the first load, showing tiles for
    // files that no longer existed. loadedTrashedIds is checked on every re-entry (a cheap Room
    // query) specifically to catch that case and trigger the expensive reload only when the trash
    // set actually changed.
    private var loadedKey: String? = null
    private var loadedTrashedIds: Set<String> = emptySet()

    fun load(bucketId: Long?, displayName: String) {
        val key = "$bucketId|$displayName"
        viewModelScope.launch {
            val currentTrashedIds = trashRepository.getTrashedStableIds()
            if (key == loadedKey && currentTrashedIds == loadedTrashedIds) return@launch
            loadedKey = key
            loadedTrashedIds = currentTrashedIds
            val savedPeriod = settingsRepository.getFolderPeriodFilter(bucketId)
            loadWithPeriod(bucketId, savedPeriod, currentTrashedIds)
        }
    }

    fun onPeriodSelected(bucketId: Long?, period: PeriodFilter) {
        viewModelScope.launch {
            settingsRepository.setFolderPeriodFilter(bucketId, period)
            loadedTrashedIds = trashRepository.getTrashedStableIds()
            loadWithPeriod(bucketId, period, loadedTrashedIds)
        }
    }

    private suspend fun loadWithPeriod(bucketId: Long?, period: PeriodFilter, trashedIds: Set<String>) {
        _uiState.update { it.copy(isLoading = true, period = period) }
        val sortOrder = settingsRepository.settings.first().sortOrder
        // A swipe-deleted photo stays on disk until the trash is emptied, so it must be
        // excluded here too or it would still show up when browsing the folder grid.
        val groups = mediaRepository.getMediaGroups(bucketId, period, sortOrder)
            .filterNot { group -> group.items.any { it.stableId in trashedIds } }
        _uiState.update { it.copy(groups = groups, isLoading = false) }
    }
}
