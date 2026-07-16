package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomKind
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseThreeLifecycleTest {
    private val layout = AshcroftLayout.build()
    private val engine = SimulationEngine(layout)
    private val staffOnly = setOf(RoomKind.STAFF_ROOM, RoomKind.KITCHEN, RoomKind.MANAGER_OFFICE, RoomKind.HOUSEKEEPING_STORE)

    @Test
    fun actionsMoveThroughTheirLifecyclePhases() {
        var state = AshcroftScenario.initial()
        val seen = mutableSetOf<ActionPhase>()
        repeat(600) {
            state = engine.step(state)
            state.people.forEach { seen += it.action.phase }
        }
        assertTrue("people should end up performing actions", seen.contains(ActionPhase.IN_PROGRESS))
        assertTrue("some actions require travel", seen.contains(ActionPhase.TRAVELLING))
        assertTrue("actions complete", seen.contains(ActionPhase.COMPLETED))
    }

    @Test
    fun everyoneEventuallyHasAReasonedDecisionOnRecord() {
        val state = engine.run(AshcroftScenario.initial(), 120)
        assertTrue("each person should have made at least one recorded decision", state.people.all { it.lastDecision != null })
        assertTrue("decisions weigh several candidates", state.people.all { (it.lastDecision?.consideredActions?.size ?: 0) >= 2 })
    }

    @Test
    fun sharedStartFormsMemoriesAndAcquaintances() {
        // The staff begin together in the break room, so social actions occur and are remembered.
        val state = engine.run(AshcroftScenario.initial(), 240)
        val staff = state.people.filter { it.role.isStaff }
        assertTrue("shared space should produce some remembered interactions", staff.any { it.memories.isNotEmpty() })
        assertTrue("and some acquaintances", staff.any { it.acquaintances.isNotEmpty() })
    }

    @Test
    fun guestsNeverEndUpInStaffOnlyRooms() {
        var state = AshcroftScenario.initial()
        repeat(1_440) {
            state = engine.step(state)
            for (person in state.people) {
                if (person.role == RoleKind.GUEST) {
                    val kind = person.location.roomId?.let { layout.room(it)?.kind }
                    assertFalse("${person.name} should not be in a staff-only room ($kind)", kind in staffOnly)
                }
            }
        }
    }

    @Test
    fun plannedActionsHaveSensibleDurations() {
        val state = engine.run(AshcroftScenario.initial(), 300)
        for (person in state.people) {
            if (!person.action.phase.isTerminal) {
                assertTrue("${person.name}'s action should last a sensible time", person.action.plannedMinutes in 1..600)
            }
        }
    }

    @Test
    fun memoriesStayBounded() {
        val state = engine.run(AshcroftScenario.initial(), 3_000)
        assertTrue(state.people.all { it.memories.size <= 40 })
    }

    @Test
    fun replayFromSameSeedStaysIdentical() {
        val start = AshcroftScenario.initial()
        assertEquals(engine.run(start, 720), engine.run(start, 720))
    }
}
