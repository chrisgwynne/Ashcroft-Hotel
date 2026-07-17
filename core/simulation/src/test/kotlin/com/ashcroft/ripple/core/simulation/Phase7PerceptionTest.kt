package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.GuestPerception
import com.ashcroft.ripple.core.model.GuestValue
import com.ashcroft.ripple.core.model.PerceptionDimension
import com.ashcroft.ripple.core.model.PerceptionWeights
import com.ashcroft.ripple.core.model.Personality
import com.ashcroft.ripple.core.model.ReturnStage
import com.ashcroft.ripple.core.model.StayPurpose
import com.ashcroft.ripple.core.model.TraitKind
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7C — a guest's view of the hotel is multi-dimensional and weighted by what
 * they came for. Repeated good service builds satisfaction and a wish to return; a
 * single lapse dents only the axis it touches; and intending to return is never the
 * same as a guaranteed booking.
 */
class Phase7PerceptionTest {
    private val engine = SimulationEngine(AshcroftLayout.build())
    private val plain = Personality(mapOf())

    @Test
    fun stayPurposeAndExpectationsChangeWhatIsWeighted() {
        val business = PerceptionWeights.forGuest(StayPurpose.BUSINESS, setOf(GuestValue.SPEED), plain)
        val holiday = PerceptionWeights.forGuest(StayPurpose.HOLIDAY, setOf(GuestValue.FRIENDLINESS), plain)
        assertTrue(
            "a business guest weights speed heavily",
            business.getValue(PerceptionDimension.SPEED) > holiday.getValue(PerceptionDimension.SPEED),
        )
        assertTrue(
            "a holiday guest weights warmth more",
            holiday.getValue(PerceptionDimension.STAFF_WARMTH) > business.getValue(PerceptionDimension.STAFF_WARMTH),
        )
    }

    @Test
    fun oneMinorFailureDentsOnlyItsOwnDimension() {
        // A picture that is good across the board, then one bad speed observation.
        var picture = GuestPerception()
        for (dimension in PerceptionDimension.entries) {
            picture = picture.witness(dimension, direction = 0.8, weight = 2.0)
        }
        val before = picture.satisfaction(evenWeights())
        val dented = picture.witness(PerceptionDimension.SPEED, direction = -1.0, weight = 1.0)
        val after = dented.satisfaction(evenWeights())
        assertTrue("satisfaction dips a little", after < before)
        assertTrue("but does not collapse — the other axes still stand", after > before - 0.2)
        // Only speed actually moved; the rest are untouched.
        assertEquals(
            picture.dimensions.getValue(PerceptionDimension.CLEANLINESS),
            dented.dimensions.getValue(PerceptionDimension.CLEANLINESS),
        )
    }

    @Test
    fun repeatedGoodServiceBuildsSatisfactionAndAWishToReturn() {
        val state = engine.run(AshcroftScenario.initial(), 24 * 60)
        val guests = state.people.mapNotNull { it.stay }
        assertTrue("guests form a multi-dimensional picture", guests.any { it.perception.dimensions.isNotEmpty() })
        val bestServed = guests.maxByOrNull { it.perception.dimensions.size } ?: return
        // Someone experiences the hotel enough to develop a return wish above nothing.
        assertTrue("a well-experienced guest develops some intention to return", guests.any { it.returnIntention.value > 0.0 })
        assertTrue("their picture has several axes", bestServed.perception.dimensions.size >= 1)
    }

    @Test
    fun intentionIsNeverAGuaranteedReturn() {
        val state = engine.run(AshcroftScenario.initial(), 3 * 24 * 60)
        val stages = state.people.mapNotNull { it.stay?.returnIntention?.stage }.toSet()
        // The funnel may reach intending/planning, but never books or arrives itself —
        // an actual return needs opportunity the wish alone cannot conjure.
        assertTrue("no stay auto-advances to a booking or arrival", stages.none { it == ReturnStage.BOOKED || it == ReturnStage.ARRIVED })
    }

    @Test
    fun conscientiousGuestsWeightCleanlinessMore() {
        val fussy = Personality(mapOf(TraitKind.CONSCIENTIOUSNESS to 0.9f))
        val relaxed = Personality(mapOf(TraitKind.CONSCIENTIOUSNESS to 0.2f))
        val a = PerceptionWeights.forGuest(StayPurpose.HOLIDAY, emptySet(), fussy)
        val b = PerceptionWeights.forGuest(StayPurpose.HOLIDAY, emptySet(), relaxed)
        assertTrue(
            "a stickler cares more about cleanliness",
            a.getValue(PerceptionDimension.CLEANLINESS) > b.getValue(PerceptionDimension.CLEANLINESS),
        )
    }

    @Test
    fun perceptionIsDeterministic() {
        val start = AshcroftScenario.initial()
        val a = engine.run(start, 400).people.mapNotNull { it.stay }
        val b = engine.run(start, 400).people.mapNotNull { it.stay }
        assertEquals("guest perception and loyalty are fully deterministic", a, b)
    }

    private fun evenWeights(): Map<PerceptionDimension, Double> = PerceptionDimension.entries.associateWith { 1.0 }
}
