package com.cullect.app.ui.strings

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * All user-facing text in the app. Kept as a plain interface + two hand-written implementations
 * (not Android string resources) because [com.cullect.app.MainActivity] is a bare
 * `ComponentActivity` with a Compose-only UI — there is no XML/fragment layer that would benefit
 * from the resource system, and a `CompositionLocal` lets the in-app language switch take effect
 * immediately without recreating the Activity.
 */
interface AppStrings {
    // Top bar / navigation
    val appName: String
    val back: String
    val settingsTitle: String
    val settingsContentDescription: String
    val videoContentDescription: String

    // Folder list
    val permissionRationale: String
    val grantAccess: String
    val permissionBlockedHint: String
    val openAppSettings: String
    val allPhotos: String
    val trash: String
    val empty: String
    fun itemsCount(count: Int): String
    val periodAll: String
    val periodDay: String
    val periodMonth: String
    val periodYear: String

    // Folder grid
    val nothingHere: String

    // Swipe screen: video playback
    val playVideo: String
    val closeVideo: String

    // Swipe screen: position counter, e.g. "12 of 345"
    fun counterOfTotal(current: Int, total: Int): String

    // Swipe screen: current photo/video's own capture date+time, e.g. "Sep 12, 2026, 15:47"
    fun dateTaken(millis: Long): String

    // Trash folder: permanent delete
    val deleteForever: String
    val emptyTrash: String
    val emptyTrashConfirmTitle: String
    fun emptyTrashConfirmMessage(count: Int): String
    fun deletingProgress(done: Int, total: Int): String

    // Settings screen
    val sectionAppearance: String
    val sectionCleanup: String
    val sectionGestures: String
    val sectionTheme: String
    val themeLight: String
    val themeDark: String
    val themeSystem: String
    val sectionLanguage: String
    val languageSystem: String
    val languageEnglish: String
    val languageRussian: String
    val sectionCardOrder: String
    val sortNewestFirst: String
    val sortOldestFirst: String
    val sectionTrashRetention: String
    fun retentionDays(days: Int): String
    val sectionCardAnimation: String
    val animationClassic: String
    val animationFade: String
    val animationShrink: String
    val edgePadding: String
    val stackBrightness: String
    val stackBrightnessHint: String
    val stackBrightnessLevel: String
    val sectionCardShape: String
    val cardCornerRadius: String
    val cardBorderWidth: String
    val sectionSwipeGestures: String
    val presetClassic: String
    val presetBrowseDeleteUp: String
    val presetClassicDescription: String
    val presetBrowseDescription: String
    val swipeRight: String
    val swipeLeft: String
    val swipeUp: String
    val swipeDown: String
    val actionDelete: String
    val actionKeep: String
    val actionMoveToFolder: String
    val actionPostpone: String
    val actionNone: String
    val actionPreviousPhoto: String
    val actionNextPhoto: String
    val moveToFolderDestination: String
    val notSet: String
    val chooseAFolder: String
    val manageMediaTitle: String
    val manageMediaHint: String
    val manageMediaOn: String
    val manageMediaOff: String
    val newFolder: String
    val newFolderName: String
    val newFolderInParent: String
    val newFolderInvalidName: String
    val createAction: String
    val folderWillBeCreated: String
    val noFoldersFoundYet: String
    val cancel: String
    val clearThumbnailCache: String
    val enableSwipeLimit: String
    val unlockedLimitDisabled: String
    val refreshLogs: String
    val shareLogs: String
    val clearLogs: String

    // Swipe screen
    val reviewTrash: String
    val folderIsClean: String
    val undo: String
    val selectMoveFolderPrompt: String

    // Trash folder
    fun trashTitle(count: Int): String
    fun expiredTrashCount(count: Int): String
    val trashIsEmpty: String
    val restore: String
    val restoreAll: String
    val selectAll: String
    val restoreSelected: String
    val cancelSelection: String
    fun selectedCount(count: Int): String
    fun restoringProgress(done: Int, total: Int): String
    fun restoredFiles(count: Int): String

    // Summary
    val allCleanedUp: String
    fun itemsRemoved(count: Int): String
    fun bytesFreed(formatted: String): String
    val pendingTrashCleanup: String
    val done: String

    // Swipe limit
    val freeSwipeLimitReached: String
    val swipeLimitExplanation: String
    val watchAdToContinue: String
    val noAdAvailable: String
    val unlockForever: String
    val purchasesNotSetUp: String
    val backToFolders: String
}

object EnglishStrings : AppStrings {
    override val appName = "Cullect"
    override val back = "Back"
    override val settingsTitle = "Settings"
    override val settingsContentDescription = "Settings"
    override val videoContentDescription = "Video"

