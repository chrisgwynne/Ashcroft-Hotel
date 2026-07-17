package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * How a piece of information reached a person. Nobody in Ripple is omniscient:
 * everything a person holds to be true entered through one of these channels,
 * and the channel colours how much they trust it.
 */
@Serializable
enum class InformationSource {
    OBSERVED, // saw it with their own eyes — high confidence
    OVERHEARD, // caught it second-hand — lower confidence
    CONVERSATION, // was told directly by someone
    DOCUMENT, // read it (a ledger, a note)
    ANNOUNCEMENT, // told to everyone at once
    MEMORY, // recalled from their own past
    INFERENCE, // worked it out themselves
}

/**
 * What a claim is *about*. Topics are deliberately limited to things the
 * simulation can actually generate and later check against world truth, so a
 * belief can be found to be right or wrong. [key] gives every topic a stable
 * identity for storage and comparison.
 */
@Serializable
sealed interface FactTopic {
    val key: String

    /** Whether guest room / any room is currently occupied. */
    @Serializable
    data class RoomOccupancy(val room: RoomId) : FactTopic {
        override val key: String get() = "occupancy:${room.value}"
    }

    /** Where a particular person currently is. */
    @Serializable
    data class Whereabouts(val person: PersonId) : FactTopic {
        override val key: String get() = "where:${person.value}"
    }

    /** How a particular person seems to be feeling. */
    @Serializable
    data class PersonMood(val person: PersonId) : FactTopic {
        override val key: String get() = "mood:${person.value}"
    }

    /** Whether someone worth noticing is staying at the hotel. */
    @Serializable
    data class NotableGuest(val person: PersonId) : FactTopic {
        override val key: String get() = "notable:${person.value}"
    }
}

/**
 * A concrete assertion about a topic — the topic plus the specific value being
 * asserted (a room id, "occupied"/"empty", a mood word). Two people can hold
 * different claims about the same topic; at most one matches world truth.
 */
@Serializable
data class Claim(val topic: FactTopic, val value: String)

/**
 * One thing a person holds to be true. A belief is never "truth" — it is this
 * person's current picture, which may be out of date or simply wrong.
 * [confidence] runs 0.0 (barely a hunch) to 1.0 (certain); a low-confidence
 * belief that arrived second-hand is what we call a *rumour*.
 */
@Serializable
data class Belief(
    val claim: Claim,
    val confidence: Double,
    val source: InformationSource,
    val acquiredAt: SimTime,
    val fromPerson: PersonId? = null,
    /** The causal record of how this belief was acquired, if tracked. */
    val causeId: CauseId? = null,
) {
    val topicKey: String get() = claim.topic.key

    /** Second-hand and uncertain: heard from someone, held loosely. */
    val isRumour: Boolean
        get() = (source == InformationSource.OVERHEARD || source == InformationSource.CONVERSATION) &&
            confidence < RUMOUR_CEILING

    companion object {
        const val RUMOUR_CEILING = 0.6
    }
}

/**
 * Everything a person currently believes, keyed by topic so a newer or more
 * confident belief supersedes an older one. This is their *personal knowledge*
 * — strictly a subset of, and sometimes at odds with, world truth. Immutable:
 * every change yields a new instance so ticks stay pure and replayable.
 */
@Serializable
data class KnowledgeBase(private val beliefs: Map<String, Belief> = emptyMap()) {
    val all: Collection<Belief> get() = beliefs.values

    fun about(topic: FactTopic): Belief? = beliefs[topic.key]

    fun knows(topic: FactTopic): Boolean = beliefs.containsKey(topic.key)

    /**
     * Take on a belief. A fresh belief replaces an existing one on the same
     * topic unless the existing belief is held more confidently — so people
     * update their picture as they learn more (a re-sighting refreshes a faded
     * memory), but a confident first-hand sighting is never overwritten by a
     * vaguer bit of hearsay.
     */
    fun learn(belief: Belief): KnowledgeBase {
        val existing = beliefs[belief.topicKey]
        val keepExisting = existing != null && existing.confidence > belief.confidence
        return if (keepExisting) this else KnowledgeBase(beliefs + (belief.topicKey to belief))
    }

    /** Confidence fades a little as information ages and goes unconfirmed. */
    fun weathered(factor: Double): KnowledgeBase =
        KnowledgeBase(beliefs.mapValues { (_, b) -> b.copy(confidence = (b.confidence * factor).coerceIn(0.0, 1.0)) })

    /** Attach a causal record to the belief currently held on [topic] (no-op if none is held). */
    fun stamp(topic: FactTopic, causeId: CauseId): KnowledgeBase {
        val existing = beliefs[topic.key] ?: return this
        return KnowledgeBase(beliefs + (topic.key to existing.copy(causeId = causeId)))
    }

    companion object {
        val EMPTY = KnowledgeBase()
    }
}
