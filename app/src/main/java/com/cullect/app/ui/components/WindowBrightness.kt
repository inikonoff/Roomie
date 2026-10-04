package com.cullect.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Overrides this window's brightness for as long as the caller stays in the composition, and hands
 * it back to the system when it leaves (navigating away) or [enabled] turns off. The override is
 * per-window, so it never touches the system setting and the OS ignores it while the app is in the
 * background — only the screen showing this composable is affected.
 */
@Composable
fun WindowBrightnessOverride(enabled: Boolean, level: Float) {
    val context = LocalContext.current
    DisposableEffect(enabled, level) {
        val window = context.findActivity()?.window
        if (enabled && window != null) {
            window.attributes = window.attributes.apply { screenBrightness = level }
        }
        onDispose {
            if (enabled && window != null) {
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
