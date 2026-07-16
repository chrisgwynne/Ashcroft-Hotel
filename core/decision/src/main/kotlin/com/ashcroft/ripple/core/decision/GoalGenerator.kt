package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.Commitment
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.Goal
import com.ashcroft.ripple.core.model.GoalId
import com.ashcroft.ripple.core.model.GoalStatus
import com.ashcroft.ripple.core.model.GoalTarget
import com.ashcroft.ripple.core.model.GoalType
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.TraitKind

/**
 * Derives a person's current goals from their present state — never from a
 * desired plot. A depleting need becomes a "satisfy need" goal; an active shift
 * becomes a "fulfil work" goal; ambition raises a quiet "gain approval" goal;
 * guests carry a "complete stay" goal. Priorities are continuous, so goals
 * naturally rise, fall and compete as state changes.
 */
class GoalGenerator {
    fun generate(actor: Person, now: SimTime, activeCommitments: List<Commitment>): List<Goal> {
        val goals = mutableListOf<Goal>()

        for (need in NeedKind.entries) {
            val deficit = 1f - actor.needs[need]
            if (deficit < NEED_GOAL_FLOOR) continue
            goals += goal(actor, now, GoalType.SATISFY_NEED, GoalTarget.Need(need), priority = deficit.toDouble())
        }

        activeCommitments.firstOrNull { it.kind == CommitmentKind.SHIFT }?.let { shift ->
            val conscientiousness = actor.personality[TraitKind.CONSCIENTIOUSNESS]
            goals += goal(
                actor, now, GoalType.FULFIL_WORK, GoalTarget.None,
                priority = (shift.strength * 0.9f + conscientiousness * 0.3f).toDouble(),
            )
        }

        if (actor.role.isStaff) {
            val ambition = actor.personality[TraitKind.AMBITION]
            goals += goal(actor, now, GoalType.GAIN_APPROVAL, GoalTarget.None, priority = (0.2f + ambition * 0.4f).toDouble())
        } else {
            goals += goal(actor, now, GoalType.COMPLETE_STAY, GoalTarget.None, priority = 0.35)
        }

        return goals
    }

    private fun goal(actor: Person, now: SimTime, type: GoalType, target: GoalTarget, priority: Double): Goal = Goal(
        id = GoalId("g:${actor.id.value}:$type:${targetKey(target)}"),
        ownerId = actor.id,
        type = type,
        target = target,
        priority = priority.coerceIn(0.0, 1.0),
        commitment = 0.5,
        originCauseIds = emptySet(),
        createdAt = now,
        status = GoalStatus.ACTIVE,
    )

    private fun targetKey(target: GoalTarget): String = when (target) {
        is GoalTarget.Need -> target.kind.name
        is GoalTarget.Person -> target.id.value
        is GoalTarget.Place -> target.room.value
        GoalTarget.None -> "-"
    }

    private companion object {
        const val NEED_GOAL_FLOOR = 0.35f
    }
}
