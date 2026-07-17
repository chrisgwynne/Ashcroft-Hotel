package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.BehaviourHistory
import com.ashcroft.ripple.core.model.Belief
import com.ashcroft.ripple.core.model.Claim
import com.ashcroft.ripple.core.model.ConversationAct
import com.ashcroft.ripple.core.model.ConversationReception
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.FactTopic
import com.ashcroft.ripple.core.model.Goal
import com.ashcroft.ripple.core.model.GoalId
import com.ashcroft.ripple.core.model.GoalStatus
import com.ashcroft.ripple.core.model.GoalTarget
import com.ashcroft.ripple.core.model.GoalType
import com.ashcroft.ripple.core.model.GridCell
import com.ashcroft.ripple.core.model.Identity
import com.ashcroft.ripple.core.model.InformationSource
import com.ashcroft.ripple.core.model.KnowledgeBase
import com.ashcroft.ripple.core.model.LifeStage
import com.ashcroft.ripple.core.model.LocationState
import com.ashcroft.ripple.core.model.Memory
import com.ashcroft.ripple.core.model.MemoryKind
import com.ashcroft.ripple.core.model.NeedState
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.Personality
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.TraitKind
import com.ashcroft.ripple.core.model.WorldPos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4ConversationTest {
    private val now = SimTime(3_000)
    private val bar = RoomId("bar")
    private val absent = PersonId("george")

    private fun p(
        id: String,
        personality: Personality = Personality.of(),
        needsSocial: Float = 0.5f,
        role: RoleKind = RoleKind.RECEPTIONIST,
        memories: List<Memory> = emptyList(),
        knowledge: KnowledgeBase = KnowledgeBase.EMPTY,
        goals: List<Goal> = emptyList(),
        emotions: com.ashcroft.ripple.core.model.EmotionState = com.ashcroft.ripple.core.model.EmotionState.CALM,
        relationships: com.ashcroft.ripple.core.model.Relationships = com.ashcroft.ripple.core.model.Relationships.EMPTY,
    ) = Person(
        id = PersonId(id),
        identity = Identity(id.replaceFirstChar { it.uppercase() }, 30),
        lifeStage = LifeStage.ADULT,
        role = role,
        personality = personality,
        needs = NeedState.of(com.ashcroft.ripple.core.model.NeedKind.SOCIAL to needsSocial),
        homeRoom = RoomId("staff_room"),
        schedule = emptyList(),
        goals = goals,
        memories = memories,
        acquaintances = emptySet(),
        behaviour = BehaviourHistory(),
        money = 100,
        location = LocationState(WorldPos(0, GridCell(1, 1)), bar, emptyList()),
        action = ActionState.IDLE,
        lastDecision = null,
        knowledge = knowledge,
        emotions = emotions,
        relationships = relationships,
    )

    private fun withNews(id: String) = p(
        id,
        knowledge = KnowledgeBase.EMPTY.learn(
            Belief(Claim(FactTopic.NotableGuest(absent), "notable"), 0.9, InformationSource.OBSERVED, now),
        ),
    )

    private fun converse(actor: Person, target: Person, roll: Float) =
        ConversationSystem.converse(actor, target, setOf(actor.id, target.id), roll, now)

    @Test
    fun aWillingRecipientAcceptsAndAReluctantOneRefuses() {
        val actor = p("maya")
        val eager = p("arthur", Personality.of(TraitKind.SOCIABILITY to 0.9f), needsSocial = 0.2f)
        val (_, acceptedTarget) = converse(actor, eager, roll = 0.3f)
        assertEquals(ConversationReception.ACCEPTED, acceptedTarget.lastConversation?.reception)

        val reluctant = p("theo", Personality.of(TraitKind.SOCIABILITY to 0.1f), needsSocial = 0.95f)
        val (refusedActor, _) = converse(actor, reluctant, roll = 0.99f)
        assertEquals(ConversationReception.REFUSED, refusedActor.lastConversation?.reception)
    }

    @Test
    fun givingInformationTransfersABeliefAsRumour() {
        val actor = withNews("maya")
        val target = p("arthur", Personality.of(TraitKind.SOCIABILITY to 0.9f), needsSocial = 0.2f)
        val (a, t) = converse(actor, target, roll = 0.4f)
        assertEquals(ConversationAct.GIVE_INFORMATION, a.lastConversation?.act)
        val learned = t.knowledge.about(FactTopic.NotableGuest(absent))
        assertNotEquals(null, learned)
        assertTrue("passed-on information is second-hand", learned!!.source == InformationSource.CONVERSATION)
    }

    @Test
    fun misunderstoodInformationArrivesGarbled() {
        val actor = withNews("maya")
        val target = p("arthur", Personality.of(TraitKind.SOCIABILITY to 0.9f), needsSocial = 0.1f)
        // A very low roll lands in the misunderstanding band for information-bearing acts.
        val (_, t) = converse(actor, target, roll = 0.02f)
        assertEquals(ConversationReception.MISUNDERSTOOD, t.lastConversation?.reception)
        val learned = t.knowledge.about(FactTopic.NotableGuest(absent))!!
        assertTrue("a misunderstanding is held even more loosely", learned.confidence < 0.5)
    }

    @Test
    fun anApologyCanBeRefused() {
        val guilty = p("theo", emotions = com.ashcroft.ripple.core.model.EmotionState.CALM.stirred(mapOf(EmotionKind.GUILT to 0.6)))
        val resentful = p(
            "maya",
            relationships = com.ashcroft.ripple.core.model.Relationships.EMPTY.adjust(
                PersonId("theo"),
                mapOf(RelationDimension.RESENTMENT to 0.8),
            ),
        )
        val (actorAfter, _) = converse(guilty, resentful, roll = 0.95f)
        assertEquals(ConversationAct.APOLOGISE, actorAfter.lastConversation?.act)
        assertEquals(ConversationReception.REFUSED, actorAfter.lastConversation?.reception)
        assertTrue("a rebuffed apology stings", actorAfter.emotions[EmotionKind.EMBARRASSMENT] > 0.0)
    }

    @Test
    fun offeredHelpWhenAcceptedCreatesGratitude() {
        val helper = p(
            "arthur",
            Personality.of(TraitKind.AGREEABLENESS to 0.9f),
            knowledge = KnowledgeBase.EMPTY.learn(
                Belief(Claim(FactTopic.PersonMood(PersonId("theo")), "tired"), 0.8, InformationSource.OBSERVED, now),
            ),
        )
        val stressed = p("theo", Personality.of(TraitKind.SOCIABILITY to 0.8f), needsSocial = 0.3f)
        val (_, t) = converse(helper, stressed, roll = 0.2f)
        assertEquals(ConversationAct.OFFER_HELP, helper.let { converse(it, stressed, 0.2f).first.lastConversation?.act })
        assertTrue("being helped is remembered warmly", t.memories.any { it.kind == MemoryKind.WAS_HELPED })
        assertTrue("and breeds gratitude", t.relationships.with(helper.id)[RelationDimension.GRATITUDE] > 0.0)
    }

    @Test
    fun personalityChangesWhetherARequestIsGranted() {
        val asker = p(
            "maya",
            goals = listOf(goal()),
            relationships = com.ashcroft.ripple.core.model.Relationships.EMPTY.adjust(
                PersonId("arthur"),
                mapOf(RelationDimension.TRUST to 0.3),
            ),
        )
        val agreeable = p("arthur", Personality.of(TraitKind.AGREEABLENESS to 0.95f, TraitKind.SOCIABILITY to 0.7f), needsSocial = 0.3f)
        val prickly = p("arthur", Personality.of(TraitKind.AGREEABLENESS to 0.05f, TraitKind.SOCIABILITY to 0.2f), needsSocial = 0.9f)
        val roll = 0.55f
        val grantedByAgreeable = converse(asker, agreeable, roll).first.lastConversation?.reception
        val grantedByPrickly = converse(asker, prickly, roll).first.lastConversation?.reception
        assertNotEquals("temperament should change the outcome", grantedByAgreeable, grantedByPrickly)
    }

    @Test
    fun acceptedGreetingWarmsBothSides() {
        val a = p("maya")
        val b = p("arthur", Personality.of(TraitKind.SOCIABILITY to 0.8f), needsSocial = 0.3f)
        val (aa, bb) = converse(a, b, roll = 0.3f)
        assertEquals(ConversationAct.GREET, aa.lastConversation?.act)
        assertTrue(aa.relationships.with(b.id)[RelationDimension.FAMILIARITY] > 0.0)
        assertTrue(bb.relationships.with(a.id)[RelationDimension.FAMILIARITY] > 0.0)
    }

    @Test
    fun conversationOutcomesAreDeterministic() {
        val a = withNews("maya")
        val b = p("arthur", Personality.of(TraitKind.SOCIABILITY to 0.7f), needsSocial = 0.4f)
        assertEquals(converse(a, b, 0.5f), converse(a, b, 0.5f))
    }

    private fun goal() = Goal(
        id = GoalId("g1"),
        ownerId = PersonId("maya"),
        type = GoalType.SATISFY_NEED,
        target = GoalTarget.Need(com.ashcroft.ripple.core.model.NeedKind.HUNGER),
        priority = 0.8,
        commitment = 0.5,
        originCauseIds = emptySet(),
        createdAt = now,
        status = GoalStatus.ACTIVE,
    )
}
