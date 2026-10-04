package com.cullect.app.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The [Activity] behind a Compose [Context], which is usually wrapped (themes, Hilt-style
 *  wrappers) rather than being the activity itself. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
