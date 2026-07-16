package com.ashcroft.ripple.core.world

import com.ashcroft.ripple.core.model.RoomKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AshcroftLayoutTest {
    private val layout = AshcroftLayout.build()

    @Test
    fun hotelIdentityIsTheAshcroft() {
        assertEquals("The Ashcroft", layout.name)
        assertEquals(1924, layout.establishedYear)
    }

    @Test
    fun hasTwoFloorsAndTheVerticalSliceRooms() {
        assertEquals(2, layout.floors.size)
        // 8 ground rooms + corridor + 6 guest rooms + 1 suite = 16
        assertEquals(16, layout.allRooms.size)
    }

    @Test
    fun buildIsDeterministic() {
        val a = AshcroftLayout.build()
        val b = AshcroftLayout.build()
        assertEquals(a, b)
    }

    @Test
    fun containsExpectedPublicRooms() {
        val kinds = layout.allRooms.map { it.kind }.toSet()
        assertTrue(kinds.containsAll(setOf(RoomKind.LOBBY, RoomKind.RECEPTION, RoomKind.BAR, RoomKind.KITCHEN)))
        assertNotNull(
            layout.room(
                com.ashcroft.ripple.core.model
                    .RoomId("suite_1"),
            ),
        )
    }

    @Test
    fun roomsOnAFloorDoNotOverlap() {
        for (floor in layout.floors) {
            val occupied = mutableSetOf<com.ashcroft.ripple.core.model.GridCell>()
            for (room in floor.rooms) {
                for (cell in room.bounds) {
                    assertTrue("Overlap at $cell on ${floor.displayName}", occupied.add(cell))
                }
            }
        }
    }
}
