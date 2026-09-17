package com.roomie.app.data.media

import android.net.Uri

/**
 * One physical file in MediaStore (a photo or a video).
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val bucketId: Long,
    val bucketName: String,
    val dateTakenMillis: Long,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val durationMillis: Long = 0L,
    /** Absolute path, when readable (needs All Files Access on API 29+); used only for the
     *  optional empty-folder cleanup, never for reading/writing the file itself. */
    val filePath: String? = null,
    /** Pixel dimensions as MediaStore reports them (already EXIF-orientation-corrected), used to
     *  show each card at its own native aspect ratio instead of cropping to a fixed shape. */
    val width: Int = 0,
    val height: Int = 0,
    /** MediaStore's own `DATE_MODIFIED` (seconds since epoch), read once here so the thumbnail
     *  cache key can fold it in without MediaThumbnail ever querying MediaStore itself. */
    val dateModified: Long = 0L,
) {
    /** Stable identity across image/video tables, since raw `_ID` can collide between them. */
    val stableId: String get() = if (isVideo) "v$id" else "i$id"

    /** Falls back to a typical portrait ratio when MediaStore didn't report real dimensions. */
    val aspectRatio: Float get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 3f / 4f
}

/**
 * A single swipeable/deletable unit wrapping one photo or video.
 */
data class MediaGroup(
    val key: String,
    val items: List<MediaItem>,
) {
    init {
        require(items.isNotEmpty()) { "MediaGroup must contain at least one item" }
    }

    val cover: MediaItem get() = items.first()
    val totalSizeBytes: Long get() = items.sumOf { it.sizeBytes }
    val allUris: List<Uri> get() = items.map { it.uri }
}

/** A folder ("bucket" in MediaStore terms) discovered on-device. */
data class GalleryFolder(
    val bucketId: Long,
    val displayName: String,
    val itemCount: Int,
    val coverUri: Uri?,
    val coverIsVideo: Boolean = false,
    /** Only used to find the single most-recent item across every folder, for the "All Photos"
     *  tile's own cover — not otherwise surfaced in the UI. */
    val coverDateTakenMillis: Long = 0L,
    /** The cover item's own DATE_MODIFIED, passed through to [com.roomie.app.ui.components.MediaThumbnail]
     *  for its disk-cache key — see [MediaItem.dateModified]. */
    val coverDateModified: Long = 0L,
)

enum class PeriodFilter {
    ALL,
    LAST_DAY,
    LAST_MONTH,
    LAST_YEAR,
}

enum class SortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
}
