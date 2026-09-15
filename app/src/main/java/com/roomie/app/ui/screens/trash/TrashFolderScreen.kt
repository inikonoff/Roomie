package com.roomie.app.ui.screens.trash

import android.net.Uri
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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.roomie.app.data.db.TrashEntry
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash (${entries.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Trash is empty.", style = MaterialTheme.typography.bodyLarge)
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
                    TrashEntryTile(entry = entry, onRestore = { viewModel.restore(entry) })
                }
            }
        }
    }
}

@Composable
private fun TrashEntryTile(entry: TrashEntry, onRestore: () -> Unit) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .aspectRatio(1f)
                .clip(ContainerShape)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            AsyncImage(
                model = Uri.parse(entry.uri),
                contentDescription = entry.displayName,
                contentScale = ContentScale.Crop,
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
                Icon(Icons.Filled.Restore, contentDescription = "Restore", tint = Color.White)
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
                    formatTimeLeft(entry.permanentDeleteAtMillis),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun formatTimeLeft(permanentDeleteAtMillis: Long): String {
    val remainingMillis = (permanentDeleteAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val days = TimeUnit.MILLISECONDS.toDays(remainingMillis)
    if (days >= 1) return "${days}d left"
    val hours = TimeUnit.MILLISECONDS.toHours(remainingMillis)
    if (hours >= 1) return "${hours}h left"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMillis)
    return "${minutes}m left"
}
