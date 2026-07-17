package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.WorldPos
import com.ashcroft.ripple.core.world.AshcroftLayout
import com.ashcroft.ripple.core.world.AshcroftNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PathfinderTest {
    private val layout = AshcroftLayout.build()
    private val graph = NavGraph.from(layout, AshcroftNav.stairPortals())

    private fun access(room: String): WorldPos = graph.accessPos(RoomId(room))!!

    @Test
    fun pathStepsAreAlwaysAdjacentAndEndAtGoal() {
        val start = access("kitchen")
        val goal = access("bar")
        val path = Pathfinder.findPath(graph, start, goal)
        assertTrue("expected a route", path.isNotEmpty())
        assertEquals(goal, path.last())

        var prev = start
        for (step in path) {
            assertTrue("step $step not a neighbour of $prev", graph.neighbours(prev).contains(step))
            prev = step
        }
    }

    @Test
    fun pathfindingIsDeterministic() {
        val a = Pathfinder.findPath(graph, access("staff_room"), access("restaurant"))
        val b = Pathfinder.findPath(graph, access("staff_room"), access("restaurant"))
        assertEquals(a, b)
    }

    @Test
    fun crossFloorRouteUsesTheStairPortal() {
        val path = Pathfinder.findPath(graph, access("lobby"), access("room_106"))
        assertTrue("guest room on floor 1 must be reachable from the lobby", path.isNotEmpty())
        assertTrue("route must cross to floor 1", path.any { it.floor == 1 })
        // Exactly one floor transition happens via the portal (no teleporting).
        var transitions = 0
        var prev = access("lobby")
        for (step in path) {
            if (step.floor != prev.floor) {
                transitions++
            } else {
                assertTrue(abs(step.cell.col - prev.cell.col) + abs(step.cell.row - prev.cell.row) == 1)
            }
            prev = step
        }
        assertEquals(1, transitions)
    }

    @Test
    fun sameStartAndGoalYieldsEmptyPath() {
        assertEquals(emptyList<WorldPos>(), Pathfinder.findPath(graph, access("bar"), access("bar")))
    }
}
