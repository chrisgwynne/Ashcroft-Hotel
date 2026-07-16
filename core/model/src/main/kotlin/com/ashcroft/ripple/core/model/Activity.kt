package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/** What a person is currently doing. TRAVEL means walking toward [Activity.targetRoom]. */
@Serializable
enum class ActivityKind {
    IDLE,
    TRAVEL,
    SLEEP,
    EAT,
    WORK,
    SOCIALISE,
    WASH,
    RELAX,
}

/** The kind of obligation a [Commitment] represents. */
@Serializable
enum class CommitmentKind {
    SHIFT,
    MEAL,
    APPOINTMENT,
    CHECKOUT,
}

/**
 * A soft, time-boxed obligation — a work shift, a booked treatment, a checkout
 * time. Commitments *pull* a person toward a place during a window; they are
 * pressures weighed against needs, not scripts that seize control. [strength]
 * scales how strongly the commitment competes.
 */
@Serializable
data class Commitment(
    val kind: CommitmentKind,
    val location: RoomId?,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val strength: Float,
) {
    fun isActiveAt(minuteOfDay: Int): Boolean =
        minuteOfDay in startMinuteOfDay until endMinuteOfDay
}
