package com.roomie.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

/** Both process-wide bitmap caches are sized in bytes (via `sizeOf`), not entry count — a 320x320
 *  ARGB_8888 bitmap is ~400KB, so an entry-count limit like the old `LruCache<_, _>(300)` could
 *  silently hold well over 100MB. Photo cache is sized generously enough that a two-screen fling
 *  down and immediately back doesn't evict tiles seen moments ago — a smaller cap was flushing
 *  already-seen tiles well before the user scrolled back to them. */
private const val PHOTO_CACHE_BYTES = 40 * 1024 * 1024
private const val VIDEO_CACHE_BYTES = 24 * 1024 * 1024

/** Process-wide cache of already-decoded photo thumbnails, keyed by uri + target size (no
 *  dateModified — that's only needed to invalidate the *disk* cache; an in-memory entry only
 *  outlives the process anyway). A hit here renders straight to an [Image], no Coil involved at
 *  all, which is what makes scrolling back over already-seen tiles instant regardless of whether
 *  a fling is still in progress. */
private val photoThumbnailCache = object : LruCache<String, Bitmap>(PHOTO_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/**
 * Process-wide cache for decoded video thumbnails. Every tile requests the same fixed
 * [VIDEO_THUMBNAIL_PX] size regardless of its actual on-screen size, so a URI is a valid cache
 * key; without this, scrolling a video tile off-screen and back re-ran the (relatively expensive)
 * frame-extraction decode every single time, which is what was making video-heavy folders lag and
 * leave tiles blank — the grid was re-decoding faster than it could keep up with scrolling.
 */
private val videoThumbnailCache = object : LruCache<String, Bitmap>(VIDEO_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

/** Bounds how many `loadThumbnail` (MediaMetadataRetriever-backed) calls run at once — a fast
 *  fling through a video-heavy folder can otherwise fire off a dozen of these together, each a
 *  real frame-extraction decode, which is what produced this app's worst multi-second frames.
 *  Coil's own decode work (the photo path) is capped the same way via the shared ImageLoader's
 *  decoderCoroutineContext — see RoomieApplication. A cell that scrolls away while waiting on this
 *  semaphore has its produceState coroutine cancelled by Compose itself (LazyVerticalGrid disposes
 *  off-screen items), which withPermit honors — no manual job-cancellation bookkeeping needed. */
private val videoDecodeLimiter = Semaphore(3)

/** How long a scroll must stay stopped before new decode work (disk read, source decode, cache
 *  write) is allowed to start — only debounced on the stop edge, so a fling settling through a
 *  couple of micro-stops doesn't fire a burst of decodes, while resuming a scroll gates
 *  immediately. Reading an already-resolved bitmap (memory cache or one this composable instance
 *  already finished loading) is never gated, only *new* work is. */
private const val SCROLL_STOP_DEBOUNCE_MS = 64L

/** Gates new thumbnail work behind "not actively scrolling" — see [SCROLL_STOP_DEBOUNCE_MS]. */
@Composable
fun rememberAllowThumbnailDecode(isScrollInProgress: Boolean): Boolean {
    var allow by remember { mutableStateOf(!isScrollInProgress) }
    LaunchedEffect(isScrollInProgress) {
        if (isScrollInProgress) {
            allow = false
        } else {
            delay(SCROLL_STOP_DEBOUNCE_MS)
            allow = true
        }
    }
    return allow
}

/**
 * Grid-safe thumbnail for a gallery item. Both photos and videos go through the same shape: a
 * process-wide in-memory [LruCache] hit renders directly via [Image] (no Coil, no disk, no
 * MediaStore); a miss falls through to the on-disk cache, which is always read (cheap, a small
 * local file); only when that also misses does a real decode of the source happen, gated behind
 * [allowDecode] — see [rememberAllowThumbnailDecode]. Videos use
 * [android.content.ContentResolver.loadThumbnail] directly (API 29+) instead of Coil's video-frame
 * decoder — under a grid's concurrent load, the decoder was unreliable enough that video tiles
 * routinely rendered blank. Below API 29, video falls back to Coil since `loadThumbnail` doesn't
 * exist yet there.
 *
 * [dateModified] is the source's own MediaStore `DATE_MODIFIED`, read once by the caller's
 * ViewModel when it loaded the list — this composable never queries MediaStore itself, so a tile
 * scrolling into view for the first time never does a blocking IPC call on the UI thread.
 */
@Composable
fun MediaThumbnail(
    uri: Uri,
    isVideo: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    dateModified: Long = 0L,
    allowDecode: Boolean = true,
) {
    val context = LocalContext.current
    val placeholder = @Composable { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) }
    // Read via rememberUpdatedState, not a produceState key: gating only the real-decode branch
    // below (through the snapshotFlow wait) means scroll starting/stopping never cancels and
    // restarts the whole producer coroutine — which is what caused the white-flash bug (a
    // key-driven restart re-began at photoThumbnailCache.get(key)/initialValue, briefly showing
    // the placeholder again for a tile whose disk-cache read was already in flight or already
    // resolved but not yet promoted to the in-memory cache).
    val allowDecodeState = rememberUpdatedState(allowDecode)

    if (isVideo && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val key = uri.toString()
        val bitmap by produceState(videoThumbnailCache.get(key), uri, dateModified) {
            if (value != null) return@produceState
            // Disk cache first — MediaMetadataRetriever-backed loadThumbnail is a real
            // frame-extraction decode, objectively pricier than a photo decode, and until now was
            // only ever cached in memory for the life of the process. dateModified in the key means
            // a video replaced/edited outside Roomie invalidates on its own instead of serving a
            // stale frame forever. This disk read always runs, even mid-fling — it's a small local
            // file, cheap enough that gating it bought nothing but the flash bug above.
            val cacheKey = "video|$key|$VIDEO_THUMBNAIL_PX|$dateModified"
            val fromDisk = withContext(Dispatchers.IO) {
                val cacheFile = ThumbnailDiskCache.fileFor(context, cacheKey)
                if (cacheFile.exists()) {
                    ThumbnailDiskCache.logHit(cacheKey, cacheFile)
                    BitmapFactory.decodeFile(cacheFile.path)
                } else {
                    null
                }
            }
            if (fromDisk != null) {
                videoThumbnailCache.put(key, fromDisk)
                value = fromDisk
                return@produceState
            }
            // A real frame-extraction decode is real work — wait for scrolling to actually stop
            // before starting one, without cancelling/restarting this coroutine while we wait.
            snapshotFlow { allowDecodeState.value }.first { it }
            ThumbnailDiskCache.logMiss(cacheKey)
            value = withContext(Dispatchers.IO) {
                val cacheFile = ThumbnailDiskCache.fileFor(context, cacheKey)
                videoDecodeLimiter.withPermit {
                    runCatching {
                        context.contentResolver.loadThumbnail(
                            uri,
                            Size(VIDEO_THUMBNAIL_PX, VIDEO_THUMBNAIL_PX),
                            null,
                        )
                    }.getOrNull()
                }?.also { bmp -> ThumbnailDiskCache.writeAsync(cacheFile, bmp) }
            }?.also { videoThumbnailCache.put(key, it) }
        }
        Box(modifier = modifier) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                placeholder()
            }
        }
    } else {
        val key = "$uri|$GRID_THUMBNAIL_PX"
        val bitmap by produceState(photoThumbnailCache.get(key), uri, dateModified) {
            if (value != null) return@produceState
            val cacheKey = "$key|$dateModified"
            // Disk-JPEG read always runs, even mid-fling — a small local file is cheap enough that
            // gating it bought nothing but the white-flash bug (see the video branch's doc above).
            val fromDisk = withContext(Dispatchers.IO) {
                val cacheFile = ThumbnailDiskCache.fileFor(context, cacheKey)
                if (cacheFile.exists()) {
                    ThumbnailDiskCache.logHit(cacheKey, cacheFile)
                    BitmapFactory.decodeFile(cacheFile.path)
                } else {
                    null
                }
            }
            if (fromDisk != null) {
                photoThumbnailCache.put(key, fromDisk)
                value = fromDisk
                return@produceState
            }
            // Decoding the source (and writing the result back to disk) is the real work — wait
            // for scrolling to actually stop before starting it, without cancelling/restarting
            // this coroutine while we wait.
            snapshotFlow { allowDecodeState.value }.first { it }
            ThumbnailDiskCache.logMiss(cacheKey)
            value = withContext(Dispatchers.IO) {
                val cacheFile = ThumbnailDiskCache.fileFor(context, cacheKey)
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(GRID_THUMBNAIL_PX, GRID_THUMBNAIL_PX)
                    // Many short-lived tiles churn through a grid on every scroll. A HARDWARE
                    // bitmap (Coil's default on API 26+) is a GPU buffer allocated via gralloc IPC
                    // — cheap to keep around for one long-lived image, expensive to allocate/free
                    // at this rate (framestats showed ~5s GPU-time spikes and ~71MB of live
                    // AHardwareBuffers sized exactly like these thumbnails). Software ARGB_8888 is
                    // cheaper for this pattern.
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request) as? SuccessResult
                (result?.image as? BitmapImage)?.bitmap?.also { bmp ->
                    ThumbnailDiskCache.writeAsync(cacheFile, bmp)
                }
            }?.also { photoThumbnailCache.put(key, it) }
        }
        Box(modifier = modifier) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                placeholder()
            }
        }
    }
}
