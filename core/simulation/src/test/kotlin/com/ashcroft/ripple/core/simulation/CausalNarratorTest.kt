package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The narrator only ever renders the graph — it must produce readable lines for
 * real nodes and never invent a cause the simulation did not record.
 */
class CausalNarratorTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    private fun narrator(state: WorldState): CausalNarrator {
        val names = state.people.associate { it.id to it.name }
        return CausalNarrator(
            graph = state.causes,
            personName = { names[it] ?: it.value },
            roomName = { it.value },
        )
    }

    @Test
    fun aDecisionTellsAThreeLevelStoryFromRealNodes() {
        val state = engine.run(AshcroftScenario.initial(), 6 * 60)
        val narrator = narrator(state)
        val told = state.people.mapNotNull { it.lastDecision }
            .firstOrNull { it.resultingCauseIds.isNotEmpty() && state.causes.node(it.resultingCauseIds.first()) != null }
        assertTrue("some decision should be in the graph to explain", told != null)
        val story = narrator.tell(told!!.resultingCauseIds)
        assertTrue("the immediate reason should be rendered", story.immediate.isNotEmpty())
        assertTrue("every rendered line should be real text", story.immediate.all { it.text.isNotBlank() })
        // Deeper levels never precede the thing they explain in time.
        val topTime = story.immediate.maxOf { it.at.epochMinutes }
        assertTrue("what led here cannot come after it", story.becauseOf.all { it.at.epochMinutes <= topTime })
    }

    @Test
    fun aPersonsHistoryReadsBackFromTheGraphNewestFirst() {
        val state = engine.run(AshcroftScenario.initial(), 8 * 60)
        val narrator = narrator(state)
        val someone = state.people.first { p -> state.causes.nodes.values.any { p.id in it.actorIds } }
        val history = narrator.historyOf(someone.id)
        assertTrue("a person who acted should have a causal history", history.isNotEmpty())
        assertTrue("history should read newest first", history.zipWithNext().all { it.first.at >= it.second.at })
        assertTrue("every entry should be readable", history.all { it.text.isNotBlank() })
    }
}
