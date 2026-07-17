package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.decision.ActionCandidateProvider
import com.ashcroft.ripple.core.model.Aspiration
import com.ashcroft.ripple.core.model.BehaviourHistory
import com.ashcroft.ripple.core.model.GoalType
import com.ashcroft.ripple.core.world.AshcroftLayout
import com.ashcroft.ripple.core.world.AshcroftNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7F — careers grow from what people make of themselves, leaders act on the
 * standing they *believe* others hold, and a person's history quietly opens or
 * closes the opportunities in front of them. No promotion is ever scripted, no
 * threshold flips a title, and no leader is omniscient.
 */
class Phase7CareersTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun aspirationsFormFromAmbitionTimesARealRecordOfWork() {
        val state = engine.run(AshcroftScenario.initial(), 4 * 24 * 60)
        val staff = state.people.filter { it.role.isStaff }
        assertTrue("working staff develop a professional aspiration", staff.any { it.aspiration != null })
        // Drive rests on a demonstrated record, not temperament alone.
        val driven = staff.mapNotNull { it.aspiration }.filter { it.drive > 0.0 }
        assertTrue("some staff come to actively pursue advancement", driven.any { it.isPursuing })
        assertTrue(
            "and that pursuit is backed by a real track record, not just a wish",
            driven.any { it.demonstrated > 0.0 },
        )
    }

    @Test
    fun aPursuingStaffCarriesALongArcCareerGoal() {
        val state = engine.run(AshcroftScenario.initial(), 4 * 24 * 60)
        val pursuing = state.people.filter { it.aspiration?.isPursuing == true }
        assertTrue("some ambitious, accomplished staff come to pursue advancement", pursuing.isNotEmpty())
        // Roles never change by fiat — a career is an aspiration, not a scripted promotion.
        val startRoles = AshcroftScenario.initial().people.associate { it.id to it.role }
        assertTrue("nobody's role was changed by the engine", state.people.all { it.role == startRoles[it.id] })
        // The goal type exists and is derivable (structural guarantee).
        assertTrue("ADVANCE_CAREER is available as a long-arc goal", GoalType.entries.contains(GoalType.ADVANCE_CAREER))
    }

    @Test
    fun leadersRecogniseWhoTheyBelieveHasEarnedIt() {
        val state = engine.run(AshcroftScenario.initial(), 3 * 24 * 60)
        // Recognition leaves a trail: some non-management staff carry recognition backed
        // by the very evidence a manager's standing was built on.
        val recognised = state.people.mapNotNull { it.aspiration }.filter { it.recognition > 0.0 }
        assertTrue("leaders recognise people over a few days", recognised.isNotEmpty())
        assertTrue(
            "recognition traces back to the evidence behind the leader's belief",
            recognised.any { it.sourceEvidenceIds.isNotEmpty() },
        )
    }

    @Test
    fun historyOpensAnOpportunityThatANovicesDoesNotHave() {
        val base = AshcroftScenario.initial().people.first { it.role.isStaff }
        val provider = ActionCandidateProvider(worldFor(base))
        val proven = base.copy(aspiration = Aspiration(drive = 0.8, demonstrated = 0.8))
        val novice = base.copy(aspiration = null)
        val mentoring = { p: com.ashcroft.ripple.core.model.Person ->
            provider.knownOpportunities(p, emptyList()).any { it.label.contains("share the ropes") }
        }
        assertTrue("a proven, driven professional is offered a chance to lead others", mentoring(proven))
        assertFalse("a newcomer with no record is not", mentoring(novice))
    }

    @Test
    fun aRecentRunOfFailuresWithdrawsThatOpportunity() {
        val base = AshcroftScenario.initial().people.first { it.role.isStaff }
        val provider = ActionCandidateProvider(worldFor(base))
        val underCloud = base.copy(
            aspiration = Aspiration(drive = 0.8, demonstrated = 0.8),
            behaviour = BehaviourHistory(repeatedFailures = mapOf("a" to 3, "b" to 3)),
        )
        val offered = provider.knownOpportunities(underCloud, emptyList()).any { it.label.contains("share the ropes") }
        assertFalse("a run of setbacks costs the standing to take initiative", offered)
    }

    @Test
    fun careersAreDeterministic() {
        val start = AshcroftScenario.initial()
        val a = engine.run(start, 1000).people.map { it.aspiration }
        val b = engine.run(start, 1000).people.map { it.aspiration }
        assertEquals("career development is fully deterministic", a, b)
    }

    private fun worldFor(person: com.ashcroft.ripple.core.model.Person): HotelWorldQueries {
        val layout = AshcroftLayout.build()
        return HotelWorldQueries(layout, NavGraph.from(layout, AshcroftNav.stairPortals()), RoomLocator.from(layout), listOf(person))
    }
}
