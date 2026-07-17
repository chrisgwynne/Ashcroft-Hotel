package com.ashcroft.ripple.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The durable payload behind a save slot: a full, serialised [WorldState] snapshot
 * — people, chronicle, tasks and the whole causal record — keyed by the save it
 * belongs to. Because the engine is a pure, seeded function of the state, restoring
 * this payload restores the exact world, and stepping it reproduces the exact
 * future. [causeNodeCount] is a cheap header for listing without decoding the
 * payload. The payload itself is produced and consumed by the simulation layer;
 * the database stores it opaquely.
 */
@Entity(tableName = "world_snapshots")
data class WorldSnapshotEntity(
    @PrimaryKey val saveId: String,
    val simEpochMinutes: Long,
    val causeNodeCount: Int,
    val payload: String,
)
