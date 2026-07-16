package com.ashcroft.ripple.core.world

import com.ashcroft.ripple.core.model.Facing
import com.ashcroft.ripple.core.model.Floor
import com.ashcroft.ripple.core.model.FloorId
import com.ashcroft.ripple.core.model.GridCell
import com.ashcroft.ripple.core.model.GridSize
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomInstance
import com.ashcroft.ripple.core.model.RoomKind
import com.ashcroft.ripple.core.model.RoomTemplate
import com.ashcroft.ripple.core.model.RoomTemplateId

/**
 * The Ashcroft — Established 1924.
 *
 * Phase 1 provides the physical structure of the vertical-slice hotel only:
 * the ground-floor public rooms and one guest-room corridor. There is no
 * simulated life here yet; this is the stage on which later phases run.
 *
 * The layout is fully deterministic so that the renderer and any tests observe
 * identical geometry on every run.
 */
object AshcroftLayout {
    const val HOTEL_NAME = "The Ashcroft"
    const val ESTABLISHED = 1924

    private fun template(
        id: String,
        size: GridSize,
    ): RoomTemplate {
        val cells = mutableSetOf<GridCell>()
        for (r in 0 until size.rows) {
            for (c in 0 until size.cols) cells.add(GridCell(c, r))
        }
        return RoomTemplate(
            id = RoomTemplateId(id),
            dimensions = size,
            walkableCells = cells,
            entryPoints =
                listOf(
                    com.ashcroft.ripple.core.model
                        .EntryPoint(GridCell(0, 0), Facing.SOUTH),
                ),
            interactionSlots = emptyList(),
            furnitureSlots = emptyList(),
            lightingSlots = emptyList(),
            occlusionRegions = emptyList(),
        )
    }

    private fun room(
        id: String,
        kind: RoomKind,
        name: String,
        floor: FloorId,
        origin: GridCell,
        size: GridSize,
    ) = RoomInstance(
        id = RoomId(id),
        templateId = template(kind.name.lowercase(), size).id,
        kind = kind,
        displayName = name,
        floorId = floor,
        origin = origin,
        size = size,
    )

    fun build(): HotelLayout {
        val ground = FloorId("ground")
        val first = FloorId("first")

        val groundRooms =
            listOf(
                room("lobby", RoomKind.LOBBY, "Grand Lobby", ground, GridCell(0, 2), GridSize(4, 3)),
                room("reception", RoomKind.RECEPTION, "Reception", ground, GridCell(4, 3), GridSize(2, 2)),
                room("bar", RoomKind.BAR, "The Ashcroft Bar", ground, GridCell(6, 2), GridSize(3, 2)),
                room("restaurant", RoomKind.RESTAURANT, "Restaurant", ground, GridCell(9, 2), GridSize(3, 3)),
                room("kitchen", RoomKind.KITCHEN, "Kitchen", ground, GridCell(9, 0), GridSize(3, 2)),
                room("staff_room", RoomKind.STAFF_ROOM, "Staff Break Room", ground, GridCell(0, 0), GridSize(2, 2)),
                room("housekeeping", RoomKind.HOUSEKEEPING_STORE, "Housekeeping Store", ground, GridCell(2, 0), GridSize(2, 2)),
                room("managers_office", RoomKind.MANAGER_OFFICE, "Manager's Office", ground, GridCell(6, 0), GridSize(3, 2)),
            )

        val corridor = room("corridor_1", RoomKind.CORRIDOR, "First-Floor Corridor", first, GridCell(0, 2), GridSize(12, 1))
        val guestRooms =
            listOf(
                room("room_101", RoomKind.GUEST_SINGLE, "Room 101", first, GridCell(0, 0), GridSize(2, 2)),
                room("room_102", RoomKind.GUEST_DOUBLE, "Room 102", first, GridCell(2, 0), GridSize(2, 2)),
                room("room_103", RoomKind.GUEST_TWIN, "Room 103", first, GridCell(4, 0), GridSize(2, 2)),
                room("room_104", RoomKind.GUEST_DOUBLE, "Room 104", first, GridCell(6, 0), GridSize(2, 2)),
                room("room_105", RoomKind.GUEST_SINGLE, "Room 105", first, GridCell(8, 0), GridSize(2, 2)),
                room("room_106", RoomKind.GUEST_DOUBLE, "Room 106", first, GridCell(10, 0), GridSize(2, 2)),
                room("suite_1", RoomKind.SUITE, "Ashcroft Junior Suite", first, GridCell(0, 3), GridSize(4, 2)),
            )

        return HotelLayout(
            name = HOTEL_NAME,
            establishedYear = ESTABLISHED,
            floors =
                listOf(
                    Floor(ground, level = 0, displayName = "Ground Floor", rooms = groundRooms),
                    Floor(first, level = 1, displayName = "First Floor", rooms = listOf(corridor) + guestRooms),
                ),
        )
    }
}
