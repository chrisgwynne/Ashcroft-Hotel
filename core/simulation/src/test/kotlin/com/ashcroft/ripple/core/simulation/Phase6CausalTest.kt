package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CauseType
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6A — every meaningful change the engine makes leaves a causal record,
 * and the graph it builds stays acyclic, reference-clean and reproducible. No
 * node is created for movement, decay, awareness or routine ticks.
 */
class Phase6CausalTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun theCausalGraphStaysAcyclicAndReferenceClean() {
        val state = engine.run(AshcroftScenario.initial(), 8 * 60)
        assertTrue("the graph must never contain a cycle", state.causes.isAcyclic())
        assertTrue("every edge must reference real nodes", state.causes.referencesResolve())
        assertTrue("ordinary life should leave a causal trail", state.causes.size > 0)
    }

    @Test
    fun graphConstructionIsDeterministic() {
        val start = AshcroftScenario.initial()
        assertEquals(engine.run(start, 300).causes, engine.run(start, 300).causes)
    }

    @Test
    fun routineTicksDoNotFloodTheGraph() {
        // Movement, need decay, perception and awareness create no nodes, so the graph
        // grows far slower than person-ticks (12 people x 480 minutes = 5760 person-ticks).
        val state = engine.run(AshcroftScenario.initial(), 8 * 60)
        assertTrue("only meaningful changes are recorded (${state.causes.size} nodes)", state.causes.size < 2_000)
    }

    @Test
    fun decisionsAndConversationsAreRecorded() {
        val state = engine.run(AshcroftScenario.initial(), 4 * 60)
        val types = state.causes.nodes.values.map { it.type }.toSet()
        assertTrue("decisions are causes", types.contains(CauseType.DECISION))
        assertTrue("conversations are causes", types.contains(CauseType.CONVERSATION_ACT))
    }

    @Test
    fun memoriesCarryProvenanceThatResolvesInTheGraph() {
        val state = engine.run(AshcroftScenario.initial(), 6 * 60)
        val provenanced = state.people.flatMap { it.memories }.filter { it.causeId != null }
        assertTrue("memories should record how they were formed", provenanced.isNotEmpty())
        assertTrue(
            "and that provenance should resolve to a real cause node",
            provenanced.all { state.causes.node(it.causeId!!) != null },
        )
    }

    @Test
    fun firsthandBeliefCorrectionsAreRecordedWithProvenance() {
        // People move about and re-see each other; a confident belief seen to be
        // wrong is corrected, and that correction is a real recorded event.
        val state = engine.run(AshcroftScenario.initial(), 8 * 60)
        val corrections = state.causes.nodes.values.filter { it.type == CauseType.BELIEF_CORRECTED }
        assertTrue("ordinary life should overturn some stale beliefs", corrections.isNotEmpty())
        assertTrue(
            "a correction records the value it overturned, not an invented one",
            corrections.all { it.metadata["was"] != null && it.metadata["now"] != null && it.metadata["was"] != it.metadata["now"] },
        )
    }

    @Test
    fun decisionsPointBackAtTheCauseTheyProduced() {
        val state = engine.run(AshcroftScenario.initial(), 3 * 60)
        val decided = state.people.mapNotNull { it.lastDecision }.filter { it.resultingCauseIds.isNotEmpty() }
        assertTrue("a made decision should reference its own cause node", decided.isNotEmpty())
        assertTrue(
            "and that reference should resolve to a real DECISION node",
            decided.all { rec -> rec.resultingCauseIds.any { state.causes.node(it)?.type == CauseType.DECISION } },
        )
    }

    @Test
    fun serviceInteractionsFormATraceableChain() {
        val state = engine.run(AshcroftScenario.initial(), 12 * 60)
        val service = state.causes.nodes.values.filter { it.type == CauseType.SERVICE_INTERACTION }
        if (service.isNotEmpty()) {
            // A service interaction should trace back to the task completion that caused it.
            assertTrue(
                "service should link back to a completed task",
                service.any { s -> state.causes.parentsOf(s.id).any { state.causes.node(it.parentId)?.type == CauseType.TASK_COMPLETED } },
            )
        }
    }
}
