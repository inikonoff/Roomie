package com.roomie.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Rolling in-memory + on-disk log, readable from inside the app (Settings → Logs) without adb.
 * Bounded so it can't grow forever — oldest lines drop off once MAX_LINES is exceeded.
 */
object AppLogger {
    private const val MAX_LINES = 1000
    private const val FILE_NAME = "app_log.txt"
    private val buffer = ConcurrentLinkedDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        val line = "${timeFormat.format(System.currentTimeMillis())} [$tag] $message"
        buffer.addLast(line)
        while (buffer.size > MAX_LINES) buffer.pollFirst()
        android.util.Log.d(tag, message) // same call that used to be scattered around — logcat still gets it
    }

    fun snapshot(): List<String> = buffer.toList()

    fun clear() = buffer.clear()

    /** Writes the current buffer to a file under the app's files dir and returns it, for sharing. */
    fun writeToFile(context: Context): File {
        val file = File(context.filesDir, FILE_NAME)
        file.writeText(buffer.joinToString("\n"))
        return file
    }
}
