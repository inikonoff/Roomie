package com.cullect.app.util

/** Parent of every folder the user creates for "Move to folder". DCIM is the one top-level
 *  directory MediaStore accepts for both photos and videos, so a single path serves both. */
const val NEW_FOLDER_PARENT = "DCIM"

private const val MAX_FOLDER_NAME_LENGTH = 50
private val FORBIDDEN_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

/** The trimmed name if it can be a folder name, null if not (empty, too long, forbidden
 *  characters, or something that would be hidden or climb out of the parent like ".." or ".x"). */
fun validFolderNameOrNull(raw: String): String? {
    val name = raw.trim()
    if (name.isEmpty() || name.length > MAX_FOLDER_NAME_LENGTH) return null
    if (name.any { it in FORBIDDEN_CHARS || it.isISOControl() }) return null
    if (name.startsWith(".")) return null
    return name
}

/** MediaStore `RELATIVE_PATH` for a new folder: always ends with a slash, as MediaStore stores it. */
fun relativePathForNewFolder(name: String): String = "$NEW_FOLDER_PARENT/$name/"
