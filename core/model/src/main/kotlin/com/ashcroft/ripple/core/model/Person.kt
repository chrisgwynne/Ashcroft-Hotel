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
 * A person in (or around) the hotel. Phase 2 gives them enough interior state
 * to move about with purpose — needs, personality, a schedule of commitments,
 * a current activity and a location. Memories, goals, relationships and the
 * decision engine arrive in later phases; this is deliberately not a scripted
 * life story.
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
    val location: LocationState,
    val currentActivity: Activity,
) {
    val name: String get() = identity.name
}
