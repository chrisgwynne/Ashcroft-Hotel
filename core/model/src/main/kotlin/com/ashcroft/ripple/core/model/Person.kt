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
) {
    val name: String get() = identity.name

    /** How this person currently feels about [other], from remembered moments. */
    fun sentimentToward(other: PersonId): Double =
        memories.filter { it.subjectId == other }.sumOf { it.sentiment() }
}
