package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.decision.ActionScorer
import com.ashcroft.ripple.core.decision.DecisionContext
import com.ashcroft.ripple.core.model.ActionCandidate
import com.ashcroft.ripple.core.model.ActionId
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.HabitProfile
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.Practice
import com.ashcroft.ripple.core.model.PracticeStage
import com.ashcroft.ripple.core.model.ScoreComponentType
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.routineKeyOf
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7E — what people do repeatedly becomes a habit; what a group of them
 * independently take up becomes a custom; and both feed back into future choices.
 * All of it is derived from real behaviour (and the watching of respected
 * colleagues), never a copied trait or a scripted tradition, and it biases rather
 * than dictates.
 */
class Phase7PracticesTest {
    private val engine = SimulationEngine(AshcroftLayout.build())
    private val scorer = ActionScorer()

    @Test
    fun habitsFormFromAPersonsOwnRepeatedBehaviour() {
        val state = engine.run(AshcroftScenario.initial(), 3 * 24 * 60)
        val staff = state.people.filter { it.role.isStaff }
        assertTrue("staff who work all day settle into routines", staff.any { it.habits.habits.isNotEmpty() })
        assertTrue(
            "a habit rests on repeated observations, not one action",
            staff.flatMap { it.habits.habits.values }.any { it.observations > 3 },
        )
        assertTrue("some routine has genuinely settled in", staff.any { it.habits.settled().isNotEmpty() })
    }

    @Test
    fun aHabitClimbsWithRepetitionAndFadesWithDisuse() {
        val now = SimTime(0)
        var profile = HabitProfile.EMPTY
        val key = "WORK|desk"
        repeat(12) { profile = profile.reinforce(key, 0.08, now) }
        val settled = profile.strengthOf(key)
        assertTrue("repetition builds the habit", settled > 0.3)
        // Reinforcing *other* routines lets this one quietly fade.
        repeat(400) { profile = profile.reinforce("EAT|canteen", 0.02, now) }
        assertTrue("disuse unlearns it", profile.strengthOf(key) < settled)
    }

    @Test
    fun aLearnedHabitBiasesTheFamiliarChoice() {
        val person = AshcroftScenario.initial().people.first { it.role.isStaff }
        val room = person.location.roomId
        val key = routineKeyOf(ActionVerb.WORK, room)
        var habits = HabitProfile.EMPTY
        repeat(20) { habits = habits.reinforce(key, 0.08, SimTime(0)) }
        val work = ActionCandidate(ActionId("WORK|${room?.value}|"), ActionVerb.WORK, room, null, 30)

        val withHabit = scorer.score(work, context(person.copy(habits = habits), work), noiseSeed = 3)
        val without = scorer.score(work, context(person, work), noiseSeed = 3)
        assertTrue("the habitual choice scores higher", withHabit.finalScore > without.finalScore)
        assertTrue(
            "and it shows up as habit strength in the breakdown",
            withHabit.components.any { it.type == ScoreComponentType.HABIT_STRENGTH && it.value > 0.0 },
        )
    }

    @Test
    fun aCustomAddsAGentlePullToConform() {
        val person = AshcroftScenario.initial().people.first { it.role.isStaff }
        val room = person.location.roomId
        val key = routineKeyOf(ActionVerb.WORK, room)
        val work = ActionCandidate(ActionId("WORK|${room?.value}|"), ActionVerb.WORK, room, null, 30)
        val ctx = context(person, work).copy(customaryPractices = setOf(key))
        val score = scorer.score(work, ctx, noiseSeed = 3)
        assertTrue(
            "matching the custom is favoured",
            score.components.any { it.type == ScoreComponentType.HABIT_STRENGTH && it.value > 0.0 },
        )
    }

    @Test
    fun practicesEmergeFromTheAggregateOfHabits() {
        val state = engine.run(AshcroftScenario.initial(), 7 * 24 * 60)
        assertTrue("places accrue practices from their people's routines", state.practices.byEntity.isNotEmpty())
        val allPractices = state.practices.byEntity.values.flatMap { it.values }
        assertTrue(
            "at least one custom has taken hold rather than lain dormant",
            allPractices.any { it.stage != PracticeStage.DORMANT && it.adoption > 0.1 },
        )
    }

    @Test
    fun practiceStageIsReadFromAdoptionNotScripted() {
        val now = SimTime(0)
        var practice = Practice("WORK|desk")
        repeat(30) { practice = practice.observed(0.8, now) }
        assertEquals("wide, sustained adoption makes it established", PracticeStage.ESTABLISHED, practice.stage)
        // The group drifts away from it; it fades, then lapses — no script, just the numbers.
        repeat(60) { practice = practice.observed(0.0, now) }
        assertTrue("abandonment lets a custom lapse", practice.stage == PracticeStage.DORMANT || practice.stage == PracticeStage.FADING)
    }

    @Test
    fun learningAndCustomsAreDeterministic() {
        val start = AshcroftScenario.initial()
        val a = engine.run(start, 800)
        val b = engine.run(start, 800)
        assertEquals("habit formation is deterministic", a.people.map { it.habits }, b.people.map { it.habits })
        assertEquals("practice formation is deterministic", a.practices, b.practices)
    }

    private fun context(person: Person, vararg candidates: ActionCandidate): DecisionContext = DecisionContext(
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
    )
}
