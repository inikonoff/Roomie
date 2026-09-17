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
) {
    val hasReachedSwipeLimit: Boolean
        get() = monetizationEnabled && !isPremiumUnlocked && sessionSwipeCount >= freeSwipeLimit

    companion object {
        val ALLOWED_RETENTION_DAYS = (1..30).toList()
        const val MIN_EDGE_PADDING_DP = 0
        const val MAX_EDGE_PADDING_DP = 48
    }
}
