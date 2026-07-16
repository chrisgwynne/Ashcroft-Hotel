package com.ashcroft.ripple.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Simple key/value metadata about a simulation (schema markers, counters). */
@Entity(tableName = "simulation_metadata")
data class SimulationMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)
