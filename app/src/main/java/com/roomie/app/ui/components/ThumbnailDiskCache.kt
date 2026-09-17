package com.roomie.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.roomie.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val LOG_TAG = "ThumbCache"

/**
 * Disk cache of already-decoded, already-downsized thumbnail bitmaps, keyed by a hash of
 * (uri + target size). Equivalent to Glide's `DiskCacheStrategy.RESOURCE` — what's stored is the
 * final small bitmap, not the source bytes, so a cache hit skips decoding/downsampling the
 * original file entirely. Lives under `cacheDir`, so the OS can reclaim it under storage
 * pressure like any other cache data; no automatic eviction here — [clear] is manual, from
 * Settings, and is never called from app/process startup.
 */
object ThumbnailDiskCache {
    private const val DIR_NAME = "thumb_cache"

    /** Outlives any single composable's own coroutine scope on purpose. [writeAsync] used to be
     *  launched on a grid tile's `rememberCoroutineScope()` — but a `LazyVerticalGrid` disposes a
     *  tile's composition (and cancels its scope) the moment it scrolls off-screen, which happens
     *  well before a decode-then-compress-then-write finishes during normal fast scrolling. That
     *  silently dropped most writes, which is why the disk cache looked empty again on the next
     *  cold start despite `write` itself working fine when it did get to run: the *decode* (the
     *  expensive part, already reflected on screen via [onSuccess]) had completed, only the trivial
     *  file write after it kept getting cancelled. */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The source's own last-modified time (seconds since epoch, per `MediaStore.MediaColumns
     * .DATE_MODIFIED`), folded into a cache key so a file edited outside Roomie (or replaced at the
     * same MediaStore id) invalidates automatically instead of serving a stale thumbnail forever —
     * the gap e995ade's disk cache deliberately left open. Queried once per uri (callers `remember`
     * the result, same as the existing file-exists check), not on every recomposition/frame; a
     * failed query (permission revoked mid-session, row already gone) falls back to a fixed value
     * rather than crashing — worst case that item's cache just never invalidates on edit, same as
     * before this existed.
     */
    fun dateModifiedKeyPart(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATE_MODIFIED),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            }
        }.getOrNull() ?: 0L

    fun fileFor(context: Context, key: String): File {
        val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        val hash = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.jpg")
    }

    /** Fire-and-forget: runs on [writeScope], not the caller's own (possibly short-lived)
     *  coroutine scope — see the class doc for why that distinction matters. */
    fun writeAsync(file: File, bitmap: Bitmap) {
        writeScope.launch { write(file, bitmap) }
    }

    suspend fun write(file: File, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "write file=${file.name} size=${file.length()}b")
        }
    }

    /** Temporary — see today's TZ. Logs a hit (cache file already on disk, no decode of the
     *  original needed) or a miss (falling through to a real decode) for the first tiles shown
     *  after a cold start, to verify hits actually happen instead of guessing from user reports. */
    fun logHit(key: String, file: File) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "hit=true key=$key file=${file.name} size=${file.length()}b")
    }

    fun logMiss(key: String) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, "hit=false key=$key (decoding source)")
    }

    fun sizeBytes(context: Context): Long {
        val dir = File(context.cacheDir, DIR_NAME)
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clear(context: Context) {
        File(context.cacheDir, DIR_NAME).deleteRecursively()
    }
}
