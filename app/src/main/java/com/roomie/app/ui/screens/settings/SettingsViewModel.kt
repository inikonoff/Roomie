package com.roomie.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roomie.app.data.media.GalleryFolder
import com.roomie.app.data.media.MediaRepository
import com.roomie.app.data.media.SortOrder
import com.roomie.app.data.settings.RoomieSettings
import com.roomie.app.data.settings.SettingsRepository
import com.roomie.app.data.settings.SwipeCardAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    val settings: StateFlow<RoomieSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RoomieSettings(),
    )

    private val _folders = MutableStateFlow<List<GalleryFolder>>(emptyList())
    val folders: StateFlow<List<GalleryFolder>> = _folders.asStateFlow()

    init {
        viewModelScope.launch { _folders.value = mediaRepository.getFolders() }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settingsRepository.setSortOrder(order) }
    }

    fun setTrashRetentionDays(days: Int) {
        viewModelScope.launch { settingsRepository.setTrashRetentionDays(days) }
    }

    fun setAutoDeleteEmptyFolders(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoDeleteEmptyFolders(enabled) }
    }

    fun setMonetizationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMonetizationEnabled(enabled) }
    }

    fun setSwipeLeftAction(action: SwipeCardAction) {
        viewModelScope.launch { settingsRepository.setSwipeLeftAction(action) }
    }

    fun setSwipeRightAction(action: SwipeCardAction) {
        viewModelScope.launch { settingsRepository.setSwipeRightAction(action) }
    }

    fun setSwipeUpAction(action: SwipeCardAction) {
        viewModelScope.launch { settingsRepository.setSwipeUpAction(action) }
    }

    fun setSwipeDownAction(action: SwipeCardAction) {
        viewModelScope.launch { settingsRepository.setSwipeDownAction(action) }
    }

    fun setMoveToFolder(folder: GalleryFolder) {
        viewModelScope.launch { settingsRepository.setMoveToFolder(folder.bucketId, folder.displayName) }
    }
}
