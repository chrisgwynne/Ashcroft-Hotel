package com.ashcroft.ripple.core.database.save

import com.ashcroft.ripple.core.database.entity.WorldSnapshotEntity
import com.ashcroft.ripple.core.model.SimTime

/**
 * Domain representation of a saved world snapshot, decoupled from the Room entity.
 * [payload] is the serialised [com.ashcroft.ripple.core.simulation.WorldState]
 * produced by the simulation layer's store; this module only carries it.
 */
data class WorldSnapshot(
    val saveId: String,
    val simTime: SimTime,
    val causeNodeCount: Int,
    val payload: String,
)

fun WorldSnapshotEntity.toDomain(): WorldSnapshot =
    WorldSnapshot(
        saveId = saveId,
        simTime = SimTime(simEpochMinutes),
        causeNodeCount = causeNodeCount,
        payload = payload,
    )

fun WorldSnapshot.toEntity(): WorldSnapshotEntity =
    WorldSnapshotEntity(
        saveId = saveId,
        simEpochMinutes = simTime.epochMinutes,
        causeNodeCount = causeNodeCount,
        payload = payload,
    )
