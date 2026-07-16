package com.ashcroft.ripple.core.world

import com.ashcroft.ripple.core.model.GridCell
import com.ashcroft.ripple.core.model.WorldPos

/**
 * Vertical connections through The Ashcroft. Phase 1 geometry has no explicit
 * stairwell cell, so the main staircase is modelled as a portal linking the
 * ground-floor lobby to the first-floor corridor. Portals are bidirectional.
 */
object AshcroftNav {
    fun stairPortals(): List<Pair<WorldPos, WorldPos>> = listOf(
        WorldPos(0, GridCell(0, 2)) to WorldPos(1, GridCell(0, 2)),
    )
}