    override val permissionRationale =
        "Cullect needs access to your photos and videos to help you clean up your gallery."
    override val grantAccess = "Grant access"
    override val permissionBlockedHint =
        "Access to photos and videos is turned off. Allow it in the app's system settings to continue."
    override val openAppSettings = "Open settings"
    override val allPhotos = "All photos"
    override val trash = "Trash"
    override val empty = "Empty"
    override fun itemsCount(count: Int) = "$count items"
    override val periodAll = "All time"
    override val periodDay = "Day"
    override val periodMonth = "Month"
    override val periodYear = "Year"

    override val nothingHere = "Nothing here."

    override val playVideo = "Play video"
    override val closeVideo = "Close video"

    override fun counterOfTotal(current: Int, total: Int) = "$current of $total"

    override fun dateTaken(millis: Long): String =
        DATE_TAKEN_FORMATTER_EN.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    override val deleteForever = "Delete forever"
    override val emptyTrash = "Empty trash"
    override val emptyTrashConfirmTitle = "Empty trash?"
    override fun emptyTrashConfirmMessage(count: Int) =
        "This permanently deletes $count item${if (count == 1) "" else "s"}. This can't be undone."
    override fun deletingProgress(done: Int, total: Int) = "Deleting… $done / $total"

    override val sectionAppearance = "Appearance"
    override val sectionCleanup = "Cleanup"
    override val sectionGestures = "Gestures"
    override val sectionTheme = "Theme"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val themeSystem = "System"
    override val sectionLanguage = "Language"
    override val languageSystem = "System"
    override val languageEnglish = "English"
    override val languageRussian = "Russian"
    override val sectionCardOrder = "Card order"
    override val sortNewestFirst = "Newest first"
    override val sortOldestFirst = "Oldest first"
    override val sectionTrashRetention = "Trash retention"
    override fun retentionDays(days: Int) = "${days}d"
    override val sectionCardAnimation = "Card animation"
    override val animationClassic = "Classic"
    override val animationFade = "Fade"
    override val animationShrink = "Shrink"
    override val edgePadding = "Padding"
    override val stackBrightness = "Brightness while swiping"
    override val stackBrightnessHint = "Only while going through photos; the rest of the app follows the system brightness."
    override val stackBrightnessLevel = "Level"
    override val sectionCardShape = "Card shape"
    override val cardCornerRadius = "Corner radius"
    override val cardBorderWidth = "Border width"
    override val sectionSwipeGestures = "Swipe gestures"
    override val presetClassic = "Classic"
    override val presetBrowseDeleteUp = "Browse"
    override val presetClassicDescription = "Right keeps, left deletes, up moves to folder, down postpones."
    override val presetBrowseDescription = "Left/right just browse — nothing is changed. Up deletes, down postpones."
    override val swipeRight = "Swipe right"
    override val swipeLeft = "Swipe left"
    override val swipeUp = "Swipe up"
    override val swipeDown = "Swipe down"
    override val actionDelete = "Delete"
    override val actionKeep = "Keep (next card)"
    override val actionMoveToFolder = "Move to folder"
    override val actionPostpone = "Postpone"
    override val actionNone = "Do nothing"
    override val actionPreviousPhoto = "Previous photo"
    override val actionNextPhoto = "Next photo"
    override val moveToFolderDestination = "\"Move to folder\" destination"
    override val notSet = "Not set"
    override val chooseAFolder = "Choose a folder"
    override val manageMediaTitle = "Move without confirmation"
    override val manageMediaHint = "Allow Cullect to manage media files in system settings, and moving a photo will no longer ask for confirmation. Without it, moves are confirmed together in one dialog."
    override val manageMediaOn = "On"
    override val manageMediaOff = "Off"
    override val newFolder = "New folder"
    override val newFolderName = "Folder name"
    override val newFolderInParent = "Created inside DCIM (photos and videos both go there)"
    override val newFolderInvalidName = "Use up to 50 characters, without / \\ : * ? \" < > | and not starting with a dot."
    override val createAction = "Create"
    override val folderWillBeCreated = " (will be created)"
    override val noFoldersFoundYet = "No folders found yet."
    override val cancel = "Cancel"
    override val clearThumbnailCache = "Clear thumbnail cache"
    override val enableSwipeLimit = "Enable swipe limit & monetization"
    override val unlockedLimitDisabled = "Unlocked — limit disabled"
    override val refreshLogs = "Refresh"
    override val shareLogs = "Share"
    override val clearLogs = "Clear"

