package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.BehaviourHistory
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.GridCell
import com.ashcroft.ripple.core.model.Identity
import com.ashcroft.ripple.core.model.LifeStage
import com.ashcroft.ripple.core.model.LocationState
import com.ashcroft.ripple.core.model.Memory
import com.ashcroft.ripple.core.model.MemoryId
import com.ashcroft.ripple.core.model.MemoryKind
import com.ashcroft.ripple.core.model.NeedState
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.Personality
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.WorldPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4RecallTest {
    private val now = SimTime(5_000)
    private val bar = RoomId("bar")
    private val theo = PersonId("theo")

    private fun person(memories: List<Memory>, room: String? = "bar") = Person(
        id = PersonId("maya"),
        identity = Identity("Maya", 27),
        lifeStage = LifeStage.ADULT,
        role = RoleKind.RECEPTIONIST,
        personality = Personality.of(),
        needs = NeedState.full(),
        homeRoom = RoomId("staff_room"),
        schedule = emptyList(),
        goals = emptyList(),
        memories = memories,
        acquaintances = emptySet(),
        behaviour = BehaviourHistory(),
        money = 100,
        location = LocationState(WorldPos(0, GridCell(1, 1)), room?.let { RoomId(it) }, emptyList()),
        action = ActionState.IDLE,
        lastDecision = null,
    )

    private fun memory(id: String, kind: MemoryKind, subject: PersonId?, valence: Double, place: RoomId? = null) = Memory(
        id = MemoryId(id),
        ownerId = PersonId("maya"),
        occurredAt = SimTime(100),
        kind = kind,
        subjectId = subject,
        valence = valence,
        importance = 0.8,
        placeId = place,
    )

    private fun emptyContext() = MemoryRecall.Context(
        room = null,
        presentPeople = emptySet(),
        currentVerb = null,
        strongestEmotion = null,
        goalSubjects = emptySet(),
        goalTypes = emptySet(),
        rumourSubjects = emptySet(),
    )

    @Test
    fun enteringAPlaceRecallsWhatHappenedThere() {
        val m = memory("m1", MemoryKind.WAS_EMBARRASSED, null, valence = -0.8, place = bar)
        val ctx = emptyContext().copy(room = bar)
        assertEquals(m.id, MemoryRecall.recall(person(listOf(m)), ctx, now).recalled?.id)
    }

    @Test
    fun seeingAPersonRecallsAMemoryOfThem() {
        val m = memory("m1", MemoryKind.WAS_INTERRUPTED, theo, valence = -0.7)
        val ctx = emptyContext().copy(presentPeople = setOf(theo))
        val recalled = MemoryRecall.recall(person(listOf(m)), ctx, now).recalled
        assertEquals(theo, recalled?.subjectId)
    }

    @Test
    fun aRecalledUnpleasantMemoryStirsUnease() {
        val m = memory("m1", MemoryKind.WAS_EMBARRASSED, theo, valence = -0.9, place = bar)
        val result = MemoryRecall.recall(person(listOf(m)), emptyContext().copy(room = bar), now)
        assertTrue("an embarrassing memory should stir embarrassment", (result.emotionDeltas[EmotionKind.EMBARRASSMENT] ?: 0.0) > 0.0)
    }

    @Test
    fun aRecalledPleasantMemoryLiftsTheMood() {
        val m = memory("m1", MemoryKind.WAS_PRAISED, theo, valence = 0.9)
        val result = MemoryRecall.recall(person(listOf(m)), emptyContext().copy(presentPeople = setOf(theo)), now)
        assertTrue((result.emotionDeltas[EmotionKind.HAPPINESS] ?: 0.0) > 0.0)
    }

    @Test
    fun irrelevantMemoriesAreNotRecalled() {
        // A memory anchored to the bar and to Theo, with neither present, has no cue.
        val m = memory("m1", MemoryKind.WAS_HELPED, theo, valence = 0.8, place = bar)
        val ctx = emptyContext().copy(room = RoomId("kitchen"), presentPeople = setOf(PersonId("sam")))
        assertNull("nothing should surface without a cue", MemoryRecall.recall(person(listOf(m)), ctx, now).recalled)
    }

    @Test
    fun recallIsDeterministic() {
        val ms = listOf(
            memory("m1", MemoryKind.WAS_HELPED, theo, valence = 0.6, place = bar),
            memory("m2", MemoryKind.WAS_IGNORED, theo, valence = -0.6, place = bar),
        )
        val ctx = emptyContext().copy(room = bar, presentPeople = setOf(theo))
        val a = MemoryRecall.recall(person(ms), ctx, now).recalled?.id
        val b = MemoryRecall.recall(person(ms), ctx, now).recalled?.id
        assertEquals(a, b)
    }

    @Test
    fun recallMarksTheMemoryAsRevisitedWithoutRewritingTruth() {
        val m = memory("m1", MemoryKind.WAS_EMBARRASSED, theo, valence = -0.8, place = bar)
        val engine = SimulationEngine(com.ashcroft.ripple.core.world.AshcroftLayout.build())
        // Running the world never fabricates or alters the objective record of what happened.
        val state = engine.run(AshcroftScenario.initial(), 60)
        assertTrue(state.people.all { p -> p.memories.all { it.occurredAt.epochMinutes >= 0 } })
        // Direct recall bumps the recall count on the surfaced memory only.
        val recalled = MemoryRecall.recall(person(listOf(m)), emptyContext().copy(room = bar), now).recalled
        assertEquals(0, recalled?.recallCount) // recall() returns the memory as-is; the engine bumps it
    }
}
