package com.ashcroft.ripple.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A persisted save slot. The simulation itself lives in memory during play;
 * this row records the durable header (seed, clock position, timestamps) that
 * a later phase's snapshot payload will attach to.
 */
@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val id: String,
    val name: String,
    val seed: Long,
    val simEpochMinutes: Long,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long,
)
