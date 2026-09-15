package com.roomie.app.data.media

/**
 * Wraps every item in its own singleton [MediaGroup] so each photo or video is an independent
 * swipeable/deletable card — no burst clustering.
 */
fun List<MediaItem>.groupIntoUnits(): List<MediaGroup> = map {
    MediaGroup(key = "group_${it.stableId}", items = listOf(it))
}
