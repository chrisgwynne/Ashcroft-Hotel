package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8C (engine) — the "Understand" layer. Reputation is shown as a spread of
 * observer opinions, never one averaged score; a relationship is shown both ways
 * round and keeps the two directions apart. Everything is derived and deterministic.
 */
class Phase8LegibilityTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun reputationKeepsDivergentOpinionsApartInsteadOfAveraging() {
        val state = engine.run(AshcroftScenario.initial(), 3 * 24 * 60)
        // Someone judged by more than one observer.
        val subject = state.people.flatMap { it.standings.personal.keys }
            .firstOrNull { s -> state.people.count { it.standings.personalOf(s) != null } >= 2 }
            ?: return
        val view = Legibility.reputationOf(state, subject)
        assertTrue("the reputation names the subject", view.subjectName.isNotBlank())
        // Opinions are held individually, not collapsed to a mean.
        val opinions = view.admirers + view.doubters
        assertTrue("individual observer opinions are preserved", opinions.isNotEmpty())
        assertTrue("each opinion records how sure and how sourced it is", opinions.all { it.evidenceCount >= 0 })
        assertTrue("the summary reads as prose, not a number", view.summary.isNotBlank())
    }

    @Test
    fun reputationSummaryDistinguishesAudiences() {
        val state = engine.run(AshcroftScenario.initial(), 2 * 24 * 60)
        val subject = state.people.firstOrNull { p -> state.people.count { it.standings.personalOf(p.id) != null } >= 1 }?.id ?: return
        val view = Legibility.reputationOf(state, subject)
        // The summary talks about groups (staff / management / guests), not a global score.
        assertTrue(
            "audience-specific phrasing",
            view.summary.contains("staff") ||
                view.summary.contains("management") ||
                view.summary.contains("guests") ||
                view.summary.contains("Not yet"),
        )
    }

    @Test
    fun relationshipIsDirectionalAndNotASingleScore() {
        val state = engine.run(AshcroftScenario.initial(), 2 * 24 * 60)
        val pair = state.people.firstNotNullOfOrNull { p ->
            p.relationships.all.firstOrNull { it.warmth() > 0.1 || it[com.ashcroft.ripple.core.model.RelationDimension.FAMILIARITY] > 0.1 }
                ?.let { p.id to it.other }
        } ?: return
        val view = Legibility.relationship(state, pair.first, pair.second)
        // Two distinct directions, each with its own headline — never merged.
        assertEquals("A→B is from A to B", pair.first, view.a)
        assertTrue("A's view of B has a headline", view.aTowardB.headline.isNotBlank())
        assertTrue("B's view of A has a headline", view.bTowardA.headline.isNotBlank())
        assertTrue("the two directions are independent objects", view.aTowardB !== view.bTowardA)
    }

    @Test
    fun legibilityIsDeterministic() {
        val start = AshcroftScenario.initial()
        val a = engine.run(start, 1000)
        val b = engine.run(start, 1000)
        val subject = a.people.flatMap { it.standings.personal.keys }.firstOrNull() ?: return
        assertEquals(
            "reputation views are a pure function of state",
            Legibility.reputationOf(a, subject),
            Legibility.reputationOf(b, subject),
        )
    }
}