    override val reviewTrash = "Review trash"
    override val folderIsClean = "Nothing left here — this folder is clean."
    override val undo = "Undo"
    override val selectMoveFolderPrompt = "Choose a destination folder in Settings first"

    override fun trashTitle(count: Int) = "Trash ($count)"
    override fun expiredTrashCount(count: Int) = "$count expired — tap Empty trash to free up space"
    override val trashIsEmpty = "Trash is empty."
    override val restore = "Restore"
    override val restoreAll = "Restore all"
    override val selectAll = "Select all"
    override val restoreSelected = "Restore selected"
    override val cancelSelection = "Cancel selection"
    override fun selectedCount(count: Int) = "Selected: $count"
    override fun restoringProgress(done: Int, total: Int) = "Restoring… $done / $total"
    override fun restoredFiles(count: Int) = "Restored $count file${if (count == 1) "" else "s"}"

    override val allCleanedUp = "All cleaned up!"
    override fun itemsRemoved(count: Int) = "$count item${if (count == 1) "" else "s"} removed"
    override fun bytesFreed(formatted: String) = "$formatted freed"
    override val pendingTrashCleanup = "Files are in Trash — space frees up once you empty it."
    override val done = "Done"

    override val freeSwipeLimitReached = "Free swipe limit reached"
    override val swipeLimitExplanation = "Watch a short ad to keep going, or unlock unlimited swiping for good."
    override val watchAdToContinue = "Watch ad to continue"
    override val noAdAvailable = "No ad available right now."
    override val unlockForever = "Unlock forever"
    override val purchasesNotSetUp = "Purchases aren't set up yet."
    override val backToFolders = "Back to folders"
}

object RussianStrings : AppStrings {
    override val appName = "Cullect"
    override val back = "Назад"
    override val settingsTitle = "Настройки"
    override val settingsContentDescription = "Настройки"
    override val videoContentDescription = "Видео"

    override val permissionRationale =
        "Cullect нужен доступ к фото и видео, чтобы помочь навести порядок в галерее."
    override val grantAccess = "Предоставить доступ"
    override val permissionBlockedHint =
        "Доступ к фото и видео отключён. Разрешите его в системных настройках приложения, чтобы продолжить."
    override val openAppSettings = "Открыть настройки"
    override val allPhotos = "Все фото"
    override val trash = "Корзина"
    override val empty = "Пусто"
    override fun itemsCount(count: Int) = "$count шт."
    override val periodAll = "Всё время"
    override val periodDay = "День"
    override val periodMonth = "Месяц"
    override val periodYear = "Год"

    override val nothingHere = "Здесь пусто."

    override val playVideo = "Воспроизвести видео"
    override val closeVideo = "Закрыть видео"

    override fun counterOfTotal(current: Int, total: Int) = "$current из $total"

    override fun dateTaken(millis: Long): String =
        DATE_TAKEN_FORMATTER_RU.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    override val deleteForever = "Удалить навсегда"
    override val emptyTrash = "Очистить корзину"
    override val emptyTrashConfirmTitle = "Очистить корзину?"
    override fun emptyTrashConfirmMessage(count: Int) =
        "Будет безвозвратно удалено объектов: $count. Это нельзя отменить."
    override fun deletingProgress(done: Int, total: Int) = "Удаление… $done из $total"

