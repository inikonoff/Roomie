package com.cullect.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cullect.app.data.media.PeriodFilter
import com.cullect.app.data.media.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "cullect_settings")

/**
 * All user-configurable state and the free-swipe-session counter. Backed by DataStore so it
 * survives process death without the overhead of a Room table for what is just scalar state.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val SESSION_SWIPE_COUNT = intPreferencesKey("session_swipe_count")
        val MONETIZATION_ENABLED = booleanPreferencesKey("monetization_enabled")
        val FREE_SWIPE_LIMIT = intPreferencesKey("free_swipe_limit")
        val IS_PREMIUM_UNLOCKED = booleanPreferencesKey("is_premium_unlocked")
        val SWIPE_LEFT_ACTION = stringPreferencesKey("swipe_left_action")
        val SWIPE_RIGHT_ACTION = stringPreferencesKey("swipe_right_action")
        val SWIPE_UP_ACTION = stringPreferencesKey("swipe_up_action")
        val SWIPE_DOWN_ACTION = stringPreferencesKey("swipe_down_action")
        val MOVE_TO_FOLDER_BUCKET_ID = longPreferencesKey("move_to_folder_bucket_id")
        val MOVE_TO_FOLDER_NAME = stringPreferencesKey("move_to_folder_name")
        val MOVE_TO_FOLDER_PATH = stringPreferencesKey("move_to_folder_path")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val CARD_ANIMATION_STYLE = stringPreferencesKey("card_animation_style")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val EDGE_PADDING_DP = intPreferencesKey("edge_padding_dp")
        val CARD_CORNER_RADIUS_DP = intPreferencesKey("card_corner_radius_dp")
        val CARD_BORDER_WIDTH_DP = floatPreferencesKey("card_border_width_dp")
        val STACK_BRIGHTNESS_ENABLED = booleanPreferencesKey("stack_brightness_enabled")
        val STACK_BRIGHTNESS_LEVEL = floatPreferencesKey("stack_brightness_level")
    }

    val settings: Flow<CullectSettings> = context.dataStore.data.map { prefs ->
        val defaults = CullectSettings()
        CullectSettings(
            sortOrder = prefs[Keys.SORT_ORDER]?.let { SortOrder.valueOf(it) } ?: defaults.sortOrder,
            trashRetentionDays = prefs[Keys.TRASH_RETENTION_DAYS] ?: defaults.trashRetentionDays,
            sessionSwipeCount = prefs[Keys.SESSION_SWIPE_COUNT] ?: defaults.sessionSwipeCount,
            monetizationEnabled = prefs[Keys.MONETIZATION_ENABLED] ?: defaults.monetizationEnabled,
            freeSwipeLimit = prefs[Keys.FREE_SWIPE_LIMIT] ?: defaults.freeSwipeLimit,
            isPremiumUnlocked = prefs[Keys.IS_PREMIUM_UNLOCKED] ?: defaults.isPremiumUnlocked,
            swipeLeftAction = prefs[Keys.SWIPE_LEFT_ACTION]?.let { SwipeCardAction.valueOf(it) }
                ?: defaults.swipeLeftAction,
            swipeRightAction = prefs[Keys.SWIPE_RIGHT_ACTION]?.let { SwipeCardAction.valueOf(it) }
                ?: defaults.swipeRightAction,
            swipeUpAction = prefs[Keys.SWIPE_UP_ACTION]?.let { SwipeCardAction.valueOf(it) }
                ?: defaults.swipeUpAction,
            swipeDownAction = prefs[Keys.SWIPE_DOWN_ACTION]?.let { SwipeCardAction.valueOf(it) }
                ?: defaults.swipeDownAction,
            moveToFolderBucketId = prefs[Keys.MOVE_TO_FOLDER_BUCKET_ID],
            moveToFolderName = prefs[Keys.MOVE_TO_FOLDER_NAME],
            moveToFolderRelativePath = prefs[Keys.MOVE_TO_FOLDER_PATH],
            themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: defaults.themeMode,
            cardAnimationStyle = prefs[Keys.CARD_ANIMATION_STYLE]?.let { CardAnimationStyle.valueOf(it) }
                ?: defaults.cardAnimationStyle,
            languageMode = prefs[Keys.LANGUAGE_MODE]?.let { LanguageMode.valueOf(it) } ?: defaults.languageMode,
            edgePaddingDp = prefs[Keys.EDGE_PADDING_DP] ?: defaults.edgePaddingDp,
            cardCornerRadiusDp = prefs[Keys.CARD_CORNER_RADIUS_DP] ?: defaults.cardCornerRadiusDp,
            cardBorderWidthDp = prefs[Keys.CARD_BORDER_WIDTH_DP] ?: defaults.cardBorderWidthDp,
            stackBrightnessEnabled = prefs[Keys.STACK_BRIGHTNESS_ENABLED] ?: defaults.stackBrightnessEnabled,
            stackBrightnessLevel = prefs[Keys.STACK_BRIGHTNESS_LEVEL] ?: defaults.stackBrightnessLevel,
        )
    }

    suspend fun setSortOrder(sortOrder: SortOrder) {
        context.dataStore.edit { it[Keys.SORT_ORDER] = sortOrder.name }
    }

    suspend fun setTrashRetentionDays(days: Int) {
        require(days in CullectSettings.ALLOWED_RETENTION_DAYS)
        context.dataStore.edit { it[Keys.TRASH_RETENTION_DAYS] = days }
    }

    suspend fun setMonetizationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONETIZATION_ENABLED] = enabled }
    }

    suspend fun unlockPremium() {
        context.dataStore.edit { it[Keys.IS_PREMIUM_UNLOCKED] = true }
    }

    /** Rewarded ad: lets the user keep swiping for the remainder of this session only. */
    suspend fun resetSessionSwipeCount() {
        context.dataStore.edit { it[Keys.SESSION_SWIPE_COUNT] = 0 }
    }

    /** [by] lets a caller batch up several swipes into one disk write instead of one per swipe —
     *  see [com.cullect.app.ui.screens.swipe.SwipeSessionViewModel]'s pendingSwipeIncrement. */
    suspend fun incrementSessionSwipeCount(by: Int = 1) {
        context.dataStore.edit {
            val current = it[Keys.SESSION_SWIPE_COUNT] ?: 0
            it[Keys.SESSION_SWIPE_COUNT] = current + by
        }
    }

    suspend fun setSwipeLeftAction(action: SwipeCardAction) {
        context.dataStore.edit { it[Keys.SWIPE_LEFT_ACTION] = action.name }
    }

    suspend fun setSwipeRightAction(action: SwipeCardAction) {
        context.dataStore.edit { it[Keys.SWIPE_RIGHT_ACTION] = action.name }
    }

    suspend fun setSwipeUpAction(action: SwipeCardAction) {
        context.dataStore.edit { it[Keys.SWIPE_UP_ACTION] = action.name }
    }

    suspend fun setSwipeDownAction(action: SwipeCardAction) {
        context.dataStore.edit { it[Keys.SWIPE_DOWN_ACTION] = action.name }
    }

    /** Whether up/down have ever been written — individually (the always-open dropdowns in
     *  Settings) or via a previous preset apply — as opposed to still sitting at [CullectSettings]'s
     *  built-in defaults because nothing has touched them yet. A preset apply uses this to decide
     *  whether it's still safe to also set up/down to its own defaults (first run only) or must
     *  leave them alone (the user, or an earlier preset apply, already has an opinion). */
    suspend fun hasCustomSwipeUpDown(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs.contains(Keys.SWIPE_UP_ACTION) || prefs.contains(Keys.SWIPE_DOWN_ACTION)
    }

    /** Sets all four directions in one write, for a gesture-scheme preset in Settings. */
    suspend fun setSwipeActions(
        left: SwipeCardAction,
        right: SwipeCardAction,
        up: SwipeCardAction,
        down: SwipeCardAction,
    ) {
        context.dataStore.edit {
            it[Keys.SWIPE_LEFT_ACTION] = left.name
            it[Keys.SWIPE_RIGHT_ACTION] = right.name
            it[Keys.SWIPE_UP_ACTION] = up.name
            it[Keys.SWIPE_DOWN_ACTION] = down.name
        }
    }

    suspend fun setMoveToFolder(bucketId: Long, name: String) {
        context.dataStore.edit {
            it[Keys.MOVE_TO_FOLDER_BUCKET_ID] = bucketId
            it[Keys.MOVE_TO_FOLDER_NAME] = name
            it.remove(Keys.MOVE_TO_FOLDER_PATH)
        }
    }

    /** A folder that doesn't exist yet: only its path is known until the first file is moved in. */
    suspend fun setMoveToNewFolder(relativePath: String, name: String) {
        context.dataStore.edit {
            it.remove(Keys.MOVE_TO_FOLDER_BUCKET_ID)
            it[Keys.MOVE_TO_FOLDER_NAME] = name
            it[Keys.MOVE_TO_FOLDER_PATH] = relativePath
        }
    }

    /** Records the bucket a [setMoveToNewFolder] folder turned into once it got its first file. */
    suspend fun setMoveToFolderBucketId(bucketId: Long) {
        context.dataStore.edit { it[Keys.MOVE_TO_FOLDER_BUCKET_ID] = bucketId }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setCardAnimationStyle(style: CardAnimationStyle) {
        context.dataStore.edit { it[Keys.CARD_ANIMATION_STYLE] = style.name }
    }

    suspend fun setLanguageMode(mode: LanguageMode) {
        context.dataStore.edit { it[Keys.LANGUAGE_MODE] = mode.name }
    }

    suspend fun setEdgePaddingDp(value: Int) {
        require(value in CullectSettings.MIN_EDGE_PADDING_DP..CullectSettings.MAX_EDGE_PADDING_DP)
        context.dataStore.edit { it[Keys.EDGE_PADDING_DP] = value }
    }

    suspend fun setCardCornerRadiusDp(value: Int) {
        require(value in CullectSettings.MIN_CARD_CORNER_RADIUS_DP..CullectSettings.MAX_CARD_CORNER_RADIUS_DP)
        context.dataStore.edit { it[Keys.CARD_CORNER_RADIUS_DP] = value }
    }

    suspend fun setCardBorderWidthDp(value: Float) {
        require(value in CullectSettings.MIN_CARD_BORDER_WIDTH_DP..CullectSettings.MAX_CARD_BORDER_WIDTH_DP)
        context.dataStore.edit { it[Keys.CARD_BORDER_WIDTH_DP] = value }
    }

    suspend fun setStackBrightnessEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STACK_BRIGHTNESS_ENABLED] = enabled }
    }

    suspend fun setStackBrightnessLevel(value: Float) {
        require(value in CullectSettings.MIN_STACK_BRIGHTNESS..CullectSettings.MAX_STACK_BRIGHTNESS)
        context.dataStore.edit { it[Keys.STACK_BRIGHTNESS_LEVEL] = value }
    }

    /** One saved period filter per folder (keyed by bucketId; `null` is the "All photos" bucket) —
     *  a map of id to filter rather than a single scalar, which is why this uses its own
     *  dynamically-named DataStore key instead of a field on [CullectSettings] like everything else
     *  here. Not worth a Room table for what's still just one small value per folder. */
    suspend fun getFolderPeriodFilter(bucketId: Long?): PeriodFilter {
        val key = stringPreferencesKey("period_filter_${bucketId ?: "all"}")
        return context.dataStore.data.first()[key]?.let { runCatching { PeriodFilter.valueOf(it) }.getOrNull() }
            ?: PeriodFilter.ALL
    }

    suspend fun setFolderPeriodFilter(bucketId: Long?, filter: PeriodFilter) {
        val key = stringPreferencesKey("period_filter_${bucketId ?: "all"}")
        context.dataStore.edit { it[key] = filter.name }
    }
}
