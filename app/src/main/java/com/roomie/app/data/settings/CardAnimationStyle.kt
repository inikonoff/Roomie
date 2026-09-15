package com.roomie.app.data.settings

/** How a card visually reacts while being dragged and flung out on a swipe. */
enum class CardAnimationStyle {
    /** Rotates as it's dragged, like a card pivoting off a table. */
    CLASSIC,

    /** No rotation — slides straight and fades out as it leaves. */
    FADE,

    /** No rotation — slides straight and shrinks as it leaves, like it's receding into the distance. */
    SHRINK,
}
