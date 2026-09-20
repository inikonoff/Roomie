package com.roomie.app.ui.screens.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roomie.app.data.media.GalleryFolder
import com.roomie.app.data.media.MediaRepository
import com.roomie.app.data.trash.TrashRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FolderListUiState(
    val folders: List<GalleryFolder> = emptyList(),
    val isLoading: Boolean = true,
    /** A re-fetch that isn't the very first one — pull-to-refresh, or simply returning to this
     *  screen from swipe/summary/trash. Shows a small indicator instead of unmounting the grid
     *  back to a full-screen spinner and losing scroll position. */
    val isRefreshing: Boolean = false,
    val hasMediaPermission: Boolean = true,
)

class FolderListViewModel(
    private val mediaRepository: MediaRepository,
    private val trashRepository: TrashRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FolderListUiState())
    val uiState: StateFlow<FolderListUiState> = _uiState.asStateFlow()

    val trashCount: StateFlow<Int> = trashRepository.observeTrash().map { it.size }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasMediaPermission = granted) }
        if (granted) refresh()
    }

    /** Called on every entry to this screen — including a pop-back from swipe/summary/trash, where
     *  Navigation Compose recreates the composition and re-fires its LaunchedEffect(Unit) ->
     *  onPermissionResult() — and by pull-to-refresh. Always re-fetches now: the previous
     *  once-ever guard here meant folder tiles never updated after the very first load, so a tile's
     *  count/cover could sit stale (and, worse, disagree with the corresponding folder's own grid,
     *  which always re-queries) for the rest of the app's life. Only the very first call — before
     *  any folders have ever loaded — shows the full-screen spinner; every later call is quiet,
     *  scroll position untouched, via [FolderListUiState.isRefreshing] instead. */
    fun refresh() {
        val state = _uiState.value
        if (!state.hasMediaPermission || state.isLoading || state.isRefreshing) return
        val firstLoad = state.folders.isEmpty()
        viewModelScope.launch {
            _uiState.update { if (firstLoad) it.copy(isLoading = true) else it.copy(isRefreshing = true) }
            val trashedIds = trashRepository.getTrashedStableIds()
            val folders = mediaRepository.getFolders(trashedIds)
            _uiState.update { it.copy(folders = folders, isLoading = false, isRefreshing = false) }
        }
    }
}
