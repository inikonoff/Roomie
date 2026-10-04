package com.cullect.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.cullect.app.work.TrashCleanupWorker
import kotlinx.coroutines.Dispatchers

class CullectApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TrashCleanupWorker.schedule(this)
    }

    /** Registers the video-frame decoder as a fallback for video covers on API < 29 (see
     *  [com.cullect.app.ui.components.MediaThumbnail]), and enables downsampling-friendly defaults
     *  to avoid OOM on large photos. An explicit memory cache (left unset, Coil still has one, but
     *  a much smaller default) is what actually makes scrolling back over recently-seen thumbnails
     *  feel instant instead of re-decoding them. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            // Crossfade is applied per-request instead of globally — see SwipeCard, the only place
            // it's still wanted. A grid tile shows straight from its own LRU cache or a placeholder
            // (see MediaThumbnail), so a global fade would apply to nothing there anyway; it's the
            // full-screen swipe card, decoded via Coil on every new top-of-stack card, where a
            // crossfade actually matters.
            // A fast fling through a grid can otherwise queue dozens of decodes at once — capping
            // how many run concurrently is what turns that into a steady trickle of ~150-300ms
            // frame spikes into a smooth scroll, at the cost of slightly later delivery per tile
            // (masked by the surface-colored placeholder every grid tile already sits on).
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
            .build()
}
