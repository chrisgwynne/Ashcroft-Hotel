package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CausalGraph
import com.ashcroft.ripple.core.model.CauseType
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7A — the causal graph merge must no longer cost O(n) per tick. Rather than
 * assert a wall-clock bound (which drifts with the machine), this compares the cost
 * of merging into a tiny graph with merging into a large one: with a structurally
 * shared map the two are close; with a full copy per merge the large graph is
 * dramatically slower. The ratio is machine-independent.
 */
class Phase7PerformanceTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    private fun mergeNanos(graph: CausalGraph, iterations: Int): Long {
        var g = graph
        val start = System.nanoTime()
        for (i in 0 until iterations) {
            val log = CauseLog(com.ashcroft.ripple.core.model.SimTime(i.toLong()))
            log.emit(CauseType.DECISION, "d", 0.3)
            g = log.foldInto(g) // fold into the growing graph; structural sharing keeps this cheap
        }
        return System.nanoTime() - start
    }

    @Test
    fun mergeCostDoesNotScaleWithGraphSize() {
        // A large steady-state graph vs an empty one.
        val large = engine.run(AshcroftScenario.initial(), 3 * 24 * 60).causes
        val small = CausalGraph()
        assertTrue("the large graph should really be large", large.size > 1_000)

        val iterations = 4_000
        // Warm up the JIT on both paths before measuring.
        mergeNanos(small, 500)
        mergeNanos(large, 500)
        val smallNanos = mergeNanos(small, iterations)
        val largeNanos = mergeNanos(large, iterations)

        // With an O(n) copy, merging into a >1000-node graph would be orders of
        // magnitude slower than into an empty one. Structural sharing keeps it close.
        val ratio = largeNanos.toDouble() / smallNanos.toDouble().coerceAtLeast(1.0)
        assertTrue("merge into a large graph must not scale with its size (ratio $ratio)", ratio < 8.0)
    }

    @Test
    fun foldIntoLeavesTheReceiverGraphUntouched() {
        val base = engine.run(AshcroftScenario.initial(), 60).causes
        val sizeBefore = base.size
        val log = CauseLog(com.ashcroft.ripple.core.model.SimTime(999_999))
        log.emit(CauseType.DECISION, "d", 0.3)
        val merged = log.foldInto(base)
        assertEquals("the original graph is immutable", sizeBefore, base.size)
        assertTrue("the merged graph carries the addition", merged.size == sizeBefore + 1)
        // The graph is backed by structurally-shared persistent collections — enforced
        // by the field types (PersistentMap / PersistentList) at compile time.
    }
}
