package com.roomie.app.ui.navigation

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roomie.app.ui.screens.swipe.MoveConfirmationRequest
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.roomie.app.data.media.PeriodFilter
import com.roomie.app.ui.ViewModelFactory
import com.roomie.app.ui.screens.foldergrid.FolderGridScreen
import com.roomie.app.ui.screens.foldergrid.FolderGridViewModel
import com.roomie.app.ui.screens.folders.FolderListScreen
import com.roomie.app.ui.screens.folders.FolderListViewModel
import com.roomie.app.ui.screens.limit.SwipeLimitScreen
import com.roomie.app.ui.screens.logs.LogsScreen
import com.roomie.app.ui.screens.logs.LogsViewModel
import com.roomie.app.ui.screens.settings.SettingsScreen
import com.roomie.app.ui.screens.settings.SettingsViewModel
import com.roomie.app.ui.screens.summary.SummaryScreen
import com.roomie.app.ui.screens.swipe.SwipeScreen
import com.roomie.app.ui.screens.swipe.SwipeSessionViewModel
import com.roomie.app.ui.screens.trash.TrashFolderScreen
import com.roomie.app.ui.screens.trash.TrashFolderViewModel
import java.net.URLDecoder
import java.net.URLEncoder

private object Routes {
    const val FOLDERS = "folders"
    const val FOLDER_GRID = "folder_grid/{bucketId}/{displayName}"
    const val SWIPE = "swipe/{bucketId}/{displayName}/{period}/{startAt}"
    const val TRASH_FOLDER = "trash_folder"
    const val SUMMARY = "summary"
    const val SWIPE_LIMIT = "swipe_limit"
    const val SETTINGS = "settings"
    const val LOGS = "logs"

    const val ALL_PHOTOS_SENTINEL = "all"
    const val NO_START_SENTINEL = "start"

    fun folderGrid(bucketId: Long?, displayName: String): String {
        val encodedName = URLEncoder.encode(displayName, "UTF-8")
        val bucket = bucketId?.toString() ?: ALL_PHOTOS_SENTINEL
        return "folder_grid/$bucket/$encodedName"
    }

    fun swipe(bucketId: Long?, displayName: String, period: PeriodFilter, startAtStableId: String?): String {
        val encodedName = URLEncoder.encode(displayName, "UTF-8")
        val bucket = bucketId?.toString() ?: ALL_PHOTOS_SENTINEL
        val startAt = startAtStableId?.let { URLEncoder.encode(it, "UTF-8") } ?: NO_START_SENTINEL
        return "swipe/$bucket/$encodedName/${period.name}/$startAt"
    }
}

