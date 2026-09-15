package com.roomie.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roomie.app.data.media.GalleryFolder
import com.roomie.app.data.media.SortOrder
import com.roomie.app.data.settings.CardAnimationStyle
import com.roomie.app.data.settings.LanguageMode
import com.roomie.app.data.settings.RoomieSettings
import com.roomie.app.data.settings.SwipeCardAction
import com.roomie.app.data.settings.ThemeMode
import com.roomie.app.ui.strings.AppStrings
import com.roomie.app.ui.strings.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val strings = LocalAppStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionTitle(strings.sectionTheme)
            ThemeModeSelector(strings, settings.themeMode, viewModel::setThemeMode)

            SectionTitle(strings.sectionLanguage)
            LanguageModeSelector(strings, settings.languageMode, viewModel::setLanguageMode)

            SectionTitle(strings.sectionCardOrder)
            SortOrderSelector(strings, settings.sortOrder, viewModel::setSortOrder)

            SectionTitle(strings.sectionTrashRetention)
            RetentionSelector(strings, settings.trashRetentionDays, viewModel::setTrashRetentionDays)

            SectionTitle(strings.sectionCardAnimation)
            CardAnimationStyleSelector(strings, settings.cardAnimationStyle, viewModel::setCardAnimationStyle)

            SectionTitle(strings.sectionSwipeGestures)
            GesturePresetRow(
                strings = strings,
                selected = SwipeGesturePreset.matching(settings),
                onPresetSelected = viewModel::applyGesturePreset,
            )
            SwipeActionRow(strings.swipeRight, strings, settings.swipeRightAction, viewModel::setSwipeRightAction)
            SwipeActionRow(strings.swipeLeft, strings, settings.swipeLeftAction, viewModel::setSwipeLeftAction)
            SwipeActionRow(strings.swipeUp, strings, settings.swipeUpAction, viewModel::setSwipeUpAction)
            SwipeActionRow(strings.swipeDown, strings, settings.swipeDownAction, viewModel::setSwipeDownAction)
            MoveToFolderRow(strings, settings.moveToFolderName, folders, viewModel::setMoveToFolder)

            SwitchRow(
                title = strings.autoDeleteEmptyFolders,
                checked = settings.autoDeleteEmptyFolders,
                onCheckedChange = viewModel::setAutoDeleteEmptyFolders,
            )

            SwitchRow(
                title = strings.enableSwipeLimit,
                subtitle = if (settings.isPremiumUnlocked) strings.unlockedLimitDisabled else null,
                checked = settings.monetizationEnabled,
                onCheckedChange = viewModel::setMonetizationEnabled,
            )
        }
    }
}

/** A segmented-button label that can never wrap to a second line and spill past the button's
 *  fixed height into whatever is below it — Material3's SegmentedButton doesn't grow for
 *  multi-line content, so without this a longer translation (Russian labels run noticeably
 *  longer than English ones) visually overlapped the row underneath instead of just wrapping. */
@Composable
private fun SegmentedLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

private fun ThemeMode.label(strings: AppStrings): String = when (this) {
    ThemeMode.LIGHT -> strings.themeLight
    ThemeMode.DARK -> strings.themeDark
    ThemeMode.SYSTEM -> strings.themeSystem
}

private fun LanguageMode.label(strings: AppStrings): String = when (this) {
    LanguageMode.SYSTEM -> strings.languageSystem
    LanguageMode.ENGLISH -> strings.languageEnglish
    LanguageMode.RUSSIAN -> strings.languageRussian
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(strings: AppStrings, current: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
            ) {
                SegmentedLabel(mode.label(strings))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageModeSelector(strings: AppStrings, current: LanguageMode, onSelected: (LanguageMode) -> Unit) {
    val options = LanguageMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
            ) {
                SegmentedLabel(mode.label(strings))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOrderSelector(strings: AppStrings, current: SortOrder, onSelected: (SortOrder) -> Unit) {
    val options = listOf(
        SortOrder.NEWEST_FIRST to strings.sortNewestFirst,
        SortOrder.OLDEST_FIRST to strings.sortOldestFirst,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (order, label) ->
            SegmentedButton(
                selected = current == order,
                onClick = { onSelected(order) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
            ) {
                SegmentedLabel(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionSelector(strings: AppStrings, currentDays: Int, onSelected: (Int) -> Unit) {
    val options = RoomieSettings.ALLOWED_RETENTION_DAYS
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, days ->
            SegmentedButton(
                selected = currentDays == days,
                onClick = { onSelected(days) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
            ) {
                SegmentedLabel(strings.retentionDays(days))
            }
        }
    }
}

private fun SwipeCardAction.label(strings: AppStrings): String = when (this) {
    SwipeCardAction.DELETE -> strings.actionDelete
    SwipeCardAction.KEEP -> strings.actionKeep
    SwipeCardAction.MOVE_TO_FOLDER -> strings.actionMoveToFolder
    SwipeCardAction.POSTPONE -> strings.actionPostpone
    SwipeCardAction.NONE -> strings.actionNone
}

private fun CardAnimationStyle.label(strings: AppStrings): String = when (this) {
    CardAnimationStyle.CLASSIC -> strings.animationClassic
    CardAnimationStyle.FADE -> strings.animationFade
    CardAnimationStyle.SHRINK -> strings.animationShrink
}

private fun SwipeGesturePreset.label(strings: AppStrings): String = when (this) {
    SwipeGesturePreset.CLASSIC -> strings.presetClassic
    SwipeGesturePreset.BROWSE_AND_DELETE -> strings.presetBrowseDeleteUp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardAnimationStyleSelector(
    strings: AppStrings,
    current: CardAnimationStyle,
    onSelected: (CardAnimationStyle) -> Unit,
) {
    val options = CardAnimationStyle.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, style ->
            SegmentedButton(
                selected = current == style,
                onClick = { onSelected(style) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
            ) {
                SegmentedLabel(style.label(strings))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GesturePresetRow(
    strings: AppStrings,
    selected: SwipeGesturePreset?,
    onPresetSelected: (SwipeGesturePreset) -> Unit,
) {
    val options = SwipeGesturePreset.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        options.forEachIndexed { index, preset ->
            SegmentedButton(
                selected = selected == preset,
                onClick = { onPresetSelected(preset) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
            ) {
                SegmentedLabel(preset.label(strings))
            }
        }
    }
}

@Composable
private fun SwipeActionRow(
    label: String,
    strings: AppStrings,
    current: SwipeCardAction,
    onSelected: (SwipeCardAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                current.label(strings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SwipeCardAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label(strings)) },
                    onClick = {
                        onSelected(action)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun MoveToFolderRow(
    strings: AppStrings,
    currentName: String?,
    folders: List<GalleryFolder>,
    onSelected: (GalleryFolder) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(strings.moveToFolderDestination, style = MaterialTheme.typography.bodyLarge)
        Text(
            currentName ?: strings.notSet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(strings.chooseAFolder) },
            text = {
                if (folders.isEmpty()) {
                    Text(strings.noFoldersFoundYet)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(folders, key = { it.bucketId }) { folder ->
                            Text(
                                folder.displayName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelected(folder)
                                        showDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
