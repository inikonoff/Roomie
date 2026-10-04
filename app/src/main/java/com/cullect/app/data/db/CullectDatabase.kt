package com.cullect.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Schema is intentionally minimal for the MVP (trash only). Duplicate-detection hashes and
 * streak/stats tables are backlog items (see project TZ section 6) — add them as new entities with
 * a Room migration when that work starts; nothing here needs to change to allow it.
 */
@Database(
    entities = [TrashEntry::class],
    version = 2,
    exportSchema = false,
)
abstract class CullectDatabase : RoomDatabase() {

    abstract fun trashDao(): TrashDao

    companion object {
        @Volatile
        private var instance: CullectDatabase? = null

        fun getInstance(context: Context): CullectDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CullectDatabase::class.java,
                    "cullect.db",
                )
                    // Version 1 -> 2 dropped the favorites table; the app has no released users
                    // yet, so recreating rather than writing a migration is the simpler trade.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