@Composable
fun RoomieNavHost(viewModelFactory: ViewModelFactory) {
    val navController = rememberNavController()

    // Activity-scoped: the swipe/summary/limit screens are one continuous flow and share this
    // single session's in-memory state (stack, undo history).
    val swipeSessionViewModel: SwipeSessionViewModel = viewModel(factory = viewModelFactory)

    // For the write access needed to move a swiped card into the configured target folder.
    // launch() only starts the system confirmation activity — it returns immediately, well before
    // the user has even seen the dialog. The actual write grant is only real once the result
    // callback below fires with RESULT_OK, so the pending request is stashed here and acted on
    // there, never right after launch().
    var pendingMoveRequest by remember { mutableStateOf<MoveConfirmationRequest?>(null) }
    val moveIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val request = pendingMoveRequest
        pendingMoveRequest = null
        if (request != null && result.resultCode == Activity.RESULT_OK) {
            swipeSessionViewModel.onMoveConfirmed(request)
        }
    }

    LaunchedEffect(swipeSessionViewModel) {
        swipeSessionViewModel.moveConfirmationEvents.collect { request ->
            pendingMoveRequest = request
            moveIntentSenderLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        }
    }

    NavHost(navController = navController, startDestination = Routes.FOLDERS) {
        composable(Routes.FOLDERS) {
            val folderListViewModel: FolderListViewModel = viewModel(factory = viewModelFactory)
            FolderListScreen(
                viewModel = folderListViewModel,
                onOpenFolder = { bucketId, displayName ->
                    navController.navigate(Routes.folderGrid(bucketId, displayName))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenTrash = { navController.navigate(Routes.TRASH_FOLDER) },
            )
        }

        composable(
            route = Routes.FOLDER_GRID,
            arguments = listOf(
                navArgument("bucketId") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            val bucketIdArg = args.getString("bucketId")
            val bucketId = bucketIdArg?.takeIf { it != Routes.ALL_PHOTOS_SENTINEL }?.toLongOrNull()
            val displayName = URLDecoder.decode(args.getString("displayName") ?: "", "UTF-8")

            val folderGridViewModel: FolderGridViewModel = viewModel(factory = viewModelFactory)
            FolderGridScreen(
                viewModel = folderGridViewModel,
                bucketId = bucketId,
                displayName = displayName,
                onOpenSwipe = { startAtStableId ->
                    // Read live from the ViewModel's own state, not a captured route argument —
                    // the period can change while browsing this folder's grid, and the swipe
                    // session must start with whatever's actually selected at tap time.
                    val period = folderGridViewModel.uiState.value.period
                    navController.navigate(Routes.swipe(bucketId, displayName, period, startAtStableId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TRASH_FOLDER) {
            val trashFolderViewModel: TrashFolderViewModel = viewModel(factory = viewModelFactory)
            TrashFolderScreen(
                viewModel = trashFolderViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.SWIPE,
            arguments = listOf(
                navArgument("bucketId") { type = NavType.StringType },
                navArgument("displayName") { type = NavType.StringType },
                navArgument("period") { type = NavType.StringType },
                navArgument("startAt") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            val bucketIdArg = args.getString("bucketId")
            val bucketId = bucketIdArg?.takeIf { it != Routes.ALL_PHOTOS_SENTINEL }?.toLongOrNull()
            val displayName = URLDecoder.decode(args.getString("displayName") ?: "", "UTF-8")
            val period = PeriodFilter.valueOf(args.getString("period") ?: PeriodFilter.ALL.name)
            val startAtArg = args.getString("startAt")
            val startAtStableId = startAtArg
                ?.takeIf { it != Routes.NO_START_SENTINEL }
                ?.let { URLDecoder.decode(it, "UTF-8") }

            SwipeSessionEntry(
                viewModel = swipeSessionViewModel,
                bucketId = bucketId,
                displayName = displayName,
                period = period,
                startAtStableId = startAtStableId,
                // Every swipe-delete already committed to the trash the instant it happened (see
                // SwipeSessionViewModel.swipe) — there is nothing left to confirm or commit here.
                onBack = { navController.popBackStack() },
                onStackExhausted = {
                    swipeSessionViewModel.prepareSessionSummary()
                    navController.navigate(Routes.SUMMARY) { popUpTo(Routes.FOLDERS) }
                },
                onLimitReached = { navController.navigate(Routes.SWIPE_LIMIT) },
                onOpenTrash = { navController.navigate(Routes.TRASH_FOLDER) },
            )
        }

        composable(Routes.SUMMARY) {
            val summary by swipeSessionViewModel.summaryState.collectAsState()
            summary?.let {
                SummaryScreen(
                    summary = it,
                    onDone = {
                        swipeSessionViewModel.clearSummary()
                        navController.popBackStack(Routes.FOLDERS, inclusive = false)
                    },
                )
            }
        }

        composable(Routes.SWIPE_LIMIT) {
            SwipeLimitScreen(
                viewModel = swipeSessionViewModel,
                onUnlocked = { navController.popBackStack() },
                onBackToFolders = { navController.popBackStack(Routes.FOLDERS, inclusive = false) },
            )
        }

        composable(Routes.SETTINGS) {
            val settingsViewModel: SettingsViewModel = viewModel(factory = viewModelFactory)
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onOpenLogs = { navController.navigate(Routes.LOGS) },
            )
        }

        composable(Routes.LOGS) {
            val logsViewModel: LogsViewModel = viewModel(factory = viewModelFactory)
            LogsScreen(
                viewModel = logsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun SwipeSessionEntry(
    viewModel: SwipeSessionViewModel,
    bucketId: Long?,
    displayName: String,
    period: PeriodFilter,
    startAtStableId: String?,
    onBack: () -> Unit,
    onStackExhausted: () -> Unit,
    onLimitReached: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    LaunchedEffect(bucketId, displayName, period, startAtStableId) {
        viewModel.loadFolder(bucketId, displayName, period, startAtStableId)
    }
    SwipeScreen(
        viewModel = viewModel,
        onBack = onBack,
        onStackExhausted = onStackExhausted,
        onLimitReached = onLimitReached,
        onOpenTrash = onOpenTrash,
    )
}
