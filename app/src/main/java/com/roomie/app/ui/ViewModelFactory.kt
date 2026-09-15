package com.roomie.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.roomie.app.AppContainer
import com.roomie.app.ui.screens.foldergrid.FolderGridViewModel
import com.roomie.app.ui.screens.folders.FolderListViewModel
import com.roomie.app.ui.screens.settings.SettingsViewModel
import com.roomie.app.ui.screens.swipe.SwipeSessionViewModel
import com.roomie.app.ui.screens.trash.TrashFolderViewModel

/** Manual DI: builds each screen's ViewModel from the [AppContainer] the Application owns. */
class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when (modelClass) {
        FolderListViewModel::class.java ->
            FolderListViewModel(container.mediaRepository, container.trashRepository) as T

        FolderGridViewModel::class.java ->
            FolderGridViewModel(container.mediaRepository, container.settingsRepository, container.trashRepository) as T

        SwipeSessionViewModel::class.java ->
            SwipeSessionViewModel(
                mediaRepository = container.mediaRepository,
                trashRepository = container.trashRepository,
                settingsRepository = container.settingsRepository,
                monetizationGateway = container.monetizationGateway,
            ) as T

        SettingsViewModel::class.java ->
            SettingsViewModel(container.settingsRepository, container.mediaRepository) as T

        TrashFolderViewModel::class.java ->
            TrashFolderViewModel(container.trashRepository) as T

        else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
