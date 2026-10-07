package com.cullect.app.data.media

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.pendingMoveDataStore by preferencesDataStore(name = "cullect_pending_moves")

/** One file waiting to be moved to [targetPath] once the user confirms (see [PendingMoveStore]). */
data class PendingMove(val stableId: String, val uri: String, val targetPath: String)

/**
 * Moves the user has swiped but Android hasn't been asked to carry out yet. Without the
 * "manage media" permission every write to someone else's file needs a system confirmation, and one
 * dialog per swipe is unbearable — so swiped moves are queued here and confirmed together in one
 * dialog (when the stack ends, the user leaves it, or enough have piled up).
 *
 * Kept on disk so a queue survives the app being killed before it was confirmed. Queued files are
 * hidden from browsing (see [com.cullect.app.data.trash.TrashRepository.getHiddenStableIds]) until
 * they've actually moved or the move was declined.
 */
class PendingMoveStore(private val context: Context) {

    private val key = stringSetPreferencesKey("pending")

    suspend fun getAll(): List<PendingMove> =
        context.pendingMoveDataStore.data.first()[key].orEmpty().mapNotNull(::decode)

    suspend fun getStableIds(): Set<String> = getAll().mapTo(HashSet()) { it.stableId }

    suspend fun add(moves: List<PendingMove>) {
        if (moves.isEmpty()) return
        val ids = moves.mapTo(HashSet()) { it.stableId }
        context.pendingMoveDataStore.edit { prefs ->
            // A file queued again (e.g. a different destination) replaces its earlier entry.
            val kept = prefs[key].orEmpty().filterNot { entry -> decode(entry)?.stableId in ids }
            prefs[key] = kept.toSet() + moves.map(::encode)
        }
    }

    suspend fun remove(stableIds: Set<String>) {
        if (stableIds.isEmpty()) return
        context.pendingMoveDataStore.edit { prefs ->
            prefs[key] = prefs[key].orEmpty().filterNot { entry -> decode(entry)?.stableId in stableIds }.toSet()
        }
    }

    private fun encode(move: PendingMove) = listOf(move.stableId, move.uri, move.targetPath).joinToString(SEP)

    private fun decode(entry: String): PendingMove? =
        entry.split(SEP).takeIf { it.size == 3 }?.let { PendingMove(it[0], it[1], it[2]) }

    private companion object {
        /** ASCII "unit separator": never appears in a URI, a stable id or a folder path. */
        const val SEP = "\u001F"
    }
}
