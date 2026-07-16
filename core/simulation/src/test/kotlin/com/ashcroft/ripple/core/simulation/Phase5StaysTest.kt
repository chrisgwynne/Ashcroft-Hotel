package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.HotelTaskType
import com.ashcroft.ripple.core.model.RoleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5StaysTest {
    private val engine = SimulationEngine(com.ashcroft.ripple.core.world.AshcroftLayout.build())

    private fun guest(id: String) = AshcroftScenario.initial().people.first { it.id.value == id }

    @Test
    fun guestsCarryAPurposeAndExpectationsButStaffDoNot() {
        val guests = AshcroftScenario.initial().people.filter { it.role == RoleKind.GUEST }
        assertTrue("every guest has a stay", guests.all { it.stay != null })
        assertTrue("with expectations to be judged against", guests.all { it.stay!!.expectations.isNotEmpty() })
        assertNull("staff are not guests", AshcroftScenario.initial().people.first { it.role == RoleKind.RECEPTIONIST }.stay)
    }

    private fun appointmentsOf(id: String) =
        guest(id).schedule.filter { it.kind == CommitmentKind.APPOINTMENT }.map { it.location?.value to it.startMinuteOfDay }

    @Test
    fun differentPurposesGiveDifferentDailyRoutines() {
        val business = appointmentsOf("ethan")
        val family = appointmentsOf("paul")
        assertNotEquals("a business trip and a family visit should not share a routine", business, family)
        assertTrue("the business guest keeps a morning appointment", business.any { it.second == 9 * 60 })
        assertTrue("the family visitor has a midday one", family.any { it.second == 13 * 60 })
    }

    @Test
    fun guestsWithDifferentRoutinesVisitDifferentPlaces() {
        var state = AshcroftScenario.initial()
        val visited = HashMap<String, MutableSet<String>>()
        repeat(20 * 60) {
            state = engine.step(state)
            for (p in state.people.filter { it.role == RoleKind.GUEST }) {
                p.location.roomId?.let { visited.getOrPut(p.id.value) { mutableSetOf() }.add(it.value) }
            }
        }
        // The holidaymaker and the interview guest should not trace identical paths.
        assertNotEquals(visited["naomi"], visited["sophie"])
    }

    @Test
    fun overlappingShiftsGenerateHandoverTasks() {
        // Reception is staffed 07-15 and 14-22, so 14:00-15:00 is a handover window.
        var state = AshcroftScenario.initial()
        var sawHandover = false
        repeat(20 * 60) {
            state = engine.step(state)
            if (state.tasks.any { it.type == HotelTaskType.SHIFT_HANDOVER }) sawHandover = true
        }
        assertTrue("an overlapping shift should offer a handover", sawHandover)
    }

    @Test
    fun beingServedLiftsGuestSatisfaction() {
        // Over a day of operations, at least one guest should be served often enough
        // to sit above the neglected baseline their satisfaction otherwise ebbs toward.
        val state = engine.run(AshcroftScenario.initial(), 24 * 60)
        val best = state.people.mapNotNull { it.stay?.satisfaction }.maxOrNull() ?: 0.0
        assertTrue("attentive service should leave a guest content ($best)", best > 0.5)
    }

    @Test
    fun scenarioWithStaysIsDeterministic() {
        val start = AshcroftScenario.initial()
        assertEquals(engine.run(start, 600), engine.run(start, 600))
    }
}
