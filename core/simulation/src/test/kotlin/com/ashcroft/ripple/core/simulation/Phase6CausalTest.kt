package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CausalGraph
import com.ashcroft.ripple.core.model.CauseRelation
import com.ashcroft.ripple.core.model.CauseType
import com.ashcroft.ripple.core.model.SimTime
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
    fun unmetServiceLowersSatisfactionThroughATraceableChain() {
        // Overdue neglect is rare (a guest must be kept waiting where they sit), so
        // capture chains as they are emitted, tick by tick, rather than trust one
        // late snapshot the pruner may have thinned.
        var state = AshcroftScenario.initial()
        val seen = HashSet<String>()
        var fullChains = 0
        repeat(2 * 24 * 60) {
            state = engine.step(state)
            state.causes.nodes.values.filter { it.type == CauseType.TASK_OVERDUE && seen.add(it.id.value) }.forEach { o ->
                val fromCreation = state.causes.parentsOf(o.id).any {
                    state.causes.node(it.parentId)?.type == CauseType.TASK_CREATED
                }
                val toSatisfaction = state.causes.childrenOf(o.id).any {
                    state.causes.node(it.childId)?.type == CauseType.SATISFACTION_CHANGE
                }
                assertTrue("an overdue task must sour the guest it kept waiting", toSatisfaction)
                if (fromCreation && toSatisfaction) fullChains++
            }
        }
        assertTrue("neglect should trace, whole, from a task's creation through to the guest it soured", fullChains > 0)
    }

    @Test
    fun consequencesRaiseSignificanceRetrospectively() {
        // A cause with no consequence keeps its base significance; the same kind of
        // cause that later sours a guest is reinforced above it, and the echo reaches
        // its own parent. This is what the pruner reads to keep what mattered.
        val log0 = CauseLog(SimTime(0))
        val root = log0.emit(CauseType.TASK_CREATED, "task.x", significance = 0.2)
        var graph = log0.foldInto(CausalGraph())
        val log1 = CauseLog(SimTime(1))
        val consequence = log1.emit(
            CauseType.TASK_OVERDUE, "task.overdue.x", significance = 0.2, parents = listOf(root to CauseRelation.CAUSED),
        )
        log1.reinforce(consequence, 0.3)
        graph = log1.foldInto(graph)
        val consequenceSig = graph.node(consequence)!!.significance
        val rootSig = graph.node(root)!!.significance
        assertTrue("the consequence is reinforced above its base", consequenceSig > 0.2)
        assertTrue("and echoes a fraction up to its cause", rootSig > 0.2)
        assertTrue("the echo is weaker than the direct reinforcement", rootSig < consequenceSig)
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
