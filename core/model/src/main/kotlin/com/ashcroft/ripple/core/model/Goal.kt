package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/** A lightweight cause handle. The full causal graph arrives in Phase 6. */
@Serializable
@JvmInline
value class CauseId(val value: String)

@Serializable
@JvmInline
value class GoalId(val value: String)

/**
 * The kinds of desired future state a person can hold. These are ordinary
 * hotel-life goals — deliberately no marriage, crime or other dramatic arcs.
 * Goals arise from current state; they are never assigned because the engine
 * wants a plot. [ADVANCE_CAREER] (Phase 7F) is a long-arc *aspiration*, not a
 * scripted promotion: it biases an ambitious, accomplished person toward growth,
 * but nothing about it ever changes their role by fiat.
 */
@Serializable
enum class GoalType {
    SATISFY_NEED,
    FULFIL_WORK,
    PRESERVE_RELATIONSHIP,
    GAIN_APPROVAL,
    AVOID_DISCOMFORT,
    SEEK_PRIVACY,
    IMPROVE_COMPETENCE,
    SAVE_RESOURCES,
    COMPLETE_STAY,
    ADVANCE_CAREER,
}

/** What a goal is oriented toward. */
@Serializable
sealed interface GoalTarget {
    @Serializable
    data class Need(val kind: NeedKind) : GoalTarget

    @Serializable
    data class Person(val id: PersonId) : GoalTarget

    @Serializable
    data class Place(val room: RoomId) : GoalTarget

    @Serializable
    data object None : GoalTarget
}

@Serializable
enum class GoalStatus { ACTIVE, COMPLETED, FAILED, ABANDONED, BLOCKED }

/**
 * A desired future state. Priority and commitment change over time; goals can
 * conflict, complete, fail, be abandoned or become temporarily blocked. A goal
 * records the causes that gave rise to it so later phases can trace it.
 */
@Serializable
data class Goal(
    val id: GoalId,
    val ownerId: PersonId,
    val type: GoalType,
    val target: GoalTarget,
    val priority: Double,
    val commitment: Double,
    val originCauseIds: Set<CauseId>,
    val createdAt: SimTime,
    val status: GoalStatus,
)
