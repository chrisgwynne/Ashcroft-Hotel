package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Performance benchmark for the causal graph, opt-in via RIPPLE_BENCH=true so it
 * never runs on CI. Measures steady-state tick throughput and the cost of the two
 * per-tick graph operations (merge and prune) on identical scenarios, so a change
 * to the graph's internal structure can be compared before and after.
 */
class CausalGraphBenchmark {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Before
    fun benchIsOptIn() = assumeTrue("set RIPPLE_BENCH=true to run benchmarks", System.getenv("RIPPLE_BENCH") == "true")

    @Test
    fun steadyStateThroughput() {
        // Warm up until the graph reaches its steady-state (pruned) size.
        var state = engine.run(AshcroftScenario.initial(), WARMUP)
        val startNodes = state.causes.size

        val measured = 30 * 24 * 60
        val t0 = System.nanoTime()
        repeat(measured) { state = engine.step(state) }
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0

        val perTickUs = elapsedMs * 1000.0 / measured
        val ticksPerSec = measured / (elapsedMs / 1000.0)
        println("=== CausalGraph benchmark ===")
        println("warmup graph size: $startNodes; final size: ${state.causes.size}")
        println("measured ticks: $measured over %.0f ms".format(elapsedMs))
        println("throughput: %.0f ticks/sec (%.2f us/tick)".format(ticksPerSec, perTickUs))
        println(
            "projected 1yr: %.1f s; 3yr: %.1f s; 10yr: %.1f min".format(
                365.0 * 24 * 60 / ticksPerSec,
                3 * 365.0 * 24 * 60 / ticksPerSec,
                10 * 365.0 * 24 * 60 / ticksPerSec / 60.0,
            ),
        )
    }

    @Test
    fun mergeAndPruneMicrobench() {
        // A realistic steady-state graph to merge into and prune.
        val warm = engine.run(AshcroftScenario.initial(), WARMUP)
        val graph = warm.causes
        val now = warm.clock + 1

        // Merge: a handful of pending nodes folded into the graph (what every tick does).
        val mergeRuns = 20_000
        val m0 = System.nanoTime()
        repeat(mergeRuns) { i ->
            val log = CauseLog(now + i.toLong())
            repeat(4) { log.emit(com.ashcroft.ripple.core.model.CauseType.DECISION, "d", 0.3) }
            log.foldInto(graph)
        }
        val mergeUs = (System.nanoTime() - m0) / 1000.0 / mergeRuns

        // Prune: retain from a graph above the cap.
        val pruneRuns = 2_000
        val p0 = System.nanoTime()
        repeat(pruneRuns) { graph.retain(4_000, 3_000) }
        val pruneUs = (System.nanoTime() - p0) / 1000.0 / pruneRuns

        println("=== merge/prune microbench (graph ${graph.size} nodes) ===")
        println("merge (4 pending): %.2f us/call".format(mergeUs))
        println("prune (retain): %.2f us/call".format(pruneUs))
    }

    private companion object {
        const val WARMUP = 3 * 24 * 60
    }
}
