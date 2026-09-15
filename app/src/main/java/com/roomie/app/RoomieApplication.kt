package com.roomie.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.roomie.app.work.TrashCleanupWorker

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
     *  to avoid OOM on large photos. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
