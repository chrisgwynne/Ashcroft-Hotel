package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.BehaviourHistory
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.EmotionState
import com.ashcroft.ripple.core.model.GridCell
import com.ashcroft.ripple.core.model.Identity
import com.ashcroft.ripple.core.model.LifeStage
import com.ashcroft.ripple.core.model.LocationState
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.NeedState
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.Personality
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.Relationships
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.TraitKind
import com.ashcroft.ripple.core.model.WorldPos
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase5DiversityTest {
    private val now = SimTime(2_000)

    private fun person(
        id: String,
        personality: Personality,
        social: Float = 0.6f,
        emotions: EmotionState = EmotionState.CALM,
        relationships: Relationships = Relationships.EMPTY,
    ) = Person(
        id = PersonId(id),
        identity = Identity(id, 30),
        lifeStage = LifeStage.ADULT,
        role = RoleKind.RECEPTIONIST,
        personality = personality,
        needs = NeedState.of(NeedKind.SOCIAL to social),
        homeRoom = RoomId("staff_room"),
        schedule = emptyList(),
        goals = emptyList(),
        memories = emptyList(),
        acquaintances = emptySet(),
        behaviour = BehaviourHistory(),
        money = 100,
        location = LocationState(WorldPos(0, GridCell(1, 1)), RoomId("bar"), emptyList()),
        action = ActionState.IDLE,
        lastDecision = null,
        emotions = emotions,
        relationships = relationships,
    )

    @Test
    fun temperamentPullsFeelingInDifferentDirections() {
        var extravert = person("ext", Personality.of(TraitKind.EXTRAVERSION to 0.9f), social = 0.9f)
        var impatient = person("imp", Personality.of(TraitKind.PATIENCE to 0.1f, TraitKind.EXTRAVERSION to 0.2f))
        repeat(200) {
            extravert = extravert.copy(emotions = EmotionDynamics.tick(extravert))
            impatient = impatient.copy(emotions = EmotionDynamics.tick(impatient))
        }
        assertNotEquals(
            "different temperaments settle on different feelings",
            extravert.emotions.strongest,
            impatient.emotions.strongest,
        )
        assertTrue("the outgoing one warms toward happiness", extravert.emotions[EmotionKind.HAPPINESS] > 0.3)
        assertTrue("the impatient one simmers with frustration", impatient.emotions[EmotionKind.FRUSTRATION] > 0.3)
    }

    @Test
    fun praiseDoesNotWarmEveryAxisTheSameWay() {
        // The same praise breeds warmth in the agreeable and a flicker of suspicion in the guarded.
        val actor = person(
            "maya",
            Personality.of(),
            emotions = EmotionState.CALM.stirred(mapOf(EmotionKind.HAPPINESS to 0.5)),
            relationships = Relationships.EMPTY.adjust(PersonId("guarded"), mapOf(RelationDimension.AFFECTION to 0.5)),
        )
        val guarded = person("guarded", Personality.of(TraitKind.AGREEABLENESS to 0.1f), social = 0.3f)
        val (_, praised) = ConversationSystem.converse(actor, guarded, setOf(actor.id, guarded.id), roll = 0.2f, now = now)
        val rel = praised.relationships.with(actor.id)
        assertTrue("a guarded person meets praise with some suspicion", rel[RelationDimension.RESENTMENT] > 0.0)
    }

    @Test
    fun warmthSaturatesSoBondsDoNotAllMaxOut() {
        // Repeated pleasant contact should not drive affection straight to the ceiling.
        val engine = SimulationEngine(AshcroftLayout.build())
        val state = engine.run(AshcroftScenario.initial(), 7 * 24 * 60)
        val affections = state.people.flatMap { it.relationships.all.map { r -> r[RelationDimension.AFFECTION] } }
        assertTrue("some bonds should form", affections.any { it > 0.1 })
        assertTrue("but not everyone should be maxed out with everyone", affections.count { it > 0.95 } < affections.size / 2)
    }

    @Test
    fun noSinglePairMonopolisesConversation() {
        val engine = SimulationEngine(AshcroftLayout.build())
        var state = AshcroftScenario.initial()
        val pairCounts = HashMap<String, Int>()
        val lastAt = HashMap<String, Long>()
        var total = 0
        repeat(7 * 24 * 60) {
            state = engine.step(state)
            for (p in state.people) {
                val c = p.lastConversation ?: continue
                if (c.initiatedByMe && lastAt[p.id.value] != c.at.epochMinutes) {
                    lastAt[p.id.value] = c.at.epochMinutes
                    total++
                    val key = listOf(p.id.value, c.withPerson.value).sorted().joinToString("-")
                    pairCounts.merge(key, 1, Int::plus)
                }
            }
        }
        val topShare = (pairCounts.values.maxOrNull() ?: 0).toDouble() / total.coerceAtLeast(1)
        assertTrue("no one pair should dominate the hotel's conversation ($topShare)", topShare < 0.5)
    }
}
