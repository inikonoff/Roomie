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
import com.roomie.app.data.media.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * A swipe-delete is a *soft* trash: [recordTrashed] only ever writes to Room, immediately, the
 * moment the user swipes — it never touches MediaStore or shows a system dialog, so browsing and
 * deleting stays completely uninterrupted no matter how many photos get swiped away. The file
 * itself is untouched on disk until the user visits the Trash folder and empties it, at which
 * point [permanentlyDelete] (or the [com.roomie.app.work.TrashCleanupWorker] schedule, via
 * [permanentlyDeleteExpired]) does the real, irreversible deletion — the one place a system
 * confirmation dialog (API 30+, [buildDeleteRequest]) is expected and appropriate.
 */
class TrashRepository(
    private val context: Context,
    private val trashDao: TrashDao,
) {
    private val resolver get() = context.contentResolver

    /** Persistent view of everything currently trashed (survives app restarts) — backs the Trash
     *  folder on the main screen. */
    fun observeTrash(): Flow<List<TrashEntry>> = trashDao.observeAll()

    /** Every stable id currently sitting in the trash, so a folder/grid query can exclude them —
     *  a swiped-away photo is still physically on disk (see class doc) and would otherwise keep
     *  showing up in normal browsing. */
    suspend fun getTrashedStableIds(): Set<String> = withContext(Dispatchers.IO) {
        trashDao.getAllStableIds().toSet()
    }

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

    /** Called immediately when the user swipes a card away — see the class doc for why this never
     *  touches MediaStore. */
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

    /** Undo, right after a swipe: since [recordTrashed] never touched MediaStore, un-trashing is
     *  just dropping the Room rows — the file was never actually modified. */
    suspend fun cancelPendingTrash(items: List<MediaItem>) = withContext(Dispatchers.IO) {
        trashDao.deleteByIds(items.map { it.stableId })
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
        val uris = entries.map { Uri.parse(it.uri) }
        return MediaStore.createDeleteRequest(resolver, uris).intentSender
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
