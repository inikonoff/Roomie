package com.roomie.app.data.settings

/** What a swipe in a given direction does to the current card; user-configurable per direction. */
enum class SwipeCardAction {
    DELETE,
    KEEP,
    MOVE_TO_FOLDER,
    POSTPONE,
    NONE,
}
