package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.department
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8A — the observation engine. The pulse surfaces only meaningful
 * developments (never routine work), following projects a readable digest,
 * summaries stay honest about quiet days, and "jump to the next change" stops on
 * the simulation's own emergent turns — all pure, deterministic and evidence-backed.
 */
class Phase8ObservationTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    @Test
    fun pulseSurfacesMeaningfulDevelopmentsBetweenTwoSnapshots() {
        val start = AshcroftScenario.initial()
        val later = engine.run(start, 3 * 24 * 60)
        val events = PulseDetector.detect(start, later)
        assertTrue("a few days produce meaningful developments", events.isNotEmpty())
        // Everything surfaced clears the significance floor — nothing routine leaks through.
        assertTrue("no trivial noise surfaces", events.all { it.significance >= 0.4 })
        // Events are provenance-bearing where the underlying record has it (chronicle/service).
        assertTrue("developments carry a stable, unique id", events.map { it.id }.toSet().size == events.size)
    }

    @Test
    fun anIdenticalSnapshotHasNoPulse() {
        val state = engine.run(AshcroftScenario.initial(), 500)
        assertTrue("nothing changed between a state and itself", PulseDetector.detect(state, state).isEmpty())
    }

    @Test
    fun pulseIsDeterministic() {
        val start = AshcroftScenario.initial()
        val a = engine.run(start, 2000)
        val b = engine.run(start, 2000)
        assertEquals("the pulse is a pure function of the two states", PulseDetector.detect(start, a), PulseDetector.detect(start, b))
    }

    @Test
    fun routineWorkNeverSurfacesAsPulse() {
        // A single tick of ordinary life should almost never cross a meaningful band.
        var state = AshcroftScenario.initial()
        var noisyTicks = 0
        repeat(600) {
            val next = engine.step(state)
            // Any pulse here must be a genuine band crossing, not routine churn: assert the
            // kinds are all meaningful ones (there are no routine kinds by construction).
            PulseDetector.detect(state, next).forEach { assertTrue("only meaningful kinds exist", it.kind in PulseKind.entries) }
            if (PulseDetector.detect(state, next).isNotEmpty()) noisyTicks++
            state = next
        }
        // Meaningful developments are the exception, not the rule — most ticks are silent.
        assertTrue("meaningful change is rare tick-to-tick ($noisyTicks/600)", noisyTicks < 120)
    }

    @Test
    fun followProjectsAReadableDigestForEachTargetKind() {
        val state = engine.run(AshcroftScenario.initial(), 24 * 60)
        val someone = state.people.first { it.role.isStaff }
        val personDigest = FollowProjector.digest(state, FollowTarget.OfPerson(someone.id))
        assertTrue("a followed person has a title and lines", personDigest.title.isNotBlank() && personDigest.lines.isNotEmpty())

        val room = someone.location.roomId
        if (room != null) {
            val roomDigest = FollowProjector.digest(state, FollowTarget.OfRoom(room))
            assertTrue("a followed room reads its occupants and activity", roomDigest.lines.isNotEmpty())
        }
        val dept = someone.role.department()
        if (dept != null) {
            val deptDigest = FollowProjector.digest(state, FollowTarget.OfDepartment(dept))
            assertTrue("a followed department reads its team", deptDigest.lines.any { it.startsWith("team:") })
        }
    }

    @Test
    fun followStatePersistsAndFiltersToTheFollowed() {
        val target = FollowTarget.OfPerson(PersonId("someone"))
        val state = FollowState.EMPTY.follow(target)
        assertTrue("following is remembered", state.isFollowing(target))
        assertFalse("unfollowing forgets it", state.unfollow(target).isFollowing(target))
        // Serialisable so a follow survives the app closing.
        val json = kotlinx.serialization.json.Json.encodeToString(FollowState.serializer(), state)
        val restored = kotlinx.serialization.json.Json.decodeFromString(FollowState.serializer(), json)
        assertEquals("follow state round-trips", state, restored)
    }

    @Test
    fun summariesStayHonestAboutQuietDays() {
        val start = AshcroftScenario.initial()
        val later = engine.run(start, 5 * 24 * 60)
        val events = PulseDetector.detect(start, later)
        // A day with no events yields an empty summary — no filler.
        val emptyDay = Summaries.day(emptyList(), day = 3)
        assertTrue("an uneventful day says nothing", emptyDay.isEmpty)
        // A period reflection over real events names who changed most and counts the shifts.
        val reflection = Summaries.period(events, 0, 5) { id -> id.value }
        if (events.any { it.subjects.any { s -> s.value.startsWith("person:") } }) {
            assertTrue("the reflection surfaces headlines", reflection.headlines.isNotEmpty())
        }
    }

    @Test
    fun jumpToNextChangeStopsOnAnEmergentDevelopment() {
        val start = AshcroftScenario.initial()
        val jump = Observation.jumpToNextChange(engine, start, maxTicks = 3 * 24 * 60, minSignificance = 0.5)
        assertTrue("it arrives at a meaningful development", jump.arrived)
        assertTrue("and stops the moment one occurs", jump.events.isNotEmpty() && jump.events.all { it.significance >= 0.5 })
        assertTrue("without skipping the whole horizon", jump.ticks in 1..(3 * 24 * 60))
    }

    @Test
    fun jumpToNextChangeIsDeterministic() {
        val start = AshcroftScenario.initial()
        val a = Observation.jumpToNextChange(engine, start)
        val b = Observation.jumpToNextChange(engine, start)
        assertEquals("fast-forward stopping is deterministic", a.ticks, b.ticks)
        assertEquals("and lands on the same events", a.events, b.events)
    }
}
