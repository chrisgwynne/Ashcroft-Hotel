package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class LifeStage { CHILD, ADOLESCENT, ADULT, ELDER }

/** Broad function a person serves in the hotel. Drives commitments and default haunts. */
@Serializable
enum class RoleKind {
    OWNER,
    GENERAL_MANAGER,
    DUTY_MANAGER,
    RECEPTIONIST,
    CONCIERGE,
    HOUSEKEEPER,
    CHEF,
    BARTENDER,
    GUEST,
    RESIDENT,
    ;

    val isStaff: Boolean
        get() = this != GUEST && this != RESIDENT
}

@Serializable
data class Identity(val name: String, val age: Int)

/**
 * A person in (or around) the hotel. By Phase 3 they carry enough interior
 * state to *decide*: needs, personality, a schedule of commitments, dynamic
 * goals, memories, a rolling behaviour history, a location and the concrete
 * action they are committed to (with its lifecycle phase). [lastDecision]
 * retains the structured reasoning behind the current action for the "Why?"
 * interface. This is deliberately not a scripted life story.
 */
@Serializable
data class Person(
    val id: PersonId,
    val identity: Identity,
    val lifeStage: LifeStage,
    val role: RoleKind,
    val personality: Personality,
    val needs: NeedState,
    /** The room a person returns to (staff bedroom or guest room), if any. */
    val homeRoom: RoomId?,
    val schedule: List<Commitment>,
    val goals: List<Goal>,
    val memories: List<Memory>,
    val acquaintances: Set<PersonId>,
    val behaviour: BehaviourHistory,
    val money: Int,
    val location: LocationState,
    val action: ActionState,
    val lastDecision: DecisionRecord?,
    /** What this person believes to be true — their non-omniscient picture of the hotel. */
    val knowledge: KnowledgeBase = KnowledgeBase.EMPTY,
    /** How they stand toward everyone they know, across every relationship dimension. */
    val relationships: Relationships = Relationships.EMPTY,
    /** Their current, fading emotional weather. */
    val emotions: EmotionState = EmotionState.CALM,
    /** A memory presently brought to mind by the current context, if any. */
    val recalledMemoryId: MemoryId? = null,
    /** The most recent conversational exchange this person took part in. */
    val lastConversation: ConversationRecord? = null,
    /**
     * A running tally of the kinds of things this person has been seen to do
     * (help, confront, avoid, praise…). It is the *evidence* from which readable
     * personality tendencies are inferred — never raw trait numbers.
     */
    val tendencyEvidence: Map<String, Int> = emptyMap(),
    /** For a guest, why they are here and how the stay is going; null for staff. */
    val stay: GuestStay? = null,
    /** Recent conversational contexts, used to damp repeating the same exchange. */
    val conversationLog: List<ConversationContextSignature> = emptyList(),
    /** This person's own, observer-specific reputations of everyone they know (Phase 7). */
    val standings: Standings = Standings.EMPTY,
) {
    /** How often this person has recently had a like-for-like exchange with [other]. */
    fun recentExchangesWith(other: PersonId): Int = conversationLog.count { it.recipientId == other && !it.taskDriven }

    val name: String get() = identity.name

    /**
     * How this person currently feels about [other], from remembered moments.
     * Kept as the memory-derived signal used since Phase 3; the multidimensional
     * [relationships] give the fuller, per-axis picture.
     */
    fun sentimentToward(other: PersonId): Double =
        memories.filter { it.subjectId == other }.sumOf { it.sentiment() }
}
