package com.roomie.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roomie.app.data.media.GalleryFolder
import com.roomie.app.data.media.SortOrder
import com.roomie.app.data.settings.CardAnimationStyle
import com.roomie.app.data.settings.LanguageMode
import com.roomie.app.data.settings.RoomieSettings
import com.roomie.app.data.settings.SwipeCardAction
import com.roomie.app.data.settings.ThemeMode
import com.roomie.app.ui.screens.swipe.SwipeDirection
import com.roomie.app.ui.strings.AppStrings
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.ui.theme.ContainerShape
import com.roomie.app.util.formatBytes
import com.roomie.app.ui.theme.DeleteContainer
import com.roomie.app.ui.theme.FolderAction
import com.roomie.app.ui.theme.FolderContainer
import com.roomie.app.ui.theme.KeepContainer
import com.roomie.app.ui.theme.NoneAction
import com.roomie.app.ui.theme.PostponeContainer
import com.roomie.app.ui.theme.SurfaceMuted
import com.roomie.app.ui.theme.SwipeLeftDelete
import com.roomie.app.ui.theme.SwipePostpone
import com.roomie.app.ui.theme.SwipeRightKeep
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val thumbnailCacheBytes by viewModel.thumbnailCacheBytes.collectAsState()
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshThumbnailCacheSize(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            SettingsCard {
                SectionTitle(strings.sectionGestures)

                SubsectionTitle(strings.sectionSwipeGestures)
                val preset = SwipeGesturePreset.matching(settings)
                GesturePresetRow(
                    strings = strings,
                    selected = preset,
                    onPresetSelected = viewModel::applyGesturePreset,
                )
                PresetDescription(strings, preset)

                // Left/Right are what define a preset (see SwipeGesturePreset.matching) — letting
                // them be edited independently is exactly what made the Classic/Browse segmented
                // buttons lose their highlight after a manual tweak, since the settings no longer
                // matched either preset. Locking them while a preset is active keeps `preset`
                // (and so the highlight) always in sync with what's actually configured.
                val leftRightLocked = preset != null
                SwipeActionRow(
                    strings.swipeRight,
                    Icons.Filled.ArrowForward,
                    strings,
                    settings.swipeRightAction,
                    viewModel::setSwipeRightAction,
                    direction = SwipeDirection.RIGHT,
                    enabled = !leftRightLocked,
                )
                SwipeActionRow(
                    strings.swipeLeft,
                    Icons.Filled.ArrowBack,
                    strings,
                    settings.swipeLeftAction,
                    viewModel::setSwipeLeftAction,
                    direction = SwipeDirection.LEFT,
                    enabled = !leftRightLocked,
                )
                // Classic already uses Delete/Keep on left/right — offering them again for up/down
                // would let the same action end up assigned to more than one direction, which reads
                // as a configuration mistake more than a real choice. Browse doesn't have this
                // concern (its up/down defaults are Delete/Postpone, left/right are both NONE).
                val upDownExcluded = if (preset == SwipeGesturePreset.CLASSIC) {
                    setOf(SwipeCardAction.DELETE, SwipeCardAction.KEEP)
                } else {
                    emptySet()
                }
                SwipeActionRow(
                    strings.swipeUp,
                    Icons.Filled.ArrowUpward,
                    strings,
                    settings.swipeUpAction,
                    viewModel::setSwipeUpAction,
                    excludedActions = upDownExcluded,
                )
                SwipeActionRow(
                    strings.swipeDown,
                    Icons.Filled.ArrowDownward,
                    strings,
                    settings.swipeDownAction,
                    viewModel::setSwipeDownAction,
                    excludedActions = upDownExcluded,
                )
                MoveToFolderRow(strings, settings.moveToFolderName, folders, viewModel::setMoveToFolder)
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsCard {
                SectionTitle(strings.sectionCleanup)

                SubsectionTitle(strings.sectionCardOrder)
                SortOrderSelector(strings, settings.sortOrder, viewModel::setSortOrder)

                RetentionSelector(strings, settings.trashRetentionDays, viewModel::setTrashRetentionDays)

                SwitchRow(
                    title = strings.autoDeleteEmptyFolders,
                    checked = settings.autoDeleteEmptyFolders,
                    onCheckedChange = viewModel::setAutoDeleteEmptyFolders,
                )

                // No confirmation dialog: this only ever deletes re-derivable cache files, not user
                // data, so the extra step that trash/delete flows need would just be friction here.
                LabelValueRow(
                    label = strings.clearThumbnailCache,
                    value = formatBytes(thumbnailCacheBytes),
                    modifier = Modifier.clickable { viewModel.clearThumbnailCache(context) },
                )

                NavigationRow(label = strings.viewLogs, onClick = onOpenLogs)
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsCard {
                SectionTitle(strings.sectionAppearance)

                SubsectionTitle(strings.sectionTheme)
                ThemeModeSelector(strings, settings.themeMode, viewModel::setThemeMode)

                SubsectionTitle(strings.sectionLanguage)
                LanguageModeSelector(strings, settings.languageMode, viewModel::setLanguageMode)

                SubsectionTitle(strings.sectionCardAnimation)
                CardAnimationStyleSelector(strings, settings.cardAnimationStyle, viewModel::setCardAnimationStyle)

                EdgePaddingSlider(strings, settings.edgePaddingDp, viewModel::setEdgePaddingDp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // TODO: временный блок для подбора радиуса/обводки, убрать после решения
            SettingsCard {
                SectionTitle(strings.tempCardTuningSection)
                StepperRow(
                    label = strings.cardCornerRadius,
                    value = "${settings.cardCornerRadiusDp}",
                    onDecrement = {
                        viewModel.setCardCornerRadiusDp(
                            (settings.cardCornerRadiusDp - 1).coerceAtLeast(RoomieSettings.MIN_CARD_CORNER_RADIUS_DP),
                        )
                    },
                    onIncrement = {
                        viewModel.setCardCornerRadiusDp(
                            (settings.cardCornerRadiusDp + 1).coerceAtMost(RoomieSettings.MAX_CARD_CORNER_RADIUS_DP),
                        )
                    },
                )
                StepperRow(
                    label = strings.cardBorderWidth,
                    value = "%.1f".format(settings.cardBorderWidthDp),
                    onDecrement = {
                        viewModel.setCardBorderWidthDp(
                            (settings.cardBorderWidthDp - 0.5f).coerceAtLeast(RoomieSettings.MIN_CARD_BORDER_WIDTH_DP),
                        )
                    },
                    onIncrement = {
                        viewModel.setCardBorderWidthDp(
                            (settings.cardBorderWidthDp + 0.5f).coerceAtMost(RoomieSettings.MAX_CARD_BORDER_WIDTH_DP),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = ContainerShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
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
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun SubsectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
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

@Composable
private fun RetentionSelector(strings: AppStrings, currentDays: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelValueRow(
                label = strings.sectionTrashRetention,
                value = strings.retentionDays(currentDays),
                modifier = Modifier.weight(1f),
            )
            ChevronIcon()
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            RoomieSettings.ALLOWED_RETENTION_DAYS.forEach { days ->
                DropdownMenuItem(
                    text = { Text(strings.retentionDays(days)) },
                    onClick = {
                        onSelected(days)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** [direction] only matters for [SwipeCardAction.NONE]: in Browse mode, a left swipe with no
 *  configured action steps back to the previous photo and a right swipe advances to the next one
 *  (see [com.roomie.app.ui.screens.swipe.SwipeSessionViewModel]'s browse history) — "Do nothing"
 *  is misleading for either; every other action's label is direction-independent. */
private fun SwipeCardAction.label(strings: AppStrings, direction: SwipeDirection? = null): String = when (this) {
    SwipeCardAction.NONE -> when (direction) {
        SwipeDirection.LEFT -> strings.actionPreviousPhoto
        SwipeDirection.RIGHT -> strings.actionNextPhoto
        else -> strings.actionNone
    }
    SwipeCardAction.DELETE -> strings.actionDelete
    SwipeCardAction.KEEP -> strings.actionKeep
    SwipeCardAction.MOVE_TO_FOLDER -> strings.actionMoveToFolder
    SwipeCardAction.POSTPONE -> strings.actionPostpone
}

/** Each action gets its own recognizable color, matching the swipe-card chip it corresponds to,
 *  so the effect of a direction is visible at a glance without reading the text. */
@Composable
private fun SwipeCardAction.chipColor(): Color = when (this) {
    SwipeCardAction.DELETE -> SwipeLeftDelete
    SwipeCardAction.KEEP -> SwipeRightKeep
    SwipeCardAction.MOVE_TO_FOLDER -> FolderAction
    SwipeCardAction.POSTPONE -> SwipePostpone
    SwipeCardAction.NONE -> NoneAction
}

@Composable
private fun SwipeCardAction.containerColor(): Color = when (this) {
    SwipeCardAction.DELETE -> DeleteContainer
    SwipeCardAction.KEEP -> KeepContainer
    SwipeCardAction.MOVE_TO_FOLDER -> FolderContainer
    SwipeCardAction.POSTPONE -> PostponeContainer
    SwipeCardAction.NONE -> SurfaceMuted
}

private fun CardAnimationStyle.label(strings: AppStrings): String = when (this) {
    CardAnimationStyle.CLASSIC -> strings.animationClassic
    CardAnimationStyle.FADE -> strings.animationFade
    CardAnimationStyle.SHRINK -> strings.animationShrink
}

private fun SwipeGesturePreset?.description(strings: AppStrings): String? = when (this) {
    SwipeGesturePreset.CLASSIC -> strings.presetClassicDescription
    SwipeGesturePreset.BROWSE_AND_DELETE -> strings.presetBrowseDescription
    null -> null
}

@Composable
private fun PresetDescription(strings: AppStrings, preset: SwipeGesturePreset?) {
    val description = preset.description(strings) ?: return
    Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(bottom = 12.dp),
    )
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

/** Plain number, no unit shown — the value is a dp amount internally, but that's an implementation
 *  detail nobody adjusting a slider needs to see. */
@Composable
private fun EdgePaddingSlider(strings: AppStrings, currentDp: Int, onChanged: (Int) -> Unit) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.edgePadding, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("$currentDp", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Slider(
            value = currentDp.toFloat(),
            onValueChange = { onChanged(it.roundToInt()) },
            valueRange = RoomieSettings.MIN_EDGE_PADDING_DP.toFloat()..RoomieSettings.MAX_EDGE_PADDING_DP.toFloat(),
            steps = RoomieSettings.MAX_EDGE_PADDING_DP - RoomieSettings.MIN_EDGE_PADDING_DP - 1,
        )
    }
}

/** Temporary — see `strings.tempCardTuningSection`'s own TODO. A stepper rather than a slider:
 *  both ranges are small (8-40, 0-4) and a slider's fine-drag precision isn't worth the extra
 *  gesture surface while these are still being tuned by feel. */
@Composable
private fun StepperRow(label: String, value: String, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement) {
            Icon(Icons.Filled.Remove, contentDescription = null)
        }
        Text(
            value,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        IconButton(onClick = onIncrement) {
            Icon(Icons.Filled.Add, contentDescription = null)
        }
    }
}

/** A tappable label-only row that navigates elsewhere (e.g. to the Logs screen) — like
 *  [LabelValueRow] but without a value, just a chevron. */
@Composable
private fun NavigationRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        ChevronIcon()
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
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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

/** A label + value row (e.g. "Swipe right" -> "Delete") that must survive arbitrarily long
 *  translations without wrapping character-by-character or overlapping the next row: giving both
 *  texts an equal weight bounds each to its own half of the row's width, so `maxLines = 1` +
 *  `TextOverflow.Ellipsis` actually has room to take effect. */
@Composable
private fun LabelValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.secondary,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Small circular badge for a swipe direction's arrow, giving each [SwipeActionRow] a glanceable
 *  icon instead of starting straight into text. */
@Composable
private fun DirectionIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Signals "tap to choose" on every row that opens a [DropdownMenu] or [AlertDialog] — rows that
 *  don't (segmented buttons, [SwitchRow]) don't get one. */
@Composable
private fun ChevronIcon() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun SwipeActionRow(
    label: String,
    directionIcon: ImageVector,
    strings: AppStrings,
    current: SwipeCardAction,
    onSelected: (SwipeCardAction) -> Unit,
    direction: SwipeDirection? = null,
    enabled: Boolean = true,
    excludedActions: Set<SwipeCardAction> = emptySet(),
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (enabled) it.clickable { expanded = true } else it }
                .padding(vertical = 10.dp)
                .alpha(if (enabled) 1f else 0.4f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DirectionIcon(directionIcon)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            ActionChip(
                text = current.label(strings, direction),
                chipColor = current.chipColor(),
                containerColor = current.containerColor(),
            )
            if (enabled) {
                Spacer(modifier = Modifier.width(4.dp))
                ChevronIcon()
            }
        }
        // Locked (enabled = false) rows keep their current value visible via the chip above, but
        // can't open this menu at all — the value is only ever changed by picking a preset.
        if (enabled) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SwipeCardAction.entries.filterNot { it in excludedActions }.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label(strings, direction)) },
                        onClick = {
                            onSelected(action)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** A colored, filled pill for a swipe action's current value — replaces plain colored text so the
 *  action reads as a tappable chip rather than a color-coded label. */
@Composable
private fun ActionChip(text: String, chipColor: Color, containerColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = chipColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            .clickable { showDialog = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelValueRow(
            label = strings.moveToFolderDestination,
            value = currentName ?: strings.notSet,
            modifier = Modifier.weight(1f),
        )
        ChevronIcon()
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