    override val sectionAppearance = "Оформление"
    override val sectionCleanup = "Очистка"
    override val sectionGestures = "Жесты"
    override val sectionTheme = "Тема"
    override val themeLight = "Светлая"
    override val themeDark = "Тёмная"
    override val themeSystem = "Системная"
    override val sectionLanguage = "Язык"
    override val languageSystem = "Система"
    override val languageEnglish = "English"
    override val languageRussian = "Русский"
    override val sectionCardOrder = "Порядок карточек"
    override val sortNewestFirst = "Сначала новые"
    override val sortOldestFirst = "Сначала старые"
    override val sectionTrashRetention = "Хранение в корзине"
    override fun retentionDays(days: Int) = "${days} дн."
    override val sectionCardAnimation = "Анимация карточек"
    override val animationClassic = "Классика"
    override val animationFade = "Затухание"
    override val animationShrink = "Сжатие"
    override val edgePadding = "Отступ"
    override val stackBrightness = "Яркость при просмотре"
    override val stackBrightnessHint = "Только пока листаете фото; в остальном приложении — системная яркость."
    override val stackBrightnessLevel = "Уровень"
    override val sectionCardShape = "Форма карточки"
    override val cardCornerRadius = "Радиус угла"
    override val cardBorderWidth = "Толщина обводки"
    override val sectionSwipeGestures = "Жесты свайпа"
    override val presetClassic = "Классика"
    override val presetBrowseDeleteUp = "Обзор"
    override val presetClassicDescription =
        "Вправо — оставить, влево — удалить, вверх — переместить в папку, вниз — отложить."
    override val presetBrowseDescription =
        "Влево/вправо — просто просмотр, ничего не меняется. Вверх — удалить, вниз — отложить."
    override val swipeRight = "Свайп вправо"
    override val swipeLeft = "Свайп влево"
    override val swipeUp = "Свайп вверх"
    override val swipeDown = "Свайп вниз"
    override val actionDelete = "Удалить"
    override val actionKeep = "Оставить (след. карточка)"
    override val actionMoveToFolder = "Переместить в папку"
    override val actionPostpone = "Отложить"
    override val actionNone = "Ничего не делать"
    override val actionPreviousPhoto = "Предыдущее фото"
    override val actionNextPhoto = "Следующее фото"
    override val moveToFolderDestination = "Папка для «Переместить в папку»"
    override val notSet = "Не задано"
    override val chooseAFolder = "Выберите папку"
    override val manageMediaTitle = "Перемещать без подтверждения"
    override val manageMediaHint = "Разрешите Cullect управлять медиафайлами в настройках системы, и перемещение фото больше не будет запрашивать подтверждение. Без этого перемещения подтверждаются сразу пачкой, одним окном."
    override val manageMediaOn = "Включено"
    override val manageMediaOff = "Выключено"
    override val newFolder = "Новая папка"
    override val newFolderName = "Название папки"
    override val newFolderInParent = "Создаётся внутри DCIM (туда же уйдут и фото, и видео)"
    override val newFolderInvalidName = "До 50 символов, без / \\ : * ? \" < > | и не с точки."
    override val createAction = "Создать"
    override val folderWillBeCreated = " (будет создана)"
    override val noFoldersFoundYet = "Папки пока не найдены."
    override val cancel = "Отмена"
    override val clearThumbnailCache = "Очистить кэш превью"
    override val enableSwipeLimit = "Включить лимит свайпов и монетизацию"
    override val unlockedLimitDisabled = "Разблокировано — лимит отключён"
    override val refreshLogs = "Обновить"
    override val shareLogs = "Поделиться"
    override val clearLogs = "Очистить"

    override val reviewTrash = "Просмотр корзины"
    override val folderIsClean = "Здесь больше ничего нет — папка чистая."
    override val undo = "Отменить"
    override val selectMoveFolderPrompt = "Сначала выберите папку назначения в Настройках"

    override fun trashTitle(count: Int) = "Корзина ($count)"
    override fun expiredTrashCount(count: Int) = "Просрочено: $count — нажмите «Очистить корзину», чтобы освободить место"
    override val trashIsEmpty = "Корзина пуста."
    override val restore = "Восстановить"
    override val restoreAll = "Восстановить всё"
    override val selectAll = "Выбрать всё"
    override val restoreSelected = "Восстановить выбранное"
    override val cancelSelection = "Отменить выбор"
    override fun selectedCount(count: Int) = "Выбрано: $count"
    override fun restoringProgress(done: Int, total: Int) = "Восстановление… $done из $total"
    override fun restoredFiles(count: Int): String {
        val word = when {
            count % 100 in 11..14 -> "файлов"
            count % 10 == 1 -> "файл"
            count % 10 in 2..4 -> "файла"
            else -> "файлов"
        }
        return "Восстановлено: $count $word"
    }

    override val allCleanedUp = "Всё убрано!"
    override fun itemsRemoved(count: Int) = "Удалено объектов: $count"
    override fun bytesFreed(formatted: String) = "Освобождено: $formatted"
    override val pendingTrashCleanup = "Файлы в корзине — место освободится после очистки."
    override val done = "Готово"

    override val freeSwipeLimitReached = "Достигнут лимит бесплатных свайпов"
    override val swipeLimitExplanation =
        "Посмотрите короткую рекламу, чтобы продолжить, или разблокируйте безлимитный свайп навсегда."
    override val watchAdToContinue = "Посмотреть рекламу"
    override val noAdAvailable = "Реклама сейчас недоступна."
    override val unlockForever = "Разблокировать навсегда"
    override val purchasesNotSetUp = "Покупки пока не настроены."
    override val backToFolders = "К папкам"
}

private val DATE_TAKEN_FORMATTER_EN: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm", Locale.ENGLISH)
private val DATE_TAKEN_FORMATTER_RU: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("ru"))

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { EnglishStrings }
