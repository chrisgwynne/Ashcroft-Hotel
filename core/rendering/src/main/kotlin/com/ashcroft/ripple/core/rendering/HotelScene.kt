package com.ashcroft.ripple.core.rendering

import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind

/** A renderable room: pure geometry + presentation, no Android types. */
data class SceneRoom(
    val id: RoomId,
    val label: String,
    val kind: RoomKind,
    val level: Int,
    val originCol: Int,
    val originRow: Int,
    val cols: Int,
    val rows: Int,
)

/**
 * A flattened, render-ready view of a [HotelLayout]. Building a scene is a
 * pure transformation, keeping the renderer independent of the domain model's
 * shape and trivial to unit test.
 */
data class HotelScene(
    val rooms: List<SceneRoom>,
) {
    fun onLevel(level: Int): List<SceneRoom> = rooms.filter { it.level == level }

    val levels: List<Int> get() = rooms.map { it.level }.distinct().sorted()

    companion object {
        fun from(layout: HotelLayout): HotelScene {
            val rooms =
                layout.floors.flatMap { floor ->
                    floor.rooms.map { r ->
                        SceneRoom(
                            id = r.id,
                            label = r.displayName,
                            kind = r.kind,
                            level = floor.level,
                            originCol = r.origin.col,
                            originRow = r.origin.row,
                            cols = r.size.cols,
                            rows = r.size.rows,
                        )
                    }
                }
            return HotelScene(rooms)
        }
    }
}

/**
 * Maps a screen tap to the room beneath it. It inverts the camera and
 * projection for the focused floor, then finds the room whose grid footprint
 * contains the resulting cell. The topmost (last drawn) match wins.
 */
class HitTester(
    private val projection: IsoProjection,
) {
    fun roomAt(
        screen: Vec2,
        camera: Camera,
        rooms: List<SceneRoom>,
        level: Int,
    ): RoomId? {
        val world = camera.screenToWorld(screen)
        val (col, row) = projection.worldToGrid(world, level)
        // Iterate in reverse so rooms drawn later (visually on top) win ties.
        for (room in rooms.asReversed()) {
            if (room.level != level) continue
            val withinCol = col >= room.originCol && col < room.originCol + room.cols
            val withinRow = row >= room.originRow && row < room.originRow + room.rows
            if (withinCol && withinRow) return room.id
        }
        return null
    }
}
