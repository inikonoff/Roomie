package com.roomie.app.data.settings

import com.roomie.app.data.media.SortOrder

data class RoomieSettings(
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val trashRetentionDays: Int = 3,
    val autoDeleteEmptyFolders: Boolean = true,
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
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val cardAnimationStyle: CardAnimationStyle = CardAnimationStyle.CLASSIC,
    val languageMode: LanguageMode = LanguageMode.SYSTEM,
    /** Padding around the swipe screen's card stack. A plain number, not a unit toggle — shown in
     *  Settings as just "24", not "24dp". */
    val edgePaddingDp: Int = 24,
    /** Temporary — see the Settings screen's own "temporary" block. Corner radius and border width
     *  of the swipe stack's top card, being tuned live rather than guessed; remove both once a
     *  value is settled on and hardcode it instead. */
    val cardCornerRadiusDp: Int = 32,
    val cardBorderWidthDp: Float = 1f,
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
    }
}
