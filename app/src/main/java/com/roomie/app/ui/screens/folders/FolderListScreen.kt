package com.roomie.app.ui.screens.folders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.roomie.app.data.media.GalleryFolder
import com.roomie.app.ui.components.MediaThumbnail
import com.roomie.app.ui.strings.AppStrings
import com.roomie.app.ui.strings.LocalAppStrings
import com.roomie.app.ui.theme.ContainerShape

@Composable
fun FolderListScreen(
    viewModel: FolderListViewModel,
    onOpenFolder: (bucketId: Long?, displayName: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val trashCount by viewModel.trashCount.collectAsState()
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.onPermissionResult(results.values.all { it })
    }

    LaunchedEffect(Unit) {
        val alreadyGranted = requiredPermissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Scaffold(
        topBar = {
            RoomieTopBar(strings = strings, onOpenSettings = onOpenSettings)
        },
    ) { padding ->
        when {
            !uiState.hasMediaPermission -> PermissionRationale(
                strings = strings,
                modifier = Modifier.padding(padding),
                onGrantClick = { permissionLauncher.launch(requiredPermissions) },
            )

            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> FolderGrid(
                strings = strings,
                modifier = Modifier.padding(padding),
                folders = uiState.folders,
                trashCount = trashCount,
                onOpenFolder = onOpenFolder,
                onOpenTrash = onOpenTrash,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomieTopBar(strings: AppStrings, onOpenSettings: () -> Unit) {
    TopAppBar(
        title = { Text(strings.appName) },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = strings.settingsContentDescription)
            }
        },
    )
}

@Composable
private fun PermissionRationale(strings: AppStrings, modifier: Modifier = Modifier, onGrantClick: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Photo, contentDescription = null, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            strings.permissionRationale,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.padding(top = 16.dp))
        Button(onClick = onGrantClick) {
            Text(strings.grantAccess)
        }
    }
}

@Composable
private fun FolderGrid(
    strings: AppStrings,
    modifier: Modifier = Modifier,
    folders: List<GalleryFolder>,
    trashCount: Int,
    onOpenFolder: (Long?, String) -> Unit,
    onOpenTrash: () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // The most recently taken item across every folder — the global max is always one
                // of the per-folder maxes already computed in getFolders(), so no extra query.
                val newestOverall = folders.maxByOrNull { it.coverDateTakenMillis }
                AllPhotosCard(
                    strings = strings,
                    totalCount = folders.sumOf { it.itemCount },
                    coverUri = newestOverall?.coverUri,
                    coverIsVideo = newestOverall?.coverIsVideo ?: false,
                    onClick = { onOpenFolder(null, strings.allPhotos) },
                )
            }
            item {
                TrashCard(strings = strings, itemCount = trashCount, onClick = onOpenTrash)
            }
            gridItems(folders, key = { it.bucketId }) { folder ->
                FolderCard(
                    folder = folder,
                    strings = strings,
                    onClick = { onOpenFolder(folder.bucketId, folder.displayName) },
                )
            }
        }
    }
}

@Composable
private fun AllPhotosCard(
    strings: AppStrings,
    totalCount: Int,
    coverUri: Uri?,
    coverIsVideo: Boolean,
    onClick: () -> Unit,
) {
    FolderTile(
        title = strings.allPhotos,
        subtitle = strings.itemsCount(totalCount),
        coverUri = coverUri,
        coverIsVideo = coverIsVideo,
        icon = Icons.Filled.Photo,
        onClick = onClick,
    )
}

@Composable
private fun TrashCard(strings: AppStrings, itemCount: Int, onClick: () -> Unit) {
    FolderTile(
        title = strings.trash,
        subtitle = if (itemCount == 0) strings.empty else strings.itemsCount(itemCount),
        coverUri = null,
        icon = Icons.Filled.Delete,
        onClick = onClick,
    )
}

@Composable
private fun FolderCard(folder: GalleryFolder, strings: AppStrings, onClick: () -> Unit) {
    FolderTile(
        title = folder.displayName,
        subtitle = strings.itemsCount(folder.itemCount),
        coverUri = folder.coverUri,
        coverIsVideo = folder.coverIsVideo,
        icon = null,
        onClick = onClick,
    )
}

@Composable
private fun FolderTile(
    title: String,
    subtitle: String,
    coverUri: Uri?,
    coverIsVideo: Boolean = false,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ContainerShape)
                .background(MaterialTheme.colorScheme.surface, ContainerShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // Explicit target size via MediaThumbnail instead of a bare AsyncImage — without
                // it Coil has to infer a size from this Box's own layout constraints, which isn't
                // reliable under a LazyVerticalGrid cell and commonly falls back to decoding the
                // source's full camera resolution, only to have the GPU downscale it at draw time.
                coverUri != null -> MediaThumbnail(
                    uri = coverUri,
                    isVideo = coverIsVideo,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                )
                icon != null -> Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
