package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Activity
import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.world.AshcroftNav

/**
 * The deterministic heart of the simulation. [step] advances the world by one
 * simulated minute as a pure function: identical input state (and the seed it
 * carries) always yields identical output, which makes replay and regression
 * testing exact. There is no wall-clock time, no shared mutable state and no
 * dependency on rendering.
 *
 * Each minute, for every person: needs drift (relieved only while actually
 * performing an activity at its destination), one step of movement is taken
 * along any active path, and — when a person becomes free — the
 * [ActivityChooser] selects what to do next and a route is planned to it.
 */
class SimulationEngine(
    layout: HotelLayout,
    private val graph: NavGraph = NavGraph.from(layout, AshcroftNav.stairPortals()),
    private val locator: RoomLocator = RoomLocator.from(layout),
) {
    private val chooser = ActivityChooser(locator)

    fun step(state: WorldState): WorldState {
        val now = state.clock + 1
        val people = state.people.map { advance(it, state.seed, now) }
        return state.copy(clock = now, people = people)
    }

    /** Run [minutes] ticks. Convenience for catch-up and soak testing. */
    fun run(state: WorldState, minutes: Int): WorldState {
        var current = state
        repeat(minutes) { current = step(current) }
        return current
    }

    private fun advance(person: Person, seed: Long, now: com.ashcroft.ripple.core.model.SimTime): Person {
        val atTarget = person.location.roomId == person.currentActivity.targetRoom
        val performing = person.currentActivity.kind != ActivityKind.IDLE &&
            !person.location.isMoving &&
            atTarget

        val effectiveKind = when {
            performing -> person.currentActivity.kind
            person.location.isMoving -> ActivityKind.TRAVEL
            else -> ActivityKind.IDLE
        }
        val needs = NeedDynamics.tick(person.needs, effectiveKind)

        // 1) Movement: take one step along the planned path.
        if (person.location.isMoving) {
            val next = person.location.path.first()
            val moved = person.location.copy(
                pos = next,
                roomId = graph.roomOf(next),
                path = person.location.path.drop(1),
            )
            return person.copy(needs = needs, location = moved)
        }

        // 2) Performing at destination: accrue time, finish into IDLE.
        if (performing) {
            val progressed = person.currentActivity.copy(elapsedMinutes = person.currentActivity.elapsedMinutes + 1)
            return if (progressed.isComplete()) {
                person.copy(needs = needs, currentActivity = Activity.IDLE)
            } else {
                person.copy(needs = needs, currentActivity = progressed)
            }
        }

        // 3) Free (idle, or holding an activity but not yet at its room): decide and route.
        return decide(person.copy(needs = needs), seed, now)
    }

    private fun decide(person: Person, seed: Long, now: com.ashcroft.ripple.core.model.SimTime): Person {
        val activity = if (person.currentActivity.kind == ActivityKind.IDLE) {
            chooser.choose(person, now, seed)
        } else {
            person.currentActivity
        }

        val target = activity.targetRoom
        if (target == null || person.location.roomId == target) {
            // Nothing to travel to — begin (or continue) performing in place.
            return person.copy(currentActivity = activity.copy(startedAt = now, elapsedMinutes = 0))
        }

        val access = graph.accessPos(target)
        val path = if (access != null) Pathfinder.findPath(graph, person.location.pos, access) else emptyList()
        if (path.isEmpty()) {
            // Unreachable (or already there): perform in place rather than freeze.
            return person.copy(currentActivity = activity.copy(startedAt = now, elapsedMinutes = 0))
        }
        return person.copy(
            currentActivity = activity,
            location = person.location.copy(path = path),
        )
    }
}
