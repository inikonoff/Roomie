package com.cullect.app.ui.screens.foldergrid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cullect.app.data.media.MediaGroup
import com.cullect.app.data.media.PeriodFilter
import com.cullect.app.ui.components.cullectFilterChipColors
import com.cullect.app.ui.components.MediaThumbnail
import com.cullect.app.ui.components.rememberAllowThumbnailDecode
import com.cullect.app.ui.strings.AppStrings
import com.cullect.app.ui.strings.LocalAppStrings
import kotlinx.coroutines.flow.first

/**
 * The full contents of a folder, seen before committing to a swipe session — tapping a photo
 * starts swiping from exactly that one instead of always from the very first item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderGridScreen(
    viewModel: FolderGridViewModel,
    bucketId: Long?,
    displayName: String,
    onOpenSwipe: (startAtStableId: String) -> Unit,
    onBack: () -> Unit,
    /** The photo the user was on when they left the swipe stack; the grid scrolls to it once. */
    scrollToStableId: String? = null,
    onScrolledToTarget: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalAppStrings.current

    LaunchedEffect(bucketId, displayName) {
        viewModel.load(bucketId, displayName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayName) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PeriodFilterRow(
                strings = strings,
                selected = uiState.period,
                onSelected = { period -> viewModel.onPeriodSelected(bucketId, period) },
            )

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.groups.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(strings.nothingHere, style = MaterialTheme.typography.bodyLarge)
                }

                else -> {
                    val gridState = rememberLazyGridState()
                    val allowDecode = rememberAllowThumbnailDecode(gridState.isScrollInProgress)
                    // Lives in this branch so it only runs once there is a grid to scroll. If the
                    // photo's row is already fully on screen the grid stays put; otherwise it's
                    // brought to the middle of the screen, with context above and below.
                    LaunchedEffect(scrollToStableId) {
                        val target = scrollToStableId ?: return@LaunchedEffect
                        val index = uiState.groups.indexOfFirst { it.cover.stableId == target }
                        if (index >= 0) {
                            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
                            val info = gridState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                            val fullyVisible = item != null && item.offset.y >= 0 &&
                                item.offset.y + item.size.height <= info.viewportSize.height
                            if (!fullyVisible) {
                                gridState.scrollToItem(index)
                                val row = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                if (row != null) {
                                    val middle = (gridState.layoutInfo.viewportSize.height - row.size.height) / 2f
                                    gridState.scrollBy(row.offset.y - middle)
                                }
                            }
                        }
                        onScrolledToTarget()
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(uiState.groups, key = { it.key }) { group ->
                            GridThumbnail(
                                strings = strings,
                                group = group,
                                allowDecode = allowDecode,
                                onClick = { onOpenSwipe(group.cover.stableId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterRow(strings: AppStrings, selected: PeriodFilter, onSelected: (PeriodFilter) -> Unit) {
    val options = listOf(
        PeriodFilter.ALL to strings.periodAll,
        PeriodFilter.LAST_DAY to strings.periodDay,
        PeriodFilter.LAST_MONTH to strings.periodMonth,
        PeriodFilter.LAST_YEAR to strings.periodYear,
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rowItems(options) { (filter, label) ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelected(filter) },
                label = { Text(label) },
                colors = cullectFilterChipColors(),
            )
        }
    }
}

@Composable
private fun GridThumbnail(strings: AppStrings, group: MediaGroup, allowDecode: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        MediaThumbnail(
            uri = group.cover.uri,
            isVideo = group.cover.isVideo,
            contentDescription = group.cover.displayName,
            modifier = Modifier.fillMaxSize(),
            dateModified = group.cover.dateModified,
            allowDecode = allowDecode,
        )
        if (group.cover.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = strings.videoContentDescription,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
            )
        }
    }
}
