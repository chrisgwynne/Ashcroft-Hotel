package com.ashcroft.ripple.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ashcroft.ripple.core.database.dao.SavedGameDao
import com.ashcroft.ripple.core.database.dao.WorldSnapshotDao
import com.ashcroft.ripple.core.database.entity.SavedGameEntity
import com.ashcroft.ripple.core.database.entity.SimulationMetadataEntity
import com.ashcroft.ripple.core.database.entity.WorldSnapshotEntity

@Database(
    entities = [SavedGameEntity::class, SimulationMetadataEntity::class, WorldSnapshotEntity::class],
    version = RippleDatabase.VERSION,
    exportSchema = true,
)
abstract class RippleDatabase : RoomDatabase() {
    abstract fun savedGameDao(): SavedGameDao

    abstract fun worldSnapshotDao(): WorldSnapshotDao

    companion object {
        const val VERSION = 2
        const val NAME = "ripple.db"

        /**
         * v1 → v2 adds the world_snapshots table that carries a save slot's full,
         * serialised [WorldState] payload (people, chronicle, tasks and the whole
         * causal record). Only additive: no existing row is touched, so saved games
         * survive the upgrade untouched.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `world_snapshots` (" +
                        "`saveId` TEXT NOT NULL, " +
                        "`simEpochMinutes` INTEGER NOT NULL, " +
                        "`causeNodeCount` INTEGER NOT NULL, " +
                        "`payload` TEXT NOT NULL, " +
                        "PRIMARY KEY(`saveId`))",
                )
            }
        }

        /**
         * Ordered schema migrations, applied from version 1 upward. Destructive
         * migration is intentionally never enabled — every schema change adds a
         * [Migration] here so saved games survive upgrades.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
