package com.roomie.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Disk cache of already-decoded, already-downsized thumbnail bitmaps, keyed by a hash of
 * (uri + target size). Equivalent to Glide's `DiskCacheStrategy.RESOURCE` — what's stored is the
 * final small bitmap, not the source bytes, so a cache hit skips decoding/downsampling the
 * original file entirely. Lives under `cacheDir`, so the OS can reclaim it under storage
 * pressure like any other cache data; no automatic eviction here — [clear] is manual, from
 * Settings.
 */
object ThumbnailDiskCache {
    private const val DIR_NAME = "thumb_cache"

    fun fileFor(context: Context, key: String): File {
        val dir = File(context.cacheDir, DIR_NAME).apply { mkdirs() }
        val hash = MessageDigest.getInstance("MD5").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.jpg")
    }

    suspend fun write(file: File, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        }
    }

    fun sizeBytes(context: Context): Long {
        val dir = File(context.cacheDir, DIR_NAME)
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clear(context: Context) {
        File(context.cacheDir, DIR_NAME).deleteRecursively()
    }
}
