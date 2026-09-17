package com.roomie.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.roomie.app.work.TrashCleanupWorker
import kotlinx.coroutines.Dispatchers

class RoomieApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // Earliest available hook: a crash inside a library's own startup (WorkManager, DataStore,
        // Room, ...) runs as part of process/ContentProvider init, before onCreate ever fires.
        CrashReporter.install(this)
        CrashReporter.mark(this, "attachBaseContext:done (about to run androidx.startup initializers)")
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.mark(this, "onCreate:start")

        CrashReporter.mark(this, "onCreate:before AppContainer()")
        container = AppContainer(this)
        CrashReporter.mark(this, "onCreate:after AppContainer()")

        CrashReporter.mark(this, "onCreate:before TrashCleanupWorker.schedule()")
        TrashCleanupWorker.schedule(this)
        CrashReporter.mark(this, "onCreate:done")
    }

    /** Registers the video-frame decoder as a fallback for video covers on API < 29 (see
     *  [com.roomie.app.ui.components.MediaThumbnail]), and enables downsampling-friendly defaults
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
            .crossfade(true)
            // A fast fling through a grid can otherwise queue dozens of decodes at once — capping
            // how many run concurrently is what turns that into a steady trickle of ~150-300ms
            // frame spikes into a smooth scroll, at the cost of slightly later delivery per tile
            // (masked by the surface-colored placeholder every grid tile already sits on).
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
            .build()
}
