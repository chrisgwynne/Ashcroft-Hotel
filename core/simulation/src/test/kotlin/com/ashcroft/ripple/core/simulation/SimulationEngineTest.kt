package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.WorldPos
import com.ashcroft.ripple.core.world.AshcroftLayout
import com.ashcroft.ripple.core.world.AshcroftNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SimulationEngineTest {
    private val layout = AshcroftLayout.build()
    private val engine = SimulationEngine(layout)
    private val graph = NavGraph.from(layout, AshcroftNav.stairPortals())

    @Test
    fun replayFromSameSeedIsIdentical() {
        val start = AshcroftScenario.initial()
        val a = engine.run(start, 720)
        val b = engine.run(start, 720)
        assertEquals(a, b)
    }

    @Test
    fun clockAdvancesOneMinutePerStepAndNeverGoesBackward() {
        var state = AshcroftScenario.initial()
        var previous = state.clock.epochMinutes
        repeat(500) {
            state = engine.step(state)
            assertTrue(state.clock.epochMinutes > previous)
            assertEquals(previous + 1, state.clock.epochMinutes)
            previous = state.clock.epochMinutes
        }
    }

    @Test
    fun peopleNeverTeleport() {
        var state = AshcroftScenario.initial()
        repeat(600) {
            val before = state.people.associate { it.id to it.location.pos }
            state = engine.step(state)
            for (person in state.people) {
                val from = before.getValue(person.id)
                val to = person.location.pos
                val step = displacement(from, to)
                assertTrue("${person.name} jumped from $from to $to", step)
            }
        }
    }

    @Test
    fun locationsStayConsistentAndNeedsStayBounded() {
        var state = AshcroftScenario.initial()
        repeat(1_440) { state = engine.step(state) }
        for (person in state.people) {
            assertTrue("${person.name} off-grid at ${person.location.pos}", graph.isWalkable(person.location.pos))
            assertEquals(graph.roomOf(person.location.pos), person.location.roomId)
            for (need in com.ashcroft.ripple.core.model.NeedKind.entries) {
                val v = person.needs[need]
                assertTrue("$need out of range: $v", v in 0f..1f)
            }
        }
    }

    @Test
    fun peopleActuallyMoveAndSpreadOut() {
        val start = AshcroftScenario.initial()
        val startRooms = start.people.associate { it.id to it.location.roomId }
        val end = engine.run(start, 480) // eight hours
        val moved = end.people.count { it.location.roomId != startRooms[it.id] }
        assertTrue("most of the cast should have gone somewhere over 8h (moved=$moved)", moved >= end.people.size / 2)
        // Occupancy should be spread across several rooms, not all clumped in one.
        val distinctRooms = end.people.mapNotNull { it.location.roomId }.toSet()
        assertTrue("expected activity across multiple rooms (was $distinctRooms)", distinctRooms.size >= 3)
    }

    @Test
    fun soakOneWeekDoesNotBreakInvariants() {
        var state = AshcroftScenario.initial()
        repeat(7 * 24 * 60) { state = engine.step(state) }
        assertEquals((6 + 7 * 24) * 60L, state.clock.epochMinutes)
        assertEquals(12, state.people.size)
        // Occupancy never double-counts a person.
        val occupants = state.occupancy().values.flatten()
        assertEquals(occupants.size, occupants.toSet().size)
    }

    private fun displacement(from: WorldPos, to: WorldPos): Boolean {
        if (from == to) return true
        if (from.floor != to.floor) return true // portal transition
        return abs(from.cell.col - to.cell.col) + abs(from.cell.row - to.cell.row) <= 1
    }
}
