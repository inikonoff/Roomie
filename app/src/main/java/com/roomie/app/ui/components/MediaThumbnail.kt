package com.roomie.app.ui.components

import android.graphics.Bitmap
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val VIDEO_THUMBNAIL_PX = 320

/**
 * Process-wide cache for decoded video thumbnails. Every tile requests the same fixed
 * [VIDEO_THUMBNAIL_PX] size regardless of its actual on-screen size, so a URI is a valid cache
 * key; without this, scrolling a video tile off-screen and back re-ran the (relatively expensive)
 * frame-extraction decode every single time, which is what was making video-heavy folders lag and
 * leave tiles blank — the grid was re-decoding faster than it could keep up with scrolling.
 */
private val videoThumbnailCache = LruCache<String, Bitmap>(64)

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
                    runCatching {
                        context.contentResolver.loadThumbnail(
                            uri,
                            Size(VIDEO_THUMBNAIL_PX, VIDEO_THUMBNAIL_PX),
                            null,
                        )
                    }.getOrNull()?.also { videoThumbnailCache.put(key, it) }
                }
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
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
