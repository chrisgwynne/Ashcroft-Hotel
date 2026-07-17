package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.GridCell
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.WorldPos

/**
 * A walkable graph over the hotel: every cell covered by a room is a node,
 * cells adjacent on the same floor are connected, and [portals] join floors
 * (stairs/lifts). Construction is a pure function of the [HotelLayout] so the
 * graph is deterministic and unit-testable without any Android or rendering.
 */
class NavGraph(
    private val walkable: Set<WorldPos>,
    private val portals: Map<WorldPos, List<WorldPos>>,
    private val roomAt: Map<WorldPos, RoomId>,
    private val roomAccess: Map<RoomId, WorldPos>,
) {
    fun isWalkable(pos: WorldPos): Boolean = pos in walkable

    fun roomOf(pos: WorldPos): RoomId? = roomAt[pos]

    /** A stable cell to stand at when using [roomId] (its centre). */
    fun accessPos(roomId: RoomId): WorldPos? = roomAccess[roomId]

    /** Neighbours in a fixed order so pathfinding stays deterministic. */
    fun neighbours(pos: WorldPos): List<WorldPos> {
        val result = ArrayList<WorldPos>(6)
        // Same-floor 4-neighbourhood, in a fixed N,E,S,W order.
        val steps = arrayOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
        for ((dc, dr) in steps) {
            val n = WorldPos(pos.floor, GridCell(pos.cell.col + dc, pos.cell.row + dr))
            if (n in walkable) result.add(n)
        }
        portals[pos]?.let { result.addAll(it) }
        return result
    }

    companion object {
        fun from(
            layout: HotelLayout,
            stairPortals: List<Pair<WorldPos, WorldPos>> = emptyList(),
        ): NavGraph {
            val walkable = LinkedHashSet<WorldPos>()
            val roomAt = LinkedHashMap<WorldPos, RoomId>()
            val roomAccess = LinkedHashMap<RoomId, WorldPos>()

            for (floor in layout.floors) {
                for (room in floor.rooms) {
                    for (cell in room.bounds) {
                        val pos = WorldPos(floor.level, cell)
                        walkable.add(pos)
                        // First room to claim a cell owns it (rooms do not overlap).
                        roomAt.putIfAbsent(pos, room.id)
                    }
                    val centre = GridCell(
                        room.origin.col + room.size.cols / 2,
                        room.origin.row + room.size.rows / 2,
                    )
                    roomAccess[room.id] = WorldPos(floor.level, centre)
                }
            }

            val portals = LinkedHashMap<WorldPos, MutableList<WorldPos>>()
            for ((a, b) in stairPortals) {
                if (a in walkable && b in walkable) {
                    portals.getOrPut(a) { mutableListOf() }.add(b)
                    portals.getOrPut(b) { mutableListOf() }.add(a)
                }
            }

            return NavGraph(walkable, portals, roomAt, roomAccess)
        }
    }
}
