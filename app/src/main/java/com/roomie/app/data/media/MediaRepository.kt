package com.roomie.app.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Read-only access to the device gallery via [MediaStore]. Combines the images and video
 * collections into one chronological, groupable stream; never touches the network.
 */
class MediaRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    suspend fun getFolders(): List<GalleryFolder> = withContext(Dispatchers.IO) {
        val counts = LinkedHashMap<Long, MutableList<MediaItem>>()
        queryImages(bucketId = null, period = PeriodFilter.ALL).forEach {
            counts.getOrPut(it.bucketId) { mutableListOf() }.add(it)
        }
        queryVideos(bucketId = null, period = PeriodFilter.ALL).forEach {
            counts.getOrPut(it.bucketId) { mutableListOf() }.add(it)
        }
        counts.map { (bucketId, items) ->
            val newestFirst = items.maxByOrNull { it.dateTakenMillis }
            GalleryFolder(
                bucketId = bucketId,
                displayName = items.first().bucketName,
                itemCount = items.size,
                coverUri = newestFirst?.uri,
                coverIsVideo = newestFirst?.isVideo ?: false,
                coverDateTakenMillis = newestFirst?.dateTakenMillis ?: 0L,
                coverDateModified = newestFirst?.dateModified ?: 0L,
            )
        }.sortedByDescending { it.itemCount }
    }

    /**
     * Returns swipeable units for [bucketId] (null = "All photos"), newest-or-oldest first per
     * [sortOrder] — one [MediaGroup] per photo or video.
     */
    suspend fun getMediaGroups(
        bucketId: Long?,
        period: PeriodFilter,
        sortOrder: SortOrder,
    ): List<MediaGroup> = withContext(Dispatchers.IO) {
        val items = (queryImages(bucketId, period) + queryVideos(bucketId, period))
            .sortedBy { it.dateTakenMillis }
        val groups = items.groupIntoUnits()
        if (sortOrder == SortOrder.NEWEST_FIRST) groups.asReversed() else groups
    }

    /**
     * The `RELATIVE_PATH` of a representative item already in [bucketId], used as the "move to
     * folder" destination — MediaStore buckets don't otherwise expose a writable path. Requires
     * API 29+ (the column doesn't exist below Q); returns null below that or if the bucket is empty.
     */
    suspend fun getRelativePathForBucket(bucketId: Long): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        queryRelativePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.BUCKET_ID, bucketId)
            ?: queryRelativePath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.BUCKET_ID, bucketId)
    }

    private fun queryRelativePath(collection: Uri, bucketColumn: String, bucketId: Long): String? {
        val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
        resolver.query(collection, projection, "$bucketColumn = ?", arrayOf(bucketId.toString()), null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getStringOrEmpty(MediaStore.MediaColumns.RELATIVE_PATH).ifBlank { null }
            }
        }
        return null
    }

    /**
     * The [IntentSender] for the single system consent dialog needed to modify media Roomie
     * doesn't own (API 30+), mirroring [com.roomie.app.data.trash.TrashRepository]'s trash-request
     * flow. Returns null on older versions (nothing to confirm — the update can be attempted
     * directly) or if [uris] is empty.
     */
    fun buildMoveRequest(uris: List<Uri>): IntentSender? {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return try {
            MediaStore.createWriteRequest(resolver, uris).intentSender
        } catch (_: Exception) {
            null
        }
    }

    /** Moves [uris] into [targetRelativePath] by updating their MediaStore row; best-effort. */
    suspend fun applyMove(uris: List<Uri>, targetRelativePath: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, targetRelativePath) }
        uris.forEach { uri ->
            try {
                resolver.update(uri, values, null, null)
            } catch (_: Exception) {
                // Best-effort: a denied write request or a race with the file being deleted elsewhere
                // just leaves that one item where it was.
            }
        }
    }

    private fun queryImages(bucketId: Long?, period: PeriodFilter): List<MediaItem> {
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.BUCKET_ID)
            add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.ORIENTATION)
            @Suppress("DEPRECATION")
            add(MediaStore.Images.Media.DATA)
        }.toTypedArray()

        val (selection, args) = buildSelection(bucketId, period, MediaStore.Images.Media.BUCKET_ID)

        return query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, args) { cursor ->
            val id = cursor.getLong(MediaStore.Images.Media._ID)
            val (width, height) = cursor.orientedSize(MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.ORIENTATION)
            MediaItem(
                id = id,
                uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                displayName = cursor.getStringOrEmpty(MediaStore.Images.Media.DISPLAY_NAME),
                bucketId = cursor.getLong(MediaStore.Images.Media.BUCKET_ID),
                bucketName = cursor.getStringOrEmpty(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                dateTakenMillis = cursor.dateTakenOrAdded(
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED,
                ),
                sizeBytes = cursor.getLong(MediaStore.Images.Media.SIZE),
                isVideo = false,
                filePath = cursor.getStringOrEmpty(MediaStore.Images.Media.DATA).ifBlank { null },
                width = width,
                height = height,
                dateModified = cursor.getLong(MediaStore.Images.Media.DATE_MODIFIED),
            )
        }
    }

    private fun queryVideos(bucketId: Long?, period: PeriodFilter): List<MediaItem> {
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.BUCKET_ID)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Video.Media.DATE_TAKEN)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            @Suppress("DEPRECATION")
            add(MediaStore.Video.Media.DATA)
        }.toTypedArray()

        val (selection, args) = buildSelection(bucketId, period, MediaStore.Video.Media.BUCKET_ID)

        return query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, selection, args) { cursor ->
            val id = cursor.getLong(MediaStore.Video.Media._ID)
            MediaItem(
                id = id,
                uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                displayName = cursor.getStringOrEmpty(MediaStore.Video.Media.DISPLAY_NAME),
                bucketId = cursor.getLong(MediaStore.Video.Media.BUCKET_ID),
                bucketName = cursor.getStringOrEmpty(MediaStore.Video.Media.BUCKET_DISPLAY_NAME),
                dateTakenMillis = cursor.dateTakenOrAdded(
                    MediaStore.Video.Media.DATE_TAKEN,
                    MediaStore.Video.Media.DATE_ADDED,
                ),
                sizeBytes = cursor.getLong(MediaStore.Video.Media.SIZE),
                isVideo = true,
                durationMillis = cursor.getLong(MediaStore.Video.Media.DURATION),
                filePath = cursor.getStringOrEmpty(MediaStore.Video.Media.DATA).ifBlank { null },
                width = cursor.getInt(MediaStore.Video.Media.WIDTH),
                height = cursor.getInt(MediaStore.Video.Media.HEIGHT),
                dateModified = cursor.getLong(MediaStore.Video.Media.DATE_MODIFIED),
            )
        }
    }

    private fun buildSelection(
        bucketId: Long?,
        period: PeriodFilter,
        bucketColumn: String,
    ): Pair<String?, Array<String>?> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (bucketId != null) {
            clauses += "$bucketColumn = ?"
            args += bucketId.toString()
        }

        periodStartMillis(period)?.let { startMillis ->
            clauses += "${MediaStore.MediaColumns.DATE_ADDED} >= ?"
            args += (startMillis / 1000).toString()
        }

        if (clauses.isEmpty()) return null to null
        return clauses.joinToString(" AND ") to args.toTypedArray()
    }

    private fun periodStartMillis(period: PeriodFilter): Long? {
        if (period == PeriodFilter.ALL) return null
        val calendar = Calendar.getInstance()
        when (period) {
            PeriodFilter.LAST_DAY -> calendar.add(Calendar.DAY_OF_YEAR, -1)
            PeriodFilter.LAST_MONTH -> calendar.add(Calendar.MONTH, -1)
            PeriodFilter.LAST_YEAR -> calendar.add(Calendar.YEAR, -1)
            PeriodFilter.ALL -> Unit
        }
        return calendar.timeInMillis
    }

    private inline fun query(
        collection: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        crossinline mapRow: (Cursor) -> MediaItem,
    ): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                results += mapRow(cursor)
            }
        }
        return results
    }

    private fun Cursor.getStringOrEmpty(column: String): String =
        getColumnIndex(column).takeIf { it >= 0 }?.let { getString(it) } ?: ""

    private fun Cursor.getLong(column: String): Long =
        getColumnIndex(column).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L

    private fun Cursor.getInt(column: String): Int =
        getColumnIndex(column).takeIf { it >= 0 }?.let { getInt(it) } ?: 0

    /**
     * MediaStore's WIDTH/HEIGHT columns often report the sensor's raw (un-rotated) pixel
     * dimensions rather than the displayed ones — a photo shot in portrait can come back with
     * width > height. Coil renders EXIF-rotated, so without this swap the card would be sized for
     * the wrong orientation and show empty background bars down the sides. ORIENTATION is the EXIF
     * rotation in degrees Roomie itself never applies, just uses to fix the pair up here.
     */
    private fun Cursor.orientedSize(widthColumn: String, heightColumn: String, orientationColumn: String): Pair<Int, Int> {
        val width = getInt(widthColumn)
        val height = getInt(heightColumn)
        val orientation = getInt(orientationColumn)
        return if (orientation == 90 || orientation == 270) height to width else width to height
    }

    /** [dateTakenColumn] is only populated by camera apps; fall back to DATE_ADDED (seconds) otherwise. */
    private fun Cursor.dateTakenOrAdded(dateTakenColumn: String, dateAddedColumn: String): Long {
        val taken = getLong(dateTakenColumn)
        if (taken > 0L) return taken
        return getLong(dateAddedColumn) * 1000
    }
}
