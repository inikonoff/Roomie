package com.roomie.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roomie.app.data.media.GalleryFolder
import com.roomie.app.data.media.MediaRepository
import com.roomie.app.data.media.SortOrder
import com.roomie.app.data.settings.CardAnimationStyle
import com.roomie.app.data.settings.LanguageMode
import com.roomie.app.data.settings.RoomieSettings
import com.roomie.app.data.settings.SettingsRepository
import com.roomie.app.data.settings.SwipeCardAction
import com.roomie.app.data.settings.ThemeMode
import com.roomie.app.ui.components.ThumbnailDiskCache
import com.roomie.app.ui.strings.AppStrings
import kotlinx.coroutines.Dispatchers
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

    /** Live size of [ThumbnailDiskCache], not a persisted setting — just disk state recomputed on
     *  request (screen open, after a clear) rather than something to keep in RoomieSettings/DataStore. */
    private val _thumbnailCacheBytes = MutableStateFlow(0L)
    val thumbnailCacheBytes: StateFlow<Long> = _thumbnailCacheBytes.asStateFlow()

    init {
        viewModelScope.launch { _folders.value = mediaRepository.getFolders() }
    }

    fun refreshThumbnailCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _thumbnailCacheBytes.value = ThumbnailDiskCache.sizeBytes(context)
        }
    }

    fun clearThumbnailCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ThumbnailDiskCache.clear(context)
            _thumbnailCacheBytes.value = 0L
        }
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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCardAnimationStyle(style: CardAnimationStyle) {
        viewModelScope.launch { settingsRepository.setCardAnimationStyle(style) }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch { settingsRepository.setLanguageMode(mode) }
    }

    fun setEdgePaddingDp(value: Int) {
        viewModelScope.launch { settingsRepository.setEdgePaddingDp(value) }
    }

    fun applyGesturePreset(preset: SwipeGesturePreset) {
        viewModelScope.launch {
            settingsRepository.setSwipeActions(
                left = preset.left,
                right = preset.right,
                up = preset.up,
                down = preset.down,
            )
        }
    }
}

/** Ready-made direction -> action mappings offered as one-tap presets in Settings; the four
 *  underlying settings stay individually editable below regardless of which preset was last used. */
enum class SwipeGesturePreset(
    val left: SwipeCardAction,
    val right: SwipeCardAction,
    val up: SwipeCardAction,
    val down: SwipeCardAction,
) {
    CLASSIC(SwipeCardAction.DELETE, SwipeCardAction.KEEP, SwipeCardAction.MOVE_TO_FOLDER, SwipeCardAction.POSTPONE),
    BROWSE_AND_DELETE(SwipeCardAction.NONE, SwipeCardAction.NONE, SwipeCardAction.DELETE, SwipeCardAction.POSTPONE),
    ;

    companion object {
        /** Which preset (if any) is active, judged by left/right alone — those are the two
         *  directions SettingsScreen locks while a preset is selected (see `leftRightLocked`
         *  there), and up/down stay freely editable under either preset. Comparing all four
         *  directions here used to mean editing up/down alone made the tuple stop matching either
         *  preset, silently unlocking left/right and dropping the Classic/Browse highlight even
         *  though nothing about the locked directions had changed. */
        fun matching(settings: RoomieSettings): SwipeGesturePreset? = entries.find {
            it.left == settings.swipeLeftAction && it.right == settings.swipeRightAction
        }
    }
}

/** Public (not SettingsScreen-private) so SwipeScreen can show the active preset's name as a
 *  subtitle without duplicating this mapping. */
fun SwipeGesturePreset.label(strings: AppStrings): String = when (this) {
    SwipeGesturePreset.CLASSIC -> strings.presetClassic
    SwipeGesturePreset.BROWSE_AND_DELETE -> strings.presetBrowseDeleteUp
}
