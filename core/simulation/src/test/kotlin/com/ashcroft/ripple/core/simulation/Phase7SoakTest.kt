package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Phase 7H — long-run validation. These soaks drive the hotel for weeks, a year,
 * and up to a decade of simulated time, checking that the emergent long-arc
 * machinery stays healthy over the long haul: reputations diverge between
 * observers, departments grow distinct characters, habits and customs settle,
 * careers develop, the chronicle keeps its long-arc milestones, and the bounded
 * structures (causal graph, evidence ledger) never grow without limit.
 *
 * They are heavy, so they are opt-in: set RIPPLE_SOAK=true to run them (CI leaves
 * it unset and skips them). Each prints a compact metrics block for the record.
 */
class Phase7SoakTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun soak30Days() {
        requireSoak()
        repeat(5) { run -> runAndReport("30d#$run", DAY * 30) }
    }

    @Test
    fun soakOneYear() {
        requireSoak()
        repeat(5) { run -> runAndReport("1yr#$run", DAY * 365) }
    }

    @Test
    fun soakFiveYears() {
        requireSoak()
        repeat(3) { run -> runAndReport("5yr#$run", DAY * 365 * 5) }
    }

    @Test
    fun soakTenYears() {
        requireSoak()
        runAndReport("10yr", DAY * 365 * 10)
    }

    private fun runAndReport(label: String, ticks: Int) {
        var state = AshcroftScenario.initial()
        val t0 = System.nanoTime()
        repeat(ticks) { state = engine.step(state) }
        val ms = (System.nanoTime() - t0) / 1_000_000

        val subjects = state.people.flatMap { it.standings.personal.keys }.toSet()
        val contested = subjects.count { s -> state.people.count { it.standings.personalOf(s) != null } >= 2 }
        val maxDivergence = subjects.maxOfOrNull { s ->
            val overalls = state.people.mapNotNull { it.standings.personalOf(s)?.overall }
            if (overalls.size >= 2) overalls.max() - overalls.min() else 0.0
        } ?: 0.0

        val hotelTraits = state.culture.of(EntityId.HOTEL)?.pronounced()?.size ?: 0
        val deptBundles = Department.entries.mapNotNull { state.culture.of(EntityId.department(it))?.pronounced() }
        val distinctDepartments = deptBundles.toSet().size
        val establishedPractices = state.practices.byEntity.values.sumOf { row -> row.values.count { it.isCustomary } }
        val settledHabitFolk = state.people.count { it.habits.settled().isNotEmpty() }
        val pursuing = state.people.count { it.aspiration?.isPursuing == true }
        val proven = state.people.count { it.aspiration?.isProven == true }
        val recognised = state.people.count { (it.aspiration?.recognition ?: 0.0) > 0.0 }
        val longArc = state.chronicle.count {
            it.headline.contains("way of doing things") || it.headline.contains("set their sights higher")
        }

        println(
            "SOAK $label ticks=$ticks ms=$ms " +
                "| rep: contested=$contested maxDiverge=${fmt(maxDivergence)} " +
                "| culture: hotelTraits=$hotelTraits distinctDepts=$distinctDepartments " +
                "| practices: established=$establishedPractices " +
                "| habits: settledFolk=$settledHabitFolk " +
                "| careers: pursuing=$pursuing proven=$proven recognised=$recognised " +
                "| chronicle: total=${state.chronicle.size} longArc=$longArc " +
                "| bounds: causes=${state.causes.size} evidence=${state.evidence.size}",
        )

        // Structural health that must hold at any horizon.
        assertTrue("$label: causal graph stays bounded", state.causes.size <= GRAPH_BOUND)
        assertTrue("$label: evidence ledger stays bounded", state.evidence.size <= EVIDENCE_BOUND)
        assertTrue("$label: the causal record stays acyclic and resolvable", state.causes.isAcyclic() && state.causes.referencesResolve())
        assertTrue("$label: staff form settled routines over the long haul", settledHabitFolk > 0)
        assertTrue("$label: departments develop character", distinctDepartments >= 1 && hotelTraits >= 1)
    }

    private fun requireSoak() = assumeTrue("set RIPPLE_SOAK=true to run", System.getenv("RIPPLE_SOAK") == "true")

    private fun fmt(v: Double): Double = kotlin.math.round(v * 1000) / 1000.0

    private companion object {
        const val DAY = 24 * 60
        const val GRAPH_BOUND = 20_000
        const val EVIDENCE_BOUND = 4_000
    }
}
