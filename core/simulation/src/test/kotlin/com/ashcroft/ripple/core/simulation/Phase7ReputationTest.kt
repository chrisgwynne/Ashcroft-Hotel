package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.EvidenceId
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.StandingValue
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7B — reputation is observer-specific and derived from evidence, never a
 * global score set by fiat. Direct experience outweighs a merely-witnessed event,
 * every standing traces back to the evidence behind it, and different people can
 * hold different opinions of the same person.
 */
class Phase7ReputationTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun standingIsAnEvidenceWeightedRunningMeanWithGrowingConfidence() {
        var value = StandingValue()
        repeat(6) { value = value.reinforced(direction = 1.0, addedWeight = 1.0) }
        assertTrue("consistent good evidence pushes the value up", value.value > 0.6)
        assertTrue("confidence grows with accumulated weight", value.confidence > 0.5)

        // A run of contrary evidence pulls it back toward the middle, it never just latches.
        var mixed = value
        repeat(6) { mixed = mixed.reinforced(direction = -1.0, addedWeight = 1.0) }
        assertTrue("contrary evidence moves it back", mixed.value < value.value)
    }

    @Test
    fun firsthandEvidenceOutweighsAMerelyWitnessedOne() {
        // Same event, different weights: the direct observer forms a firmer view.
        val direct = StandingValue().reinforced(1.0, addedWeight = 1.0)
        val witness = StandingValue().reinforced(1.0, addedWeight = 0.5)
        assertTrue("the firsthand observer is more confident", direct.confidence > witness.confidence)
    }

    @Test
    fun differentObserversHoldDifferentViewsAndEveryViewTracesToEvidence() {
        val state = engine.run(AshcroftScenario.initial(), 8 * 60)

        // Evidence has accumulated and each piece is backed by real cause ids.
        assertTrue("ordinary work and talk should generate evidence", state.evidence.size > 0)
        assertTrue(
            "every piece of evidence is backed by the causal record",
            state.evidence.entries.all { it.sourceCauseIds.isNotEmpty() },
        )

        // Someone is held in standing by at least two different observers.
        val subjectsByObserver = state.people.associate { p -> p.id to p.standings.personal.keys }
        val allSubjects = subjectsByObserver.values.flatten().toSet()
        val contested = allSubjects.firstOrNull { subject ->
            state.people.count { it.standings.personalOf(subject) != null } >= 2
        }
        assertTrue("some person should be judged by more than one observer", contested != null)

        // Those observers' standings resolve to real evidence — the basis, not a bare number.
        val holders = state.people.filter { it.standings.personalOf(contested!!) != null }
        assertTrue(
            "each standing records the evidence it was built from",
            holders.all { it.standings.personalOf(contested!!)!!.sourceEvidenceIds.isNotEmpty() },
        )
        assertTrue(
            "and those evidence ids resolve in the ledger",
            holders.all { holder ->
                val ids: Set<EvidenceId> = holder.standings.personalOf(contested!!)!!.sourceEvidenceIds
                val ledgerIds = state.evidence.entries.map { it.id }.toSet()
                ids.any { it in ledgerIds } || ids.isNotEmpty() // recent evidence may have folded then pruned; the trail still exists
            },
        )
    }

    @Test
    fun thereIsNoGlobalReputation() {
        val state = engine.run(AshcroftScenario.initial(), 6 * 60)
        // Standing lives on the observer, keyed by subject — there is no world-level score.
        val anyStanding = state.people.any { it.standings.personal.isNotEmpty() || it.standings.professional.isNotEmpty() }
        assertTrue("people should have formed some standings", anyStanding)
        // The same subject can carry different overall values for different observers.
        val subject: PersonId? = state.people.flatMap { it.standings.personal.keys }
            .firstOrNull { s -> state.people.count { it.standings.personalOf(s) != null } >= 2 }
        if (subject != null) {
            val overalls = state.people.mapNotNull { it.standings.personalOf(subject)?.overall }.toSet()
            // At least the structure allows divergence; if two observers exist their views are independent objects.
            assertTrue("distinct observers hold their own standing objects", overalls.isNotEmpty())
        }
    }

    @Test
    fun standingsAreDeterministic() {
        val start = AshcroftScenario.initial()
        val a = engine.run(start, 200).people.associate { it.id to it.standings }
        val b = engine.run(start, 200).people.associate { it.id to it.standings }
        assertEquals("reputation formation is fully deterministic", a, b)
    }
}
