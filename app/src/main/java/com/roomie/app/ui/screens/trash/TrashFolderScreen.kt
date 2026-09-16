package com.roomie.app.ui.screens.trash

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.roomie.app.CrashReporter
import com.roomie.app.data.db.TrashEntry
import com.roomie.app.ui.components.MediaThumbnail
import com.roomie.app.ui.strings.AppStrings
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.ui.theme.ContainerShape

/**
 * The persistent "Trash" folder shown on the main screen — every file Roomie has soft-trashed
 * (swiped away, but still physically on disk — see [com.roomie.app.data.trash.TrashRepository]'s
 * class doc). Tiles are deliberately minimal, matching the old swipe-session review screen: just
 * the photo and a single X to restore it. The only way to actually, permanently delete is the
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
    val strings = LocalAppStrings.current
    val context = LocalContext.current

    // A permanent delete runs in the ViewModel's own coroutine scope — leaving this screen mid-
    // delete would tear that down and abandon the loop with only some files actually removed, so
    // block every way out (system back, top-bar back) until it finishes.
    BackHandler(enabled = deleteProgress != null) {}

    // Same "wait for the real system result, not just launch() returning" pattern used for
    // trash/move requests elsewhere — launch() only starts the confirmation activity, it doesn't
    // mean the user has actually agreed to anything yet.
    var pendingDelete by remember { mutableStateOf<TrashDeleteRequest?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        // Temporary diagnostic checkpoint — see TrashRepository for why (a crash CrashReporter's
        // uncaught-exception handler never sees, so this file survives it instead).
        CrashReporter.mark(context, "trash_delete:launcher_result:code=${result.resultCode}")
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
    // just sits here until this screen is opened — pick it up automatically, right away, instead
    // of making the user notice and tap "empty trash" themselves.
    //
    // Tracked separately from the guards above: this effect re-runs on every `entries` emission,
    // including the one caused by its own request being confirmed, so without remembering which
    // stableIds were already sent it would keep re-requesting the same (still-pending) entries.
    var requestedForDeletion by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(entries) {
        val now = System.currentTimeMillis()
        val expired = entries.filter { it.permanentDeleteAtMillis <= now && it.stableId !in requestedForDeletion }
        if (expired.isNotEmpty()) {
            requestedForDeletion = requestedForDeletion + expired.map { it.stableId }
            viewModel.requestDeleteForever(expired)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.trashTitle(entries.size)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = deleteProgress == null) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.requestDeleteForever(entries) },
                            enabled = deleteProgress == null,
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
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(strings.trashIsEmpty, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyVerticalGrid(
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
                            onRestore = { viewModel.restore(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashEntryTile(
    strings: AppStrings,
    entry: TrashEntry,
    onRestore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .clip(ContainerShape)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        MediaThumbnail(
            uri = Uri.parse(entry.uri),
            isVideo = entry.stableId.startsWith("v"),
            contentDescription = entry.displayName,
            modifier = Modifier.fillMaxSize(),
        )
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
