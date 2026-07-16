package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.DecisionRecord
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Personality
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.TraitKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {
    private val now = SimTime(13L * 60)
    private val seed = 1924L

    private fun decideFor(
        person: com.ashcroft.ripple.core.model.Person,
        world: StubWorld = StubWorld(),
        commitments: List<com.ashcroft.ripple.core.model.Commitment> =
            person.schedule.filter { it.isActiveAt(now.minuteOfDay) },
    ) = DecisionMaker(world).decide(person, commitments, now, seed)

    private fun scoreOf(record: DecisionRecord, verb: ActionVerb): Double? =
        record.consideredActions.filter { it.candidate.verb == verb }.maxByOrNull { it.finalScore }?.finalScore

    @Test
    fun decisionsAreDeterministic() {
        val p = testPerson(needs = needs(NeedKind.HUNGER to 0.2f))
        assertEquals(decideFor(p).action, decideFor(p).action)
        assertEquals(decideFor(p).record.consideredActions, decideFor(p).record.consideredActions)
    }

    @Test
    fun aStrongNeedRaisesTheRelevantActionAndIsChosen() {
        val hungry = testPerson(needs = needs(NeedKind.HUNGER to 0.02f), currentRoom = "reception")
        val result = decideFor(hungry)
        assertEquals(ActionVerb.EAT, result.action.verb)
        assertEquals("restaurant", result.action.targetRoom?.value)
        // The relevant action outscores an idle break.
        assertTrue(scoreOf(result.record, ActionVerb.EAT)!! > scoreOf(result.record, ActionVerb.TAKE_BREAK)!!)
    }

    @Test
    fun anActiveShiftPullsAConscientiousWorkerToWork() {
        val worker = testPerson(
            role = RoleKind.RECEPTIONIST,
            currentRoom = "reception",
            needs = NeedState_full(),
            personality = Personality.of(TraitKind.CONSCIENTIOUSNESS to 0.9f),
            schedule = listOf(shift("reception", 8, 18)),
        )
        val result = decideFor(worker)
        assertEquals(ActionVerb.WORK, result.action.verb)
        assertTrue(scoreOf(result.record, ActionVerb.WORK)!! > scoreOf(result.record, ActionVerb.TAKE_BREAK)!!)
    }

    @Test
    fun personalityShiftsPreferences() {
        val base = needs(NeedKind.SOCIAL to 0.4f)
        val outgoing = testPerson(
            id = "out",
            personality = Personality.of(TraitKind.SOCIABILITY to 0.95f),
            needs = base,
            currentRoom = "bar",
        )
        val reserved = testPerson(
            id = "res",
            personality = Personality.of(TraitKind.SOCIABILITY to 0.05f),
            needs = base,
            currentRoom = "bar",
        )
        val outgoingSocial = scoreOf(decideFor(outgoing).record, ActionVerb.SOCIALISE)!!
        val reservedSocial = scoreOf(decideFor(reserved).record, ActionVerb.SOCIALISE)!!
        assertTrue("the more sociable person values socialising more", outgoingSocial > reservedSocial)
    }

    @Test
    fun boundedNoiseCannotOverturnALargeUtilityGap() {
        val hungry = testPerson(needs = needs(NeedKind.HUNGER to 0.01f), currentRoom = "reception")
        for (s in 0L until 40L) {
            val result = DecisionMaker(StubWorld()).decide(hungry, emptyList(), now, s)
            assertEquals("eating should win for every seed given how hungry they are", ActionVerb.EAT, result.action.verb)
        }
    }

    @Test
    fun explanationsComeOnlyFromRecordedComponents() {
        val hungry = testPerson(needs = needs(NeedKind.HUNGER to 0.05f), currentRoom = "reception")
        val result = decideFor(hungry)
        val explanation = DecisionMaker(StubWorld()).explanationFor(result.record)
        assertTrue(explanation.positives.isNotEmpty())
        val chosenScore = result.record.consideredActions.first { it.candidate == result.record.chosenAction }
        val componentKeys = chosenScore.components.map { it.explanationKey }.toSet()
        assertTrue("every positive reason must correspond to a real component", componentKeys.containsAll(explanation.positives))
        assertTrue("alternatives are recorded", explanation.alternatives.isNotEmpty())
    }

    @Test
    fun aPersonCannotActOnAnInaccessibleOrUnknownRoom() {
        val guest = testPerson(role = RoleKind.GUEST, currentRoom = "reception")
        val result = decideFor(guest)
        assertTrue(
            "a guest never decides to work back-of-house",
            result.record.consideredActions.none { it.candidate.verb == ActionVerb.WORK },
        )
    }

    @Test
    fun aFreedPersonAlwaysHasSomethingToDo() {
        val idle = testPerson(currentRoom = "reception")
        assertNotNull(decideFor(idle).action)
    }

    private fun NeedState_full(): com.ashcroft.ripple.core.model.NeedState = com.ashcroft.ripple.core.model.NeedState.full()
}
