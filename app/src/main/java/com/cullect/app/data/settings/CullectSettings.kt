package com.cullect.app.data.settings

import com.cullect.app.data.media.SortOrder

data class CullectSettings(
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val trashRetentionDays: Int = 3,
    val sessionSwipeCount: Int = 0,
    val monetizationEnabled: Boolean = false,
    val freeSwipeLimit: Int = 100,
    val isPremiumUnlocked: Boolean = false,
    val swipeLeftAction: SwipeCardAction = SwipeCardAction.DELETE,
    val swipeRightAction: SwipeCardAction = SwipeCardAction.KEEP,
    val swipeUpAction: SwipeCardAction = SwipeCardAction.MOVE_TO_FOLDER,
    val swipeDownAction: SwipeCardAction = SwipeCardAction.POSTPONE,
    /** Destination for [SwipeCardAction.MOVE_TO_FOLDER]; null until the user picks one in Settings. */
    val moveToFolderBucketId: Long? = null,
    val moveToFolderName: String? = null,
    /** Set for a folder the user created in Settings: it may not exist yet (Android only creates a
     *  folder when the first file lands in it), so the destination is a path, not a bucket. When
     *  set it wins over [moveToFolderBucketId]. */
    val moveToFolderRelativePath: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cardAnimationStyle: CardAnimationStyle = CardAnimationStyle.CLASSIC,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    /** Padding around the swipe screen's card stack. A plain number, not a unit toggle — shown in
     *  Settings as just "24", not "24dp". */
    val edgePaddingDp: Int = 24,
    /** Corner radius and border width of the swipe stack's cards. */
    val cardCornerRadiusDp: Int = 32,
    val cardBorderWidthDp: Float = 1f,
    /** Overrides the window brightness only while the swipe stack is on screen; everywhere else the
     *  app follows the system. [stackBrightnessLevel] is 0..1 of the screen's range, so it can be
     *  above the current system level (useful outdoors). */
    val stackBrightnessEnabled: Boolean = false,
    val stackBrightnessLevel: Float = 1f,
) {
    val hasReachedSwipeLimit: Boolean
        get() = monetizationEnabled && !isPremiumUnlocked && sessionSwipeCount >= freeSwipeLimit

    companion object {
        val ALLOWED_RETENTION_DAYS = (1..30).toList()
        const val MIN_EDGE_PADDING_DP = 0
        const val MAX_EDGE_PADDING_DP = 48
        const val MIN_CARD_CORNER_RADIUS_DP = 8
        const val MAX_CARD_CORNER_RADIUS_DP = 40
        const val MIN_CARD_BORDER_WIDTH_DP = 0f
        const val MAX_CARD_BORDER_WIDTH_DP = 4f
        /** Not 0: some panels read that as "backlight off". */
        const val MIN_STACK_BRIGHTNESS = 0.1f
        const val MAX_STACK_BRIGHTNESS = 1f
    }
}
