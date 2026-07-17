package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CausalGraph
import com.ashcroft.ripple.core.world.AshcroftLayout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Phase 6 dev windows report the causal record faithfully and export it losslessly. */
class InspectorsCausalTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun statsReportAHealthyGraph() {
        val state = engine.run(AshcroftScenario.initial(), 4 * 60)
        val stats = Inspectors.causalStats(state)
        assertTrue("stats should be reported", stats.isNotEmpty())
        assertTrue(
            "the graph should read as acyclic and resolved",
            stats.any { it.contains("acyclic true") && it.contains("resolve true") },
        )
    }

    @Test
    fun whyAndHistoryReadFromTheGraph() {
        val state = engine.run(AshcroftScenario.initial(), 6 * 60)
        val someone = state.people.first { p -> state.causes.nodes.values.any { p.id in it.actorIds } }
        assertTrue("a person who acted has a causal history", Inspectors.causalHistory(state, someone.id).isNotEmpty())
        val anyWhy = state.people.any { Inspectors.why(state, it.id).isNotEmpty() }
        assertTrue("at least one person can explain their last decision", anyWhy)
    }

    @Test
    fun theCausalRecordExportsAndReloadsLosslessly() {
        val state = engine.run(AshcroftScenario.initial(), 3 * 60)
        val json = Inspectors.exportCauses(state)
        val restored = Json.decodeFromString(CausalGraph.serializer(), json)
        assertEquals("export must round-trip exactly", state.causes, restored)
        assertTrue("a restored graph is still acyclic", restored.isAcyclic())
    }
}
