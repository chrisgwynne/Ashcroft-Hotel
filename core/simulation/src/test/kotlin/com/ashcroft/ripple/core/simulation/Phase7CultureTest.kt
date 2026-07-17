package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.decision.ActionScorer
import com.ashcroft.ripple.core.decision.CultureLens
import com.ashcroft.ripple.core.decision.DecisionContext
import com.ashcroft.ripple.core.model.ActionCandidate
import com.ashcroft.ripple.core.model.ActionId
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.CultureProfile
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.EvidenceDimension
import com.ashcroft.ripple.core.model.EvidenceId
import com.ashcroft.ripple.core.model.ScoreComponentType
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7D — a place develops a character, and it is *derived* from the repeated,
 * causally-recorded evidence of what has happened there, never assigned. A
 * department is a bundle of strengths, not one label; different departments come
 * out different; every trait can be traced to the evidence behind it; and a
 * settled culture quietly reweights the choices its people make — without ever
 * dictating them.
 */
class Phase7CultureTest {
    private val engine = SimulationEngine(AshcroftLayout.build())
    private val scorer = ActionScorer()

    @Test
    fun cultureFormsFromRepeatedEvidenceAndTracesBackToIt() {
        val state = engine.run(AshcroftScenario.initial(), 3 * 24 * 60)
        val hotel = state.culture.of(EntityId.HOTEL)
        assertNotNull("the hotel develops a character from a run of ordinary work", hotel)
        assertTrue("that character rests on many observations", hotel!!.observations > 5)
        assertTrue("and it keeps a trail back to the evidence behind it", hotel.sourceEvidenceIds.isNotEmpty())
        // Some trait has become genuinely pronounced — the residue of repetition.
        assertTrue("a long history makes at least one trait characteristic", hotel.pronounced().isNotEmpty())
    }

    @Test
    fun cultureIsABundleOfStrengthsNotASingleLabel() {
        var profile = CultureProfile(EntityId.HOTEL)
        val now = SimTime(0)
        // Repeated, consistent evidence on two different axes.
        repeat(8) { i ->
            profile = profile.observe(EvidenceDimension.RELIABILITY, 0.8, 1.0, EvidenceId("e:$i:r"), now)
            profile = profile.observe(EvidenceDimension.WARMTH, 0.7, 1.0, EvidenceId("e:$i:w"), now)
        }
        val pronounced = profile.pronounced()
        assertTrue("a place can be several things at once", pronounced.size >= 2)
        assertTrue("reliability is one of them", pronounced.containsKey(EvidenceDimension.RELIABILITY))
        assertTrue("warmth is another", pronounced.containsKey(EvidenceDimension.WARMTH))
    }

    @Test
    fun differentDepartmentsDevelopDistinctIdentities() {
        val state = engine.run(AshcroftScenario.initial(), 5 * 24 * 60)
        val departmental = state.culture.profiles.keys.filter { it.value.startsWith("dept:") }
        assertTrue("several departments accrue a character", departmental.size >= 2)
        // No two department profiles are the identical bundle — history diverges.
        val bundles = departmental.map { state.culture.of(it)!!.traits }
        assertTrue("departments do not converge on one identical character", bundles.toSet().size >= 2)
    }

    @Test
    fun aSingleEventBarelyMovesCulture() {
        val now = SimTime(0)
        val once = CultureProfile(EntityId.HOTEL).observe(EvidenceDimension.RELIABILITY, 1.0, 1.0, EvidenceId("e:0:r"), now)
        assertTrue("one event is not yet characteristic", once.pronounced().isEmpty())
        assertTrue("but it is recorded", once.trait(EvidenceDimension.RELIABILITY).weight > 0.0)
    }

    @Test
    fun aDiligentCulturePullsPeopleTowardWork() {
        val diligent = CultureLens(mapOf(EvidenceDimension.RELIABILITY to 0.6, EvidenceDimension.COMPETENCE to 0.6))
        val slack = CultureLens(mapOf(EvidenceDimension.RELIABILITY to -0.6, EvidenceDimension.COMPETENCE to -0.6))
        val work = ActionCandidate(ActionId("WORK||"), ActionVerb.WORK, null, null, 30)

        val inDiligent = scorer.score(work, contextWith(diligent, work), noiseSeed = 1)
        val inSlack = scorer.score(work, contextWith(slack, work), noiseSeed = 1)

        val diligentCulture = inDiligent.components.firstOrNull { it.type == ScoreComponentType.CULTURE_FIT }
        assertNotNull("a diligent department contributes a culture pull toward work", diligentCulture)
        assertTrue("that pull is positive", diligentCulture!!.value > 0.0)
        assertTrue(
            "and work scores higher where the culture is diligent than where it is slack",
            inDiligent.finalScore > inSlack.finalScore,
        )
    }

    @Test
    fun cultureBiasesButNeverDictates() {
        // A strong-diligence culture nudges work, but the nudge is small next to the
        // deterministic total — it can break a tie, not overwhelm a real preference.
        val strong = CultureLens(
            mapOf(
                EvidenceDimension.RELIABILITY to 1.0,
                EvidenceDimension.COMPETENCE to 1.0,
                EvidenceDimension.RESPONSIVENESS to 1.0,
            ),
        )
        val work = ActionCandidate(ActionId("WORK||"), ActionVerb.WORK, null, null, 30)
        val score = scorer.score(work, contextWith(strong, work), noiseSeed = 1)
        val culture = score.components.first { it.type == ScoreComponentType.CULTURE_FIT }
        assertTrue("the cultural pull is a modest bias, not a hammer", culture.value <= 0.4)
    }

    @Test
    fun cultureIsDeterministic() {
        val start = AshcroftScenario.initial()
        val a = engine.run(start, 600).culture
        val b = engine.run(start, 600).culture
        assertEquals("culture formation is fully deterministic", a, b)
    }

    private fun contextWith(culture: CultureLens, vararg candidates: ActionCandidate): DecisionContext {
        val person = AshcroftScenario.initial().people.first { it.role.isStaff }
        return DecisionContext(
            actor = person,
            currentRoom = person.location.roomId,
            perceivedPeople = emptyList(),
            knownOpportunities = emptyList(),
            activeGoals = emptyList(),
            activeCommitments = emptyList(),
            recentMemories = emptyList(),
            availableActions = candidates.toList(),
            availableTasks = emptyList(),
            simTime = SimTime(0),
            culture = culture,
        )
    }
}
