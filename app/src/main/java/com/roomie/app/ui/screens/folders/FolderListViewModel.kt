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
    val hasMediaPermission: Boolean = true,
)

class FolderListViewModel(
    private val mediaRepository: MediaRepository,
    trashRepository: TrashRepository,
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

    fun refresh() {
        if (!_uiState.value.hasMediaPermission) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val folders = mediaRepository.getFolders()
            _uiState.update { it.copy(folders = folders, isLoading = false) }
        }
    }
}
