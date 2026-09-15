package com.roomie.app.ui.screens.trash

import android.app.Activity
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.roomie.app.data.db.TrashEntry
import com.roomie.app.ui.components.MediaThumbnail
import com.roomie.app.ui.strings.AppStrings
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.ui.theme.ContainerShape
import java.util.concurrent.TimeUnit

/**
 * The persistent "Trash" folder shown on the main screen — every file Roomie has trashed and is
 * still counting down on, browsable any time (not just mid-swipe-session).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashFolderScreen(
    viewModel: TrashFolderViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()
    val strings = LocalAppStrings.current
    var showEmptyTrashConfirm by remember { mutableStateOf(false) }

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
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.deleteConfirmationEvents.collect { request ->
            pendingDelete = request
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.trashTitle(entries.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showEmptyTrashConfirm = true }) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = strings.emptyTrash)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(strings.trashIsEmpty, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(entries, key = { it.stableId }) { entry ->
                    TrashEntryTile(
                        strings = strings,
                        entry = entry,
                        onRestore = { viewModel.restore(entry) },
                        onDeleteForever = { viewModel.requestDeleteForever(listOf(entry)) },
                    )
                }
            }
        }
    }

    if (showEmptyTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashConfirm = false },
            title = { Text(strings.emptyTrashConfirmTitle) },
            text = { Text(strings.emptyTrashConfirmMessage(entries.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEmptyTrashConfirm = false
                        viewModel.requestDeleteForever(entries)
                    },
                ) { Text(strings.deleteForever) }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashConfirm = false }) { Text(strings.cancel) }
            },
        )
    }
}

@Composable
private fun TrashEntryTile(
    strings: AppStrings,
    entry: TrashEntry,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    Column {
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
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = strings.restore, tint = Color.White)
            }
            IconButton(
                onClick = onDeleteForever,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = strings.deleteForever, tint = Color.White)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    formatTimeLeft(strings, entry.permanentDeleteAtMillis),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun formatTimeLeft(strings: AppStrings, permanentDeleteAtMillis: Long): String {
    val remainingMillis = (permanentDeleteAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val days = TimeUnit.MILLISECONDS.toDays(remainingMillis)
    if (days >= 1) return strings.timeLeftDays(days)
    val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis)
    if (hours >= 1) return strings.timeLeftHours(hours)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)
    return strings.timeLeftMinutes(minutes)
}
