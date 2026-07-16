package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class ActionId(val value: String)

/**
 * A concrete behaviour a person can attempt. [effect] is the need-category the
 * behaviour relieves (reused by the needs model); [social] marks actions that
 * target another person and can be refused.
 */
@Serializable
enum class ActionVerb(val effect: ActivityKind, val social: Boolean) {
    WORK(ActivityKind.WORK, social = false),
    EAT(ActivityKind.EAT, social = false),
    SLEEP(ActivityKind.SLEEP, social = false),
    WASH(ActivityKind.WASH, social = false),
    RELAX(ActivityKind.RELAX, social = false),
    RETURN_HOME(ActivityKind.RELAX, social = false),
    TAKE_BREAK(ActivityKind.RELAX, social = false),
    SOCIALISE(ActivityKind.SOCIALISE, social = false),
    GREET(ActivityKind.SOCIALISE, social = true),
    CONVERSE(ActivityKind.SOCIALISE, social = true),
    WANDER(ActivityKind.IDLE, social = false),
    WAIT(ActivityKind.IDLE, social = false),

    // Phase 5 — attend to a concrete hotel task (check a guest in, clean a room,
    // serve at the bar). Counts as work, and often puts staff and guests together.
    ATTEND(ActivityKind.WORK, social = false),
}

/** The lifecycle a chosen action moves through. */
@Serializable
enum class ActionPhase {
    PROPOSED,
    ACCEPTED,
    TRAVELLING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    INTERRUPTED,
    ABANDONED,
    ;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == INTERRUPTED || this == ABANDONED
}

/**
 * The action a person is currently committed to, with its lifecycle phase. The
 * travel route lives on [LocationState]; this tracks intent, timing and the
 * one-line reason chosen for it.
 */
@Serializable
data class ActionState(
    val verb: ActionVerb,
    val targetRoom: RoomId?,
    val targetPerson: PersonId?,
    val phase: ActionPhase,
    val startedAt: SimTime,
    val plannedMinutes: Int,
    val elapsedMinutes: Int,
    val reasonSummary: String,
    val targetTaskId: HotelTaskId? = null,
) {
    fun signature(): ActionSignature = ActionSignature(verb, targetRoom, targetPerson)

    val isPerforming: Boolean get() = phase == ActionPhase.IN_PROGRESS

    companion object {
        val IDLE = ActionState(
            verb = ActionVerb.WAIT,
            targetRoom = null,
            targetPerson = null,
            phase = ActionPhase.COMPLETED,
            startedAt = SimTime.START,
            plannedMinutes = 0,
            elapsedMinutes = 0,
            reasonSummary = "Waiting",
        )
    }
}

/** A compact identity for an action, used for anti-repetition tracking. */
@Serializable
data class ActionSignature(
    val verb: ActionVerb,
    val room: RoomId?,
    val person: PersonId?,
)
