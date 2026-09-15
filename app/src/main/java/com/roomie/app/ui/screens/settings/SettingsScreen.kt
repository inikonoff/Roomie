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
import androidx.compose.ui.unit.dp
import com.roomie.app.data.media.GalleryFolder
import com.roomie.app.data.media.SortOrder
import com.roomie.app.data.settings.CardAnimationStyle
import com.roomie.app.data.settings.RoomieSettings
import com.roomie.app.data.settings.SwipeCardAction
import com.roomie.app.data.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val folders by viewModel.folders.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            SectionTitle("Theme")
            ThemeModeSelector(settings.themeMode, viewModel::setThemeMode)

            SectionTitle("Card order")
            SortOrderSelector(settings.sortOrder, viewModel::setSortOrder)

            SectionTitle("Trash retention")
            RetentionSelector(settings.trashRetentionDays, viewModel::setTrashRetentionDays)

            SectionTitle("Card animation")
            CardAnimationStyleSelector(settings.cardAnimationStyle, viewModel::setCardAnimationStyle)

            SectionTitle("Swipe gestures")
            GesturePresetRow(
                selected = SwipeGesturePreset.matching(settings),
                onPresetSelected = viewModel::applyGesturePreset,
            )
            SwipeActionRow("Swipe right", settings.swipeRightAction, viewModel::setSwipeRightAction)
            SwipeActionRow("Swipe left", settings.swipeLeftAction, viewModel::setSwipeLeftAction)
            SwipeActionRow("Swipe up", settings.swipeUpAction, viewModel::setSwipeUpAction)
            SwipeActionRow("Swipe down", settings.swipeDownAction, viewModel::setSwipeDownAction)
            MoveToFolderRow(settings.moveToFolderName, folders, viewModel::setMoveToFolder)

            SwitchRow(
                title = "Delete empty folders automatically",
                checked = settings.autoDeleteEmptyFolders,
                onCheckedChange = viewModel::setAutoDeleteEmptyFolders,
            )

            SwitchRow(
                title = "Enable swipe limit & monetization",
                subtitle = if (settings.isPremiumUnlocked) "Unlocked — limit disabled" else null,
                checked = settings.monetizationEnabled,
                onCheckedChange = viewModel::setMonetizationEnabled,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(current: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val options = ThemeMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onSelected(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(mode.label())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOrderSelector(current: SortOrder, onSelected: (SortOrder) -> Unit) {
    val options = listOf(SortOrder.NEWEST_FIRST to "Newest first", SortOrder.OLDEST_FIRST to "Oldest first")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (order, label) ->
            SegmentedButton(
                selected = current == order,
                onClick = { onSelected(order) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionSelector(currentDays: Int, onSelected: (Int) -> Unit) {
    val options = RoomieSettings.ALLOWED_RETENTION_DAYS
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, days ->
            SegmentedButton(
                selected = currentDays == days,
                onClick = { onSelected(days) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text("${days}d")
            }
        }
    }
}

private fun SwipeCardAction.label(): String = when (this) {
    SwipeCardAction.DELETE -> "Delete"
    SwipeCardAction.KEEP -> "Keep (next card)"
    SwipeCardAction.MOVE_TO_FOLDER -> "Move to folder"
    SwipeCardAction.POSTPONE -> "Postpone (later this session)"
    SwipeCardAction.NONE -> "Do nothing"
}

private fun CardAnimationStyle.label(): String = when (this) {
    CardAnimationStyle.CLASSIC -> "Classic"
    CardAnimationStyle.FADE -> "Fade"
    CardAnimationStyle.SHRINK -> "Shrink"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardAnimationStyleSelector(current: CardAnimationStyle, onSelected: (CardAnimationStyle) -> Unit) {
    val options = CardAnimationStyle.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, style ->
            SegmentedButton(
                selected = current == style,
                onClick = { onSelected(style) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(style.label())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GesturePresetRow(selected: SwipeGesturePreset?, onPresetSelected: (SwipeGesturePreset) -> Unit) {
    val options = SwipeGesturePreset.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        options.forEachIndexed { index, preset ->
            SegmentedButton(
                selected = selected == preset,
                onClick = { onPresetSelected(preset) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(preset.label)
            }
        }
    }
}

@Composable
private fun SwipeActionRow(label: String, current: SwipeCardAction, onSelected: (SwipeCardAction) -> Unit) {
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
                current.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SwipeCardAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label()) },
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
        Text("\"Move to folder\" destination", style = MaterialTheme.typography.bodyLarge)
        Text(
            currentName ?: "Not set",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Choose a folder") },
            text = {
                if (folders.isEmpty()) {
                    Text("No folders found yet.")
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
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
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
