package com.roomie.app.util

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Shared by the Summary screen (bytes freed this session) and Settings (thumbnail disk cache
 *  size) so both report storage the same way. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val exponent = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exponent)
    return String.format(Locale.getDefault(), "%.1f %s", value, units[exponent - 1])
}
