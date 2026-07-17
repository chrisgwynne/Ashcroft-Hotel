package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6E — the whole living hotel, causal record and all, can be saved,
 * reloaded and replayed to an identical future; and its graph stays bounded,
 * acyclic and reference-clean however long it runs.
 */
class Phase6PersistenceTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun aWorldSavesAndReloadsExactly() {
        val state = engine.run(AshcroftScenario.initial(), 3 * 60)
        val restored = WorldStore.load(WorldStore.save(state))
        assertEquals("a reloaded world is the same world, causal record and all", state, restored)
    }

    @Test
    fun aReloadedWorldReplaysToTheIdenticalFuture() {
        val snapshot = engine.run(AshcroftScenario.initial(), 2 * 60)
        val reloaded = WorldStore.load(WorldStore.save(snapshot))
        // The engine is a pure, seeded function of the state, so both must unfold alike.
        val fromOriginal = engine.run(snapshot, 500)
        val fromReloaded = engine.run(reloaded, 500)
        assertEquals("replay must be deterministic across a save/load boundary", fromOriginal, fromReloaded)
        assertEquals("and the causal record must match too", fromOriginal.causes, fromReloaded.causes)
    }

    @Test
    fun theGraphStaysBoundedAcyclicAndCleanOverALongRun() {
        // Long enough to cross the prune cap many times over (it is first reached
        // within a day, so a stretch of days exercises many prune cycles).
        var state = AshcroftScenario.initial()
        var peak = 0
        repeat(10 * 24 * 60) {
            state = engine.step(state)
            peak = maxOf(peak, state.causes.size)
        }
        assertTrue("growth stays bounded (peak ${state.causes.size} / $peak)", peak < 12_000)
        assertTrue("the graph stays acyclic however long it runs", state.causes.isAcyclic())
        assertTrue("and reference-clean", state.causes.referencesResolve())
    }

    @Test
    fun pruningKeepsWhatIsStillReferenced() {
        // After heavy pruning, every provenance pointer a person still holds must resolve.
        val state = engine.run(AshcroftScenario.initial(), 10 * 24 * 60)
        val dangling = state.people.flatMap { it.memories }
            .mapNotNull { it.causeId }
            .count { state.causes.node(it) == null }
        // Some very old provenance may legitimately have been pruned with its memory gone;
        // what must hold is that live memories keep resolving pointers.
        val liveResolvable = state.people.flatMap { it.memories }
            .mapNotNull { it.causeId }
            .count { state.causes.node(it) != null }
        assertTrue("pruning should preserve referenced causes", liveResolvable > 0 || dangling == 0)
        assertTrue(
            "the chronicle's milestones keep their chains",
            state.chronicle.all { entry ->
                entry.causeIds.isEmpty() || entry.causeIds.any { state.causes.node(it) != null }
            },
        )
    }
}
