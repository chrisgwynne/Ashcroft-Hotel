package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/** Broad category of a space, used for rendering tint and (later) behaviour. */
@Serializable
enum class RoomKind {
    LOBBY,
    RECEPTION,
    BAR,
    RESTAURANT,
    KITCHEN,
    STAFF_ROOM,
    CORRIDOR,
    GUEST_SINGLE,
    GUEST_DOUBLE,
    GUEST_TWIN,
    SUITE,
    HOUSEKEEPING_STORE,
    MANAGER_OFFICE,
    STAIRWELL,
    LIFT,
}

/**
 * A concrete room placed on a floor. It references a [RoomTemplate] and adds
 * placement + presentation. Mutable simulation state (occupancy, cleanliness,
 * lighting) is deliberately absent in Phase 1.
 */
@Serializable
data class RoomInstance(
    val id: RoomId,
    val templateId: RoomTemplateId,
    val kind: RoomKind,
    val displayName: String,
    val floorId: FloorId,
    /** Origin (top-left) of the room on the floor grid. */
    val origin: GridCell,
    val size: GridSize,
) {
    val bounds: List<GridCell>
        get() {
            val dims = size
            val start = origin
            return buildList {
                for (r in 0 until dims.rows) {
                    for (c in 0 until dims.cols) {
                        add(GridCell(start.col + c, start.row + r))
                    }
                }
            }
        }
}

/** One physical floor of the hotel. */
@Serializable
data class Floor(
    val id: FloorId,
    val level: Int,
    val displayName: String,
    val rooms: List<RoomInstance>,
)

/**
 * The full physical description of a hotel: an ordered set of floors with the
 * rooms placed on each. This is static structure — the living simulation is
 * layered on top in later phases.
 */
@Serializable
data class HotelLayout(
    val name: String,
    val establishedYear: Int,
    val floors: List<Floor>,
) {
    val allRooms: List<RoomInstance> get() = floors.flatMap { it.rooms }

    fun floor(level: Int): Floor? = floors.firstOrNull { it.level == level }

    fun room(id: RoomId): RoomInstance? = allRooms.firstOrNull { it.id == id }
}
