package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * A cell on the hotel's logical grid. Rooms occupy rectangular blocks of
 * cells; renderers project these into isometric screen space.
 */
@Serializable
data class GridCell(
    val col: Int,
    val row: Int,
)

/** The size, in grid cells, of a room or region. */
@Serializable
data class GridSize(
    val cols: Int,
    val rows: Int,
) {
    init {
        require(cols > 0 && rows > 0) { "GridSize must be positive: ${cols}x$rows" }
    }

    val area: Int get() = cols * rows
}

/** Where a person may enter/leave a room, expressed as a grid cell + facing. */
@Serializable
data class EntryPoint(
    val cell: GridCell,
    val facing: Facing,
)

@Serializable
enum class Facing { NORTH, EAST, SOUTH, WEST }

/**
 * A slot a person can stand in to use an object (e.g. the side of a bed, a
 * bar stool). Kept abstract in Phase 1; populated with behaviour later.
 */
@Serializable
data class InteractionSlot(
    val cell: GridCell,
    val kind: String,
)

/** A place a piece of furniture can be positioned within a room template. */
@Serializable
data class FurnitureSlot(
    val cell: GridCell,
    val kind: String,
)

/** A place a light source can exist within a room template. */
@Serializable
data class LightingSlot(
    val cell: GridCell,
    val kind: LightKind,
)

@Serializable
enum class LightKind { MAIN, BEDSIDE, DESK_LAMP, BATHROOM, WINDOW, TELEVISION, CORRIDOR_SPILL }

/** A region used by the renderer to occlude furniture behind walls. */
@Serializable
data class OcclusionRegion(
    val cells: Set<GridCell>,
)

/**
 * A reusable room blueprint. Concrete rooms are instances of a template that
 * differ by furniture, finish, condition and occupancy — never by being a
 * separate baked image. See [RoomInstance].
 */
@Serializable
data class RoomTemplate(
    val id: RoomTemplateId,
    val dimensions: GridSize,
    val walkableCells: Set<GridCell>,
    val entryPoints: List<EntryPoint>,
    val interactionSlots: List<InteractionSlot>,
    val furnitureSlots: List<FurnitureSlot>,
    val lightingSlots: List<LightingSlot>,
    val occlusionRegions: List<OcclusionRegion>,
)
