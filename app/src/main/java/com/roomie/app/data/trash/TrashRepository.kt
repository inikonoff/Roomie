package com.roomie.app.data.trash

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import com.roomie.app.CrashReporter
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

    /**
     * Runs on [com.roomie.app.work.TrashCleanupWorker]'s schedule — a background job with no
     * Activity, so it can never show the consent dialog [buildDeleteRequest] needs on API 30+.
     * Calling `resolver.delete()` there anyway would just throw [RecoverableSecurityException] for
     * every single file, every run, forever, and silently do nothing — expired entries would sit
     * in Room indefinitely. On those versions this deliberately leaves expired entries alone;
     * [com.roomie.app.ui.screens.trash.TrashFolderScreen] picks them up and runs them through the
     * real, consenting delete flow the next time it's opened. Pre-30, deleting someone else's media
     * needs no consent at all, so the worker can (and does) finish the job itself.
     */
    suspend fun permanentlyDeleteExpired(): CleanupResult = withContext(Dispatchers.IO) {
        val expired = trashDao.getExpired(System.currentTimeMillis())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CleanupResult(freedBytes = 0L, affectedDirs = emptySet())
        } else {
            deleteEntries(expired)
        }
    }

    /**
     * Manual "delete forever" from the Trash folder screen — lets the user free up space
     * immediately instead of only ever waiting out the retention countdown. On API 30+, get a
     * confirmation [IntentSender] via [buildDeleteRequest] first; deleting straight away here is
     * only correct once that system dialog (if any) has already been confirmed.
     *
     * [onProgress] reports (done, total) after every single file — the caller uses it to show a
     * live counter, since [deleteEntries] already commits each deletion to Room as it happens
     * rather than batching them at the end.
     */
    suspend fun permanentlyDelete(
        entries: List<TrashEntry>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): CleanupResult = withContext(Dispatchers.IO) {
        deleteEntries(entries, onProgress)
    }

    /**
     * Catches Room up with a permanent delete the system already performed: once the user confirms
     * the [IntentSender] from [buildDeleteRequest] (API 30+), `MediaStore.createDeleteRequest`
     * deletes the underlying media itself — the app is not meant to (and must not) call
     * [android.content.ContentResolver.delete] again for it. Doing so anyway (the previous bug
     * here) just queries a URI that no longer exists, gets 0 rows affected, and treats that as
     * "not deleted" — the Room entry, and so the Trash folder tile, was then left behind forever,
     * only ever cleaned up incidentally the next time [buildDeleteRequest]'s staleness check ran.
     * [onProgress] still reports (done, total) per entry so the caller can show the same live
     * counter as [permanentlyDelete].
     */
    suspend fun confirmSystemDelete(
        entries: List<TrashEntry>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): CleanupResult = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        val affectedDirs = mutableSetOf<File>()
        for ((index, entry) in entries.withIndex()) {
            trashDao.deleteByIds(listOf(entry.stableId))
            freedBytes += entry.sizeBytes
            entry.filePath?.let { path -> File(path).parentFile?.let { affectedDirs += it } }
            CrashReporter.mark(context, "trash_delete:confirmed[$index/${entries.size}]:room_cleaned:${entry.stableId}")
            onProgress(index + 1, entries.size)
        }
        CleanupResult(freedBytes, affectedDirs)
    }

    /**
     * Returns the entries actually still present in MediaStore alongside the [IntentSender] for
     * the single system confirmation dialog on API 30+ ([MediaStore.createDeleteRequest]) — or a
     * null sender on older versions, where deleting just goes straight through
     * [android.content.ContentResolver.delete] (and may throw [RecoverableSecurityException] per
     * file, which [deleteEntries] treats as "skip, needs fresh consent" rather than trying to
     * resolve it interactively).
     *
     * Entries whose URI no longer resolves in MediaStore (deleted or replaced outside Roomie) are
     * dropped from Room here and excluded from the request — [MediaStore.createDeleteRequest] can
     * throw for a URI it doesn't recognize, and there's nothing left in the trash to retry them
     * with anyway. The whole call is also wrapped so an unexpected failure just yields "nothing to
     * request" rather than crashing — the next visit to the trash folder will pick expired entries
     * back up.
     */
    suspend fun buildDeleteRequest(entries: List<TrashEntry>): Pair<List<TrashEntry>, IntentSender?> =
        withContext(Dispatchers.IO) {
            CrashReporter.mark(context, "trash_delete:build_request:count=${entries.size}")
            if (entries.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return@withContext entries to null
            }

            val (valid, stale) = entries.partition { entry -> uriExists(Uri.parse(entry.uri)) }
            CrashReporter.mark(context, "trash_delete:filtered:valid=${valid.size}:stale=${stale.size}")
            if (stale.isNotEmpty()) {
                trashDao.deleteByIds(stale.map { it.stableId })
            }
            if (valid.isEmpty()) return@withContext emptyList<TrashEntry>() to null

            val sender = try {
                val uris = valid.map { Uri.parse(it.uri) }
                val intentSender = MediaStore.createDeleteRequest(resolver, uris).intentSender
                CrashReporter.mark(context, "trash_delete:request_built")
                intentSender
            } catch (e: Exception) {
                CrashReporter.mark(context, "trash_delete:create_request_failed:${e::class.simpleName}")
                null
            }
            valid to sender
        }

    private fun uriExists(uri: Uri): Boolean =
        try {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) {
            false
        }

    private suspend fun deleteEntries(
        entries: List<TrashEntry>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): CleanupResult {
        var freedBytes = 0L
        val affectedDirs = mutableSetOf<File>()

        for ((index, entry) in entries.withIndex()) {
            CrashReporter.mark(context, "trash_delete:entry[$index/${entries.size}]:start:${entry.stableId}")
            val deleted = try {
                resolver.delete(Uri.parse(entry.uri), null, null) > 0
            } catch (_: RecoverableSecurityException) {
                // Needs a fresh user consent we can't show here; caller can retry after that.
                false
            } catch (_: SecurityException) {
                false
            }
            CrashReporter.mark(context, "trash_delete:entry[$index/${entries.size}]:done:deleted=$deleted")
            if (deleted) {
                freedBytes += entry.sizeBytes
                // Committed right away, one file at a time, instead of batched after the whole
                // loop: observeTrash() emits immediately so the UI list shrinks live, and — just
                // as importantly — anything already deleted here survives the ViewModel (and its
                // viewModelScope coroutine) being torn down mid-delete, e.g. by leaving the screen
                // or the process dying, instead of staying gone on disk but still listed in Room.
                trashDao.deleteByIds(listOf(entry.stableId))
                entry.filePath?.let { path -> File(path).parentFile?.let { affectedDirs += it } }
            }
            onProgress(index + 1, entries.size)
        }

        return CleanupResult(freedBytes, affectedDirs)
    }
}

data class CleanupResult(val freedBytes: Long, val affectedDirs: Set<File>)
