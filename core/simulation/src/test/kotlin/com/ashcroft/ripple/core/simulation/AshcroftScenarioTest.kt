package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AshcroftScenarioTest {
    private val layout = AshcroftLayout.build()
    private val graph = NavGraph.from(layout)

    @Test
    fun castIsSeededDeterministically() {
        assertEquals(AshcroftScenario.initial(), AshcroftScenario.initial())
    }

    @Test
    fun everyoneStartsOnAValidWalkableCellInTheirRoom() {
        for (person in AshcroftScenario.initial().people) {
            assertTrue("${person.name} starts off-grid", graph.isWalkable(person.location.pos))
            assertEquals(person.location.roomId, graph.roomOf(person.location.pos))
        }
    }

    @Test
    fun castHasStaffResidentsAndGuests() {
        val roles = AshcroftScenario.initial().people.map { it.role }
        assertTrue(roles.any { it.isStaff })
        assertTrue(roles.contains(RoleKind.GUEST))
        assertTrue(roles.contains(RoleKind.RESIDENT))
        assertEquals(12, roles.size)
    }

    @Test
    fun personIdsAreUnique() {
        val ids = AshcroftScenario.initial().people.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
