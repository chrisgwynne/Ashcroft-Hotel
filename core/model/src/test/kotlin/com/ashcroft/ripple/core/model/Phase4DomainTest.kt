package com.ashcroft.ripple.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4DomainTest {
    private val t0 = SimTime(0)
    private val alice = PersonId("alice")
    private val room214 = RoomId("room_214")

    @Test
    fun knowledgeKeepsTheMoreConfidentBeliefOnATopic() {
        val topic = FactTopic.RoomOccupancy(room214)
        val vague = Belief(Claim(topic, "occupied"), confidence = 0.3, source = InformationSource.OVERHEARD, acquiredAt = t0)
        val seen = Belief(Claim(topic, "empty"), confidence = 0.95, source = InformationSource.OBSERVED, acquiredAt = t0)

        val kb = KnowledgeBase.EMPTY.learn(vague).learn(seen)

        assertEquals("empty", kb.about(topic)?.claim?.value)
        // A vaguer, older rumour does not overwrite a confident sighting.
        assertEquals("empty", kb.learn(vague).about(topic)?.claim?.value)
    }

    @Test
    fun aSecondHandUncertainBeliefIsARumour() {
        val heard = Belief(
            Claim(FactTopic.NotableGuest(alice), "notable"),
            confidence = 0.4,
            source = InformationSource.CONVERSATION,
            acquiredAt = t0,
        )
        val seen = heard.copy(confidence = 0.95, source = InformationSource.OBSERVED)
        assertTrue(heard.isRumour)
        assertFalse(seen.isRumour)
    }

    @Test
    fun confidenceWeathersButBeliefsRemain() {
        val topic = FactTopic.Whereabouts(alice)
        val kb = KnowledgeBase.EMPTY.learn(
            Belief(Claim(topic, "bar"), 0.9, InformationSource.OBSERVED, t0),
        ).weathered(0.5)
        assertNotNull(kb.about(topic))
        assertEquals(0.45, kb.about(topic)!!.confidence, 1e-9)
    }

    @Test
    fun relationshipDimensionsMoveIndependently() {
        val r = Relationship(alice)
            .adjusted(mapOf(RelationDimension.RESPECT to 0.6, RelationDimension.AFFECTION to -0.2))
        assertEquals(0.6, r[RelationDimension.RESPECT], 1e-9)
        assertEquals(-0.2, r[RelationDimension.AFFECTION], 1e-9)
        // Untouched axes stay neutral — never a single collapsed score.
        assertEquals(0.0, r[RelationDimension.TRUST], 1e-9)
    }

    @Test
    fun relationshipsClampAndStartAsStrangers() {
        assertTrue(Relationships.EMPTY.with(alice).isStranger)
        val r = Relationship(alice).adjusted(mapOf(RelationDimension.TRUST to 5.0))
        assertEquals(1.0, r[RelationDimension.TRUST], 1e-9)
    }

    @Test
    fun emotionsFadeTowardCalm() {
        val stirred = EmotionState.CALM.stirred(mapOf(EmotionKind.EXCITEMENT to 0.9))
        val later = stirred.decayed(600)
        assertTrue(later[EmotionKind.EXCITEMENT] < stirred[EmotionKind.EXCITEMENT])
        assertTrue(later[EmotionKind.EXCITEMENT] >= 0.0)
    }

    @Test
    fun strongestEmotionIsReported() {
        val e = EmotionState.CALM.stirred(mapOf(EmotionKind.ANXIETY to 0.7, EmotionKind.HAPPINESS to 0.3))
        assertEquals(EmotionKind.ANXIETY, e.strongest)
        assertNull(EmotionState.CALM.strongest)
    }

    @Test
    fun recalledMemoryIsMoreSalientThanADormantOne() {
        val base = Memory(
            id = MemoryId("m1"),
            ownerId = alice,
            occurredAt = t0,
            kind = MemoryKind.WAS_HELPED,
            subjectId = PersonId("bob"),
            valence = 0.8,
            importance = 0.7,
        )
        val now = SimTime(10_000)
        val recalled = base.recalled(now)
        assertEquals(1, recalled.recallCount)
        assertTrue(recalled.salience(now) > base.salience(now))
    }
}
