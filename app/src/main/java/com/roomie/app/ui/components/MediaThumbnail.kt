package com.roomie.app.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        var sizePx by remember { mutableStateOf(IntSize.Zero) }
        val bitmap by produceState<Bitmap?>(initialValue = null, uri, sizePx) {
            value = if (sizePx.width > 0 && sizePx.height > 0) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.loadThumbnail(
                            uri,
                            Size(sizePx.width, sizePx.height),
                            null,
                        )
                    }.getOrNull()
                }
            } else {
                null
            }
        }
        Box(modifier = modifier.onSizeChanged { sizePx = it }) {
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
