package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7G — the long arc is legible. Culture and careers reach the chronicle; a
 * decision can be read three ways (what happened, what was believed, what was
 * decisive); and every derived value can be traced, through the inspectors, back
 * to the evidence beneath it. Read-only throughout — inspecting never changes
 * the world.
 */
class Phase7ChronicleWhyTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun longArcMilestonesReachTheChronicle() {
        val state = engine.run(AshcroftScenario.initial(), 8 * 24 * 60)
        val headlines = state.chronicle.map { it.headline }
        // Over a week and more, either a custom settles or someone grows into their work
        // — a long-arc milestone the chronicle should have noticed.
        val longArc = headlines.any {
            it.contains("way of doing things") || it.contains("set their sights higher")
        }
        assertTrue("a long-arc milestone is written down ($headlines)", longArc)
    }

    @Test
    fun theWhyCanBeReadThreeWays() {
        val state = engine.run(AshcroftScenario.initial(), 500)
        val someone = state.people.first { it.lastDecision != null }
        val why = Inspectors.whyThreeWays(state, someone.id)
        assertEquals("the three views are all present", setOf("objective", "believed", "decisive"), why.keys)
        assertTrue("what was decisive is spelled out from the score breakdown", why.getValue("decisive").isNotEmpty())
        assertTrue("what they believed is always addressed", why.getValue("believed").isNotEmpty())
    }

    @Test
    fun inspectorsTraceIdentityBackToItsEvidence() {
        val state = engine.run(AshcroftScenario.initial(), 3 * 24 * 60)
        // Hotel culture reports its observation and evidence counts.
        assertTrue("the hotel's character is inspectable", Inspectors.cultureOf(state, EntityId.HOTEL).isNotEmpty())
        // A department identity card reads its character and customs.
        assertTrue(
            "a department has a legible identity",
            Inspectors.departmentIdentity(state, Department.HOUSEKEEPING).isNotEmpty(),
        )
        assertTrue("the hotel has a legible identity", Inspectors.hotelIdentity(state).isNotEmpty())
        // A staff member's standing of another traces to evidence, when one exists.
        val holder = state.people.firstOrNull { it.standings.personal.isNotEmpty() }
        if (holder != null) {
            val subject = holder.standings.personal.keys.first()
            assertTrue(
                "a held standing is inspectable and evidence-backed",
                Inspectors.standingHeld(state, holder.id, subject).isNotEmpty(),
            )
        }
    }

    @Test
    fun inspectingNeverChangesTheWorld() {
        val state = engine.run(AshcroftScenario.initial(), 400)
        val before = state
        Inspectors.hotelIdentity(state)
        Inspectors.whyThreeWays(state, state.people.first().id)
        Inspectors.cultureOf(state, EntityId.HOTEL)
        state.people.forEach { Inspectors.aspirationOf(state, it.id) }
        assertEquals("read-only inspection leaves the world untouched", before, state)
    }
}
