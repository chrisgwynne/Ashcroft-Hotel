package com.ashcroft.ripple.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.ashcroft.ripple.core.database.dao.SavedGameDao
import com.ashcroft.ripple.core.database.entity.SavedGameEntity
import com.ashcroft.ripple.core.database.entity.SimulationMetadataEntity

@Database(
    entities = [SavedGameEntity::class, SimulationMetadataEntity::class],
    version = RippleDatabase.VERSION,
    exportSchema = true,
)
abstract class RippleDatabase : RoomDatabase() {
    abstract fun savedGameDao(): SavedGameDao

    companion object {
        const val VERSION = 1
        const val NAME = "ripple.db"

        /**
         * Ordered schema migrations, applied from version 1 upward. Empty in
         * Phase 1 (there is nothing before v1). Destructive migration is
         * intentionally never enabled — future schema changes must add a
         * [Migration] here so saved games survive upgrades.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
