package com.cullect.app.ui.screens.trash

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import com.cullect.app.data.db.TrashEntry
import com.cullect.app.ui.components.MediaThumbnail
import com.cullect.app.ui.components.rememberAllowThumbnailDecode
import com.cullect.app.ui.strings.AppStrings
import com.cullect.app.ui.strings.LocalAppStrings
import com.cullect.app.ui.theme.ContainerShape
import com.cullect.app.util.formatBytes

/**
 * The persistent "Trash" folder shown on the main screen — every file Cullect has soft-trashed
 * (swiped away, but still physically on disk — see [com.cullect.app.data.trash.TrashRepository]'s
 * class doc). Tiles are deliberately minimal, matching the old swipe-session review screen: just
 * the photo and a single X to restore it (or long-press to multi-select and restore several, or
 * "restore all" in the top bar). The only way to actually, permanently delete is the
 * "empty trash" action in the top bar — a single deliberate action instead of a delete button on
 * every tile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashFolderScreen(
    viewModel: TrashFolderViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val deleteProgress by viewModel.deleteProgress.collectAsState()
    val restoreProgress by viewModel.restoreProgress.collectAsState()
    val busy = deleteProgress != null || restoreProgress != null
    val justFreedBytes by viewModel.justFreedBytes.collectAsState()
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Multi-select: a long-press on a tile starts it, taps then toggle tiles. Restoring is the only
    // bulk action — permanent delete stays the single deliberate "empty trash" button.
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    // A restored entry disappears from the list; drop it from the selection too.
    LaunchedEffect(entries) {
        val present = entries.mapTo(HashSet()) { it.stableId }
        if (!present.containsAll(selectedIds)) selectedIds = selectedIds.filterTo(HashSet()) { it in present }
        if (entries.isEmpty()) selectionMode = false
    }
    BackHandler(enabled = selectionMode && !busy) {
        selectionMode = false
        selectedIds = emptySet()
    }

    LaunchedEffect(viewModel) {
        viewModel.restoredEvents.collectLatest { count ->
            snackbarHostState.showSnackbar(strings.restoredFiles(count))
        }
    }

    // Only fires once a real, physical delete has actually completed (see the ViewModel doc) —
    // never on a cancelled confirmation dialog, and never for the soft-trash that happens on swipe.
    LaunchedEffect(justFreedBytes) {
        val bytes = justFreedBytes ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(strings.bytesFreed(formatBytes(bytes)))
        viewModel.clearJustFreedBytes()
    }

    // A permanent delete runs in the ViewModel's own coroutine scope — leaving this screen mid-
    // delete would tear that down and abandon the loop with only some files actually removed, so
    // block every way out (system back, top-bar back) until it finishes.
    BackHandler(enabled = deleteProgress != null || restoreProgress != null) {}

    // Same "wait for the real system result, not just launch() returning" pattern used for
    // trash/move requests elsewhere — launch() only starts the confirmation activity, it doesn't
    // mean the user has actually agreed to anything yet.
    var pendingDelete by remember { mutableStateOf<TrashDeleteRequest?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val request = pendingDelete
        pendingDelete = null
        if (request != null && result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeleteConfirmed(request.entries)
        } else {
            // Cancelled/dismissed — must still clear the ViewModel's in-flight guard, or a
            // declined dialog would block every future delete attempt forever.
            viewModel.onDeleteFlowCancelled()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.deleteConfirmationEvents.collect { request ->
            // A second confirmation landing while one dialog is already up would otherwise call
            // launch() again before the first has returned a result, which crashes the launcher.
            if (pendingDelete != null) return@collect
            pendingDelete = request
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }

    // The cleanup worker can't get consent for a real delete from the background (see
    // TrashRepository.permanentlyDeleteExpired), so anything already past its retention window
    // just sits here until this screen is opened. Previously that meant firing the system delete
    // confirmation the instant the screen composed, with no action from the user at all — jarring,
    // and easy to mistake for a bug the first time it happens. Now it's just a count, and the same
    // "empty trash" button (which already covers every entry, expired or not) is what asks.
    val expiredCount = entries.count { it.permanentDeleteAtMillis <= System.currentTimeMillis() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (selectionMode) strings.selectedCount(selectedIds.size) else strings.trashTitle(entries.size))
                        if (!selectionMode && expiredCount > 0) {
                            Text(
                                strings.expiredTrashCount(expiredCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = strings.cancelSelection)
                        }
                    } else {
                        IconButton(onClick = onBack, enabled = !busy) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                        }
                    }
                },
                actions = {
                    if (selectionMode) {
                        IconButton(
                            onClick = { selectedIds = entries.mapTo(HashSet()) { it.stableId } },
                            enabled = !busy,
                        ) {
                            Icon(Icons.Filled.SelectAll, contentDescription = strings.selectAll)
                        }
                        IconButton(
                            onClick = {
                                viewModel.restoreMany(selectedIds)
                                selectionMode = false
                                selectedIds = emptySet()
                            },
                            enabled = selectedIds.isNotEmpty() && !busy,
                        ) {
                            Icon(Icons.Filled.RestoreFromTrash, contentDescription = strings.restoreSelected)
                        }
                    } else if (entries.isNotEmpty()) {
                        // No confirmation: restoring is reversible (swipe it away again), unlike
                        // "empty trash" next to it.
                        IconButton(
                            onClick = { viewModel.restoreMany(entries.mapTo(HashSet()) { it.stableId }) },
                            enabled = !busy,
                        ) {
                            Icon(Icons.Filled.RestoreFromTrash, contentDescription = strings.restoreAll)
                        }
                        IconButton(
                            onClick = { viewModel.requestDeleteForever(entries) },
                            enabled = !busy,
                        ) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = strings.emptyTrash)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            deleteProgress?.let { (done, total) ->
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(strings.deletingProgress(done, total), style = MaterialTheme.typography.bodySmall)
                }
            }
            restoreProgress?.let { (done, total) ->
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(strings.restoringProgress(done, total), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(strings.trashIsEmpty, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val gridState = rememberLazyGridState()
                val allowDecode = rememberAllowThumbnailDecode(gridState.isScrollInProgress)
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(entries, key = { it.stableId }) { entry ->
                        TrashEntryTile(
                            strings = strings,
                            entry = entry,
                            allowDecode = allowDecode,
                            selectionMode = selectionMode,
                            selected = entry.stableId in selectedIds,
                            onRestore = { viewModel.restore(entry) },
                            onToggleSelected = {
                                selectedIds = if (entry.stableId in selectedIds) {
                                    selectedIds - entry.stableId
                                } else {
                                    selectedIds + entry.stableId
                                }
                            },
                            onStartSelection = {
                                if (!busy) {
                                    selectionMode = true
                                    selectedIds = setOf(entry.stableId)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashEntryTile(
    strings: AppStrings,
    entry: TrashEntry,
    allowDecode: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onRestore: () -> Unit,
    onToggleSelected: () -> Unit,
    onStartSelection: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .clip(ContainerShape)
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelected() },
                onLongClick = { if (!selectionMode) onStartSelection() },
            ),
    ) {
        MediaThumbnail(
            uri = Uri.parse(entry.uri),
            isVideo = entry.stableId.startsWith("v"),
            contentDescription = entry.displayName,
            modifier = Modifier.fillMaxSize(),
            allowDecode = allowDecode,
        )
        if (selectionMode) {
            if (selected) {
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
            }
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
            )
        } else {
            IconButton(
                onClick = onRestore,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = strings.restore, tint = Color.White)
            }
        }
    }
}
