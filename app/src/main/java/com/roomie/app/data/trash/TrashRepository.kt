package com.roomie.app.data.trash

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import com.roomie.app.data.db.TrashDao
import com.roomie.app.data.db.TrashEntry
import com.roomie.app.data.media.MediaGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Owns the "one system dialog per session" flow: batches every card the user swiped left into a
 * single [MediaStore.createTrashRequest] (API 30+) and records our own retention countdown in
 * Room so [com.roomie.app.work.TrashCleanupWorker] can permanently delete on the user's configured
 * schedule (1/3/7/30 days) instead of whatever the OS would otherwise pick.
 *
 * On API 26-29, `createTrashRequest` does not exist. There the file simply stays put and visible
 * until the retention window elapses, at which point the worker deletes it directly — an accepted
 * MVP simplification for pre-scoped-storage devices (see TZ section 8.2, "Legacy Android").
 */
class TrashRepository(
    private val context: Context,
    private val trashDao: TrashDao,
) {
    private val resolver get() = context.contentResolver

    /** Persistent view of everything currently trashed (survives app restarts, unlike the
     *  in-session [com.roomie.app.ui.screens.swipe.SwipeSessionViewModel.pendingTrash]) — backs the
     *  Trash folder on the main screen. */
    fun observeTrash(): Flow<List<TrashEntry>> = trashDao.observeAll()

    /** Un-trashes [entries]: clears MediaStore's own IS_TRASHED flag (Q+, no consent needed to
     *  un-hide something the user still owns) and drops our retention bookkeeping. Best-effort —
     *  an entry whose underlying file already vanished just stays removed from Room. */
    suspend fun restoreFromTrash(entries: List<TrashEntry>) = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }
            for (entry in entries) {
                try {
                    resolver.update(Uri.parse(entry.uri), values, null, null)
                } catch (_: RecoverableSecurityException) {
                    // Needs a fresh user consent; the Room row is still cleared below so Roomie's
                    // own countdown won't delete it regardless.
                } catch (_: SecurityException) {
                }
            }
        }
        trashDao.deleteByIds(entries.map { it.stableId })
    }

    /**
     * Returns the [IntentSender] for the single system confirmation dialog on API 30+, or null on
     * older versions (nothing to confirm) or when [groups] is empty.
     */
    fun buildSystemTrashRequest(groups: List<MediaGroup>): IntentSender? {
        if (groups.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = groups.flatMap { it.allUris }
        val pendingIntent = MediaStore.createTrashRequest(resolver, uris, /* trashed = */ true)
        return pendingIntent.intentSender
    }

    /** Call once the system dialog (if any) has been confirmed, to start our retention countdown. */
    suspend fun recordTrashed(groups: List<MediaGroup>, retentionDays: Int) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val deleteAt = now + TimeUnit.DAYS.toMillis(retentionDays.toLong())
        val entries = groups.flatMap { group ->
            group.items.map { item ->
                TrashEntry(
                    stableId = item.stableId,
                    uri = item.uri.toString(),
                    displayName = item.displayName,
                    bucketId = item.bucketId,
                    sizeBytes = item.sizeBytes,
                    trashedAtMillis = now,
                    permanentDeleteAtMillis = deleteAt,
                    filePath = item.filePath,
                )
            }
        }
        trashDao.insertAll(entries)
    }

    /** Runs on [com.roomie.app.work.TrashCleanupWorker]'s schedule. */
    suspend fun permanentlyDeleteExpired(): CleanupResult = withContext(Dispatchers.IO) {
        deleteEntries(trashDao.getExpired(System.currentTimeMillis()))
    }

    /**
     * Manual "delete forever" from the Trash folder screen — lets the user free up space
     * immediately instead of only ever waiting out the retention countdown. On API 30+, get a
     * confirmation [IntentSender] via [buildDeleteRequest] first; deleting straight away here is
     * only correct once that system dialog (if any) has already been confirmed.
     */
    suspend fun permanentlyDelete(entries: List<TrashEntry>): CleanupResult = withContext(Dispatchers.IO) {
        deleteEntries(entries)
    }

    /**
     * Returns the [IntentSender] for the single system confirmation dialog on API 30+
     * ([MediaStore.createDeleteRequest]), or null on older versions where deleting just goes
     * straight through [android.content.ContentResolver.delete] (and may throw
     * [RecoverableSecurityException] per file, which [deleteEntries] treats as "skip, needs fresh
     * consent" rather than trying to resolve it interactively).
     */
    fun buildDeleteRequest(entries: List<TrashEntry>): IntentSender? {
        if (entries.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            val uris = entries.map { Uri.parse(it.uri) }
            MediaStore.createDeleteRequest(resolver, uris).intentSender
        } catch (e: Exception) {
            // Falls through to the direct-delete path in permanentlyDelete()/deleteEntries(),
            // which itself degrades gracefully (skips anything needing consent it can't get)
            // instead of crashing — better than taking the whole app down on a request the OS
            // won't build for some reason.
            null
        }
    }

    private suspend fun deleteEntries(entries: List<TrashEntry>): CleanupResult {
        var freedBytes = 0L
        val deletedIds = mutableListOf<String>()
        val affectedDirs = mutableSetOf<File>()

        for (entry in entries) {
            val deleted = try {
                resolver.delete(Uri.parse(entry.uri), null, null) > 0
            } catch (_: RecoverableSecurityException) {
                // Needs a fresh user consent we can't show here; caller can retry after that.
                false
            } catch (_: SecurityException) {
                false
            }
            if (deleted) {
                freedBytes += entry.sizeBytes
                deletedIds += entry.stableId
                entry.filePath?.let { path -> File(path).parentFile?.let { affectedDirs += it } }
            }
        }

        if (deletedIds.isNotEmpty()) {
            trashDao.deleteByIds(deletedIds)
        }
        return CleanupResult(freedBytes, affectedDirs)
    }
}

data class CleanupResult(val freedBytes: Long, val affectedDirs: Set<File>)
