package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.ScoreComponentType
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Soak tests that run the whole simulation for extended periods and report
 * behavioural metrics — the check that ordinary hotel life already produces
 * visibly different lives, not near-identical routines.
 */
class SimulationSoakTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun oneDaySoakProducesMixedAndDiverseBehaviour() {
        var state = AshcroftScenario.initial()
        val completedVerbs = HashMap<ActionVerb, Int>()
        val terminalCounts = HashMap<ActionPhase, Int>()
        val driverCounts = HashMap<ScoreComponentType, Int>()
        var decisions = 0

        val previousDecision = HashMap<String, String?>()
        repeat(24 * 60) {
            val before = state.people.associate { it.id to it.action.phase }
            state = engine.step(state)
            for (person in state.people) {
                val was = before[person.id]
                val nowPhase = person.action.phase
                if (nowPhase != was && nowPhase.isTerminal) {
                    terminalCounts.merge(nowPhase, 1, Int::plus)
                    if (nowPhase == ActionPhase.COMPLETED) completedVerbs.merge(person.action.verb, 1, Int::plus)
                }
                val decisionId = person.lastDecision?.id?.value
                if (decisionId != null && decisionId != previousDecision[person.id.value]) {
                    previousDecision[person.id.value] = decisionId
                    decisions++
                    person.lastDecision?.consideredActions
                        ?.firstOrNull { it.candidate == person.lastDecision!!.chosenAction }
                        ?.topPositive()?.type?.let { driverCounts.merge(it, 1, Int::plus) }
                }
            }
        }

        val distinctSignatures = state.people.map { it.action.signature() }.toSet()
        val distinctRooms = state.people.mapNotNull { it.location.roomId }.toSet()

        println("=== Ripple Phase 3 — one-day soak ===")
        println("decisions made: $decisions")
        println("completed action distribution: $completedVerbs")
        println("terminal phases: $terminalCounts")
        println("primary decision drivers: $driverCounts")
        println("distinct end-of-day actions: ${distinctSignatures.size}; distinct rooms: ${distinctRooms.size}")

        assertTrue("several kinds of action should complete over a day", completedVerbs.keys.size >= 4)
        assertTrue("decisions should be driven by a mix of factors", driverCounts.size >= 3)
        assertTrue("needs drive some decisions", driverCounts.containsKey(ScoreComponentType.NEED_RELIEF))
        assertTrue("goals (including work commitments) drive some decisions", driverCounts.containsKey(ScoreComponentType.GOAL_PROGRESS))
        assertTrue("the cast should not all be doing the same thing", distinctSignatures.size >= 4)
        assertTrue("nor all be in one room", distinctRooms.size >= 4)
    }

    @Test
    fun oneWeekAndThirtyDaySoaksHoldInvariants() {
        val week = engine.run(AshcroftScenario.initial(), 7 * 24 * 60)
        assertTrue(week.people.size == 12)
        assertTrue(week.people.all { p -> NeedKind.entries.all { p.needs[it] in 0f..1f } })

        val month = engine.run(AshcroftScenario.initial(), 30 * 24 * 60)
        assertTrue(month.people.size == 12)
        assertTrue("memories stay bounded over a month", month.people.all { it.memories.size <= 40 })
        // Occupancy never double-counts anyone.
        val occupants = month.occupancy().values.flatten()
        assertTrue(occupants.size == occupants.toSet().size)
    }
}
