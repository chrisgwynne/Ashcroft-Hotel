package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.InformationSource
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 — proves that ordinary life produces knowledge, beliefs, relationships
 * and feelings, and that people act on what they *believe* rather than on world
 * truth. No dramatic content is involved: staff simply share rooms, talk, and
 * remember.
 */
class Phase4SocialTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun peopleLearnWhoIsAroundThemByObservation() {
        val state = engine.run(AshcroftScenario.initial(), 120)
        // Everyone who has shared a room should have formed observed beliefs.
        val observers = state.people.filter { p -> p.knowledge.all.any { it.source == InformationSource.OBSERVED } }
        assertTrue("observation should populate personal knowledge", observers.size >= state.people.size / 2)
    }

    @Test
    fun conversationsPropagateInformationAsRumour() {
        // Over a day staff disperse to their shifts, see different things, then
        // reconvene and talk — the only way one person learns what another saw.
        val state = engine.run(AshcroftScenario.initial(), 24 * 60)
        val heard = state.people.flatMap { it.knowledge.all }.filter { it.source == InformationSource.CONVERSATION }
        assertTrue("talking should pass information from person to person", heard.isNotEmpty())
        assertTrue("second-hand information is held more loosely than first-hand", heard.all { it.isRumour })
    }

    @Test
    fun beliefsCanFallOutOfStepWithTheWorld() {
        // Over a day people move on while others' beliefs about them go stale.
        var state = AshcroftScenario.initial()
        var everSawAFalseBelief = false
        repeat(24 * 60) {
            state = engine.step(state)
            if (WorldTruth.falseBeliefs(state).isNotEmpty()) everSawAFalseBelief = true
        }
        assertTrue("stale knowledge should let false beliefs arise naturally", everSawAFalseBelief)
    }

    @Test
    fun relationshipsGrowMultidimensionally() {
        val state = engine.run(AshcroftScenario.initial(), 480)
        val known = state.people.flatMap { it.relationships.all }.filter { rel ->
            rel[RelationDimension.FAMILIARITY] > 0.0
        }
        assertTrue("shared life should build relationships", known.isNotEmpty())
        // The dimensions must move independently — not one collapsed friendship score.
        val anyDivergent = known.any { rel ->
            rel[RelationDimension.FAMILIARITY] != rel[RelationDimension.TRUST]
        }
        assertTrue("relationship dimensions should differ from one another", anyDivergent)
    }

    @Test
    fun ordinaryLifeStirsEmotions() {
        var state = AshcroftScenario.initial()
        var felt = false
        repeat(8 * 60) {
            state = engine.step(state)
            if (state.people.any { p -> EmotionKind.entries.any { p.emotions[it] > EmotionState_FELT } }) felt = true
        }
        assertTrue("interactions should stir feelings", felt)
    }

    private companion object {
        const val EmotionState_FELT = 0.2
    }
}
