package com.ashcroft.ripple.core.database.save

import com.ashcroft.ripple.core.database.entity.SavedGameEntity
import com.ashcroft.ripple.core.model.SimTime

/** Domain representation of a save slot, decoupled from the Room entity. */
data class SavedGame(
    val id: String,
    val name: String,
    val seed: Long,
    val simTime: SimTime,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long,
)

fun SavedGameEntity.toDomain(): SavedGame =
    SavedGame(
        id = id,
        name = name,
        seed = seed,
        simTime = SimTime(simEpochMinutes),
        createdAtEpoch = createdAtEpoch,
        updatedAtEpoch = updatedAtEpoch,
    )

fun SavedGame.toEntity(): SavedGameEntity =
    SavedGameEntity(
        id = id,
        name = name,
        seed = seed,
        simEpochMinutes = simTime.epochMinutes,
        createdAtEpoch = createdAtEpoch,
        updatedAtEpoch = updatedAtEpoch,
    )
