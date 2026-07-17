package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * An absolute position in the hotel: a grid cell on a specific floor. This is
 * the atom of movement — people occupy a [WorldPos] and travel between them.
 */
@Serializable
data class WorldPos(val floor: Int, val cell: GridCell)

/**
 * A person's current whereabouts and any in-progress movement. [path] is the
 * remaining sequence of positions to walk (excluding the current one); an empty
 * path means the person is standing still.
 */
@Serializable
data class LocationState(
    val pos: WorldPos,
    val roomId: RoomId?,
    val path: List<WorldPos> = emptyList(),
) {
    val isMoving: Boolean get() = path.isNotEmpty()
}
