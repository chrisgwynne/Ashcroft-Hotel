package com.ashcroft.ripple.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    @Test
    fun gridSize_area_isProductOfDimensions() {
        assertEquals(12, GridSize(3, 4).area)
    }

    @Test(expected = IllegalArgumentException::class)
    fun gridSize_rejectsNonPositiveDimensions() {
        GridSize(0, 4)
    }

    @Test
    fun roomInstance_boundsCoverEveryCell() {
        val room =
            RoomInstance(
                id = RoomId("r1"),
                templateId = RoomTemplateId("t1"),
                kind = RoomKind.GUEST_DOUBLE,
                displayName = "Room 101",
                floorId = FloorId("f1"),
                origin = GridCell(2, 3),
                size = GridSize(2, 2),
            )
        val bounds = room.bounds
        assertEquals(4, bounds.size)
        assertTrue(bounds.contains(GridCell(2, 3)))
        assertTrue(bounds.contains(GridCell(3, 4)))
    }
}
