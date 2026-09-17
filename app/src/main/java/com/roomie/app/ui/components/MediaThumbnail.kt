package com.roomie.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val VIDEO_THUMBNAIL_PX = 320

/** Target size for a photo grid thumbnail. Requested explicitly instead of leaving Coil to infer
 *  it from the composable's own layout constraints — under a `LazyVerticalGrid` cell wrapped in
 *  several nested `Box`/`aspectRatio` modifiers, that inference isn't always reliable, and when it
 *  falls back to the source's full resolution, the *view* (not Coil) ends up doing the downscale
 *  on the GPU with simple bilinear filtering — which is what produces visible moiré/blockiness on
 *  a busy photo shrunk that far down. Requesting a real target size makes Coil decode at that
 *  resolution up front instead, which looks correct and uses far less memory per tile. */
private const val GRID_THUMBNAIL_PX = 320

/**
 * Process-wide cache for decoded video thumbnails. Every tile requests the same fixed
 * [VIDEO_THUMBNAIL_PX] size regardless of its actual on-screen size, so a URI is a valid cache
 * key; without this, scrolling a video tile off-screen and back re-ran the (relatively expensive)
 * frame-extraction decode every single time, which is what was making video-heavy folders lag and
 * leave tiles blank — the grid was re-decoding faster than it could keep up with scrolling.
 */
private val videoThumbnailCache = LruCache<String, Bitmap>(300)

/** Bounds how many `loadThumbnail` (MediaMetadataRetriever-backed) calls run at once — a fast
 *  fling through a video-heavy folder can otherwise fire off a dozen of these together, each a
 *  real frame-extraction decode, which is what produced this app's worst multi-second frames.
 *  Coil's own decode work (the photo path) is capped the same way via the shared ImageLoader's
 *  decoderDispatcher — see RoomieApplication. A cell that scrolls away while waiting on this
 *  semaphore has its produceState coroutine cancelled by Compose itself (LazyVerticalGrid disposes
 *  off-screen items), which withPermit honors — no manual job-cancellation bookkeeping needed. */
private val videoDecodeLimiter = Semaphore(3)

/**
 * Grid-safe thumbnail for a gallery item. Photos go through Coil as before, but videos use
 * [android.content.ContentResolver.loadThumbnail] directly (API 29+) instead of Coil's
 * video-frame decoder — under a grid's concurrent load, the decoder was unreliable enough that
 * video tiles routinely rendered blank. Below API 29, video falls back to Coil since
 * `loadThumbnail` doesn't exist yet there.
 */
@Composable
fun MediaThumbnail(
    uri: Uri,
    isVideo: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val context = LocalContext.current
        val key = uri.toString()
        val bitmap by produceState(videoThumbnailCache.get(key), uri) {
            if (value == null) {
                value = withContext(Dispatchers.IO) {
                    // Disk cache first — MediaMetadataRetriever-backed loadThumbnail is a real
                    // frame-extraction decode, objectively pricier than a photo decode, and until
                    // now was only ever cached in memory for the life of the process. dateModified
                    // in the key means a video replaced/edited outside Roomie invalidates on its own
                    // instead of serving a stale frame forever.
                    val dateModified = ThumbnailDiskCache.dateModifiedKeyPart(context, uri)
                    val cacheKey = "video|$key|$VIDEO_THUMBNAIL_PX|$dateModified"
                    val cacheFile = ThumbnailDiskCache.fileFor(context, cacheKey)
                    val fromDisk = if (cacheFile.exists()) BitmapFactory.decodeFile(cacheFile.path) else null
                    if (fromDisk != null) {
                        ThumbnailDiskCache.logHit(cacheKey, cacheFile)
                        fromDisk
                    } else {
                        ThumbnailDiskCache.logMiss(cacheKey)
                        videoDecodeLimiter.withPermit {
                            runCatching {
                                context.contentResolver.loadThumbnail(
                                    uri,
                                    Size(VIDEO_THUMBNAIL_PX, VIDEO_THUMBNAIL_PX),
                                    null,
                                )
                            }.getOrNull()
                        }?.also { bmp -> ThumbnailDiskCache.writeAsync(cacheFile, bmp) }
                    }
                }?.also { videoThumbnailCache.put(key, it) }
            }
        }
        Box(modifier = modifier) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    } else {
        val context = LocalContext.current
        // Disk-cache key is uri + target size + the source's own DATE_MODIFIED, matching the video
        // path above — a cache hit here skips decoding/downsampling the original file entirely
        // (Glide's DiskCacheStrategy.RESOURCE equivalent), not just re-reading already-decoded bytes
        // off MediaStore, and a photo edited/replaced outside Roomie invalidates on its own instead
        // of serving a stale thumbnail forever. remember keyed on uri so a recomposition doesn't
        // re-stat the file (or re-query MediaStore for the date) on every frame. GRID_THUMBNAIL_PX
        // is a fixed constant, not a measured layout size — the key must stay identical between
        // runs, and a value that depends on this composable's own (sometimes not-yet-settled-on-
        // first-pass) layout constraints would silently change it and always miss.
        val cacheKey = remember(uri) {
            val dateModified = ThumbnailDiskCache.dateModifiedKeyPart(context, uri)
            "$uri|$GRID_THUMBNAIL_PX|$dateModified"
        }
        val cacheFile = remember(cacheKey) { ThumbnailDiskCache.fileFor(context, cacheKey) }
        val cacheHit = remember(cacheFile) {
            cacheFile.exists().also {
                if (it) ThumbnailDiskCache.logHit(cacheKey, cacheFile) else ThumbnailDiskCache.logMiss(cacheKey)
            }
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(if (cacheHit) cacheFile else uri)
                .size(GRID_THUMBNAIL_PX, GRID_THUMBNAIL_PX)
                // Many short-lived tiles churn through a grid on every scroll. A HARDWARE bitmap
                // (Coil's default on API 26+) is a GPU buffer allocated via gralloc IPC — cheap to
                // keep around for one long-lived image, expensive to allocate/free at this rate
                // (framestats showed ~5s GPU-time spikes and ~71MB of live AHardwareBuffers sized
                // exactly like these thumbnails). Software ARGB_8888 is cheaper for this pattern.
                .allowHardware(false)
                .apply {
                    // Cache miss only: once Coil finishes decoding+downsampling, stash the result on
                    // disk for the next cold start. Doesn't block this load — the write itself runs
                    // on ThumbnailDiskCache's own scope, not this composable's — a fast scroll
                    // disposing this tile (LazyVerticalGrid does that the moment it leaves the
                    // visible window) must not cancel a write for a decode that already finished.
                    if (!cacheHit) {
                        listener(onSuccess = { _, result ->
                            (result.image as? BitmapImage)?.bitmap?.let { bitmap ->
                                ThumbnailDiskCache.writeAsync(cacheFile, bitmap)
                            }
                        })
                    }
                }
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
