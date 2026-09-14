package com.roomie.probe

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Deliberately does nothing else: no permissions, no libraries beyond AppCompat, just a visible
 * on-screen stopwatch. If this also dies a fixed ~1.1s after launch on the test device, that
 * proves the OS kills ANY freshly installed app on a timer, unrelated to anything Roomie does.
 */
class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var textView: TextView
    private var startTime = 0L

    private val tick = object : Runnable {
        override fun run() {
            val elapsedMs = SystemClock.elapsedRealtime() - startTime
            textView.text = "Probe alive for %.1f s\n\nIf this counter stops or the app\ndisappears, note the number shown.".format(elapsedMs / 1000.0)
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textView = TextView(this).apply {
            textSize = 22f
            setPadding(48, 200, 48, 48)
        }
        setContentView(textView)
        startTime = SystemClock.elapsedRealtime()
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
