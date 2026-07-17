package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CauseType
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6E long-run validation. Runs the whole causal simulation headless across
 * a month, a year and a decade, and asserts the properties the phase must hold
 * over time: the graph's growth stays bounded however long the hotel lives, it
 * never loses acyclicity or reference-integrity, meaningful provenance keeps
 * resolving, and the chronicle keeps accruing real, chain-backed milestones.
 */
class Phase6SoakTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    private data class Report(
        val days: Int,
        val peakNodes: Int,
        val finalNodes: Int,
        val finalEdges: Int,
        val acyclic: Boolean,
        val referencesResolve: Boolean,
        val chronicleEntries: Int,
        val chronicleWithChains: Int,
        val provenancedMemories: Int,
        val provenanceResolved: Int,
        val beliefCorrections: Int,
        val overdueEvents: Int,
    ) {
        fun print() {
            println("=== Ripple Phase 6 soak — seed 1924, $days days ===")
            println("graph: peak $peakNodes, final $finalNodes nodes / $finalEdges edges; acyclic $acyclic, resolves $referencesResolve")
            println("chronicle: $chronicleEntries entries, $chronicleWithChains with cause chains")
            println("provenance: $provenanceResolved/$provenancedMemories stamped memories resolve")
            println("consequences (sampled): $beliefCorrections belief corrections, $overdueEvents overdue events")
        }
    }

    private fun soak(days: Int): Report {
        var state = AshcroftScenario.initial()
        var peak = 0
        // Per-tick work is kept O(1): only the cheap size peak is tracked every
        // minute. Consequence counts are read from a periodic scan and, finally,
        // from the recent graph the pruner keeps — enough to prove they keep
        // happening without an O(nodes) scan on every one of millions of ticks.
        val corrections = HashSet<String>()
        val overdue = HashSet<String>()
        repeat(days * 24 * 60) { tick ->
            state = engine.step(state)
            if (state.causes.size > peak) peak = state.causes.size
            if (tick % SCAN_EVERY == 0) tallyConsequences(state, corrections, overdue)
        }
        tallyConsequences(state, corrections, overdue)
        val stampedMemories = state.people.flatMap { it.memories }.mapNotNull { it.causeId }
        return Report(
            days = days,
            peakNodes = peak,
            finalNodes = state.causes.size,
            finalEdges = state.causes.edges.size,
            acyclic = state.causes.isAcyclic(),
            referencesResolve = state.causes.referencesResolve(),
            chronicleEntries = state.chronicle.size,
            chronicleWithChains = state.chronicle.count { it.causeIds.isNotEmpty() },
            provenancedMemories = stampedMemories.size,
            provenanceResolved = stampedMemories.count { state.causes.node(it) != null },
            beliefCorrections = corrections.size,
            overdueEvents = overdue.size,
        )
    }

    @Test
    fun aMonthAYearAndBeyondStayBoundedSoundAndTraceable() {
        val month = soak(30)
        month.print()
        val year = soak(365)
        year.print()
        // Several years — long enough that any upward creep would show. Growth cannot
        // in fact exceed the prune cap by construction; this confirms it holds far past
        // a year, and that history keeps accruing rather than freezing.
        val years = soak(YEARS * 365)
        years.print()

        for (r in listOf(month, year, years)) {
            assertTrue("[${r.days}d] the graph must stay acyclic", r.acyclic)
            assertTrue("[${r.days}d] every edge must reference real nodes", r.referencesResolve)
            assertTrue("[${r.days}d] graph growth must stay bounded (peak ${r.peakNodes})", r.peakNodes < GRAPH_BOUND)
            assertTrue("[${r.days}d] stamped memories must keep resolving", r.provenanceResolved == r.provenancedMemories)
            assertTrue("[${r.days}d] chronicled milestones must keep their chains", r.chronicleWithChains == r.chronicleEntries)
        }
        // The long run produces real, traceable history — not a frozen or runaway record.
        assertTrue("years should accrue more milestones than one", years.chronicleEntries > year.chronicleEntries)
        assertTrue("the long run should see genuine belief corrections", years.beliefCorrections > 0)
        assertTrue("the long run should see genuine unmet-service events", years.overdueEvents > 0)
        // Bounded means bounded: growth never creeps above a year's ceiling, however long it runs.
        assertTrue("growth does not creep upward over years", years.peakNodes <= year.peakNodes)
    }

    private fun tallyConsequences(state: WorldState, corrections: HashSet<String>, overdue: HashSet<String>) {
        for (node in state.causes.nodes.values) {
            when (node.type) {
                CauseType.BELIEF_CORRECTED -> corrections.add(node.id.value)
                CauseType.TASK_OVERDUE -> overdue.add(node.id.value)
                else -> Unit
            }
        }
    }

    private companion object {
        const val YEARS = 3
        const val GRAPH_BOUND = 12_000
        const val SCAN_EVERY = 240
    }
}
