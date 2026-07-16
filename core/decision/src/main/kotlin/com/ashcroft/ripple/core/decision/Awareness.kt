package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.RoleKind

/**
 * Whether one person is actually *aware* enough of another to think of
 * approaching them. Sharing a room is not enough: a chef bent over the pass
 * barely notices casual company, while an idle colleague notices everyone; a
 * receptionist is primed to notice a waiting guest; and a familiar face draws
 * the eye where a stranger does not.
 *
 * This is a deterministic zone-and-attention model — no randomness, no
 * pixel-perfect vision. It gates which co-present people become candidates for a
 * social approach, so contact still requires attention and context to align.
 */
internal object Awareness {
    const val AWARE_FLOOR = 0.5

    fun of(actor: Person, otherRole: RoleKind, alreadyKnown: Boolean, crowding: Int): Double {
        var score = attentionFor(actor)
        if (alreadyKnown) score += FAMILIAR
        // Staff are primed to notice guests they might serve; guests notice front-of-house staff.
        if (actor.role.isStaff && otherRole == RoleKind.GUEST) score += ROLE_ATTENTION
        if (!actor.role.isStaff && otherRole.isStaff) score += ROLE_ATTENTION
        // A crowd offers more chances to catch someone's eye, but only mildly.
        if (crowding >= CROWD) score += CROWD_BONUS
        return score.coerceIn(0.0, 1.0)
    }

    fun notices(actor: Person, otherRole: RoleKind, alreadyKnown: Boolean, crowding: Int): Boolean =
        of(actor, otherRole, alreadyKnown, crowding) >= AWARE_FLOOR

    /** How much spare attention the actor has, from what they are doing. */
    private fun attentionFor(actor: Person): Double = when {
        !actor.action.isPerforming -> IDLE_ATTENTION
        actor.action.verb == ActionVerb.WORK || actor.action.verb == ActionVerb.ATTEND -> FOCUSED_ATTENTION
        actor.action.verb.social || actor.action.verb == ActionVerb.SOCIALISE -> SOCIAL_ATTENTION
        else -> BASE_ATTENTION
    }

    private const val IDLE_ATTENTION = 0.7
    private const val SOCIAL_ATTENTION = 0.75
    private const val BASE_ATTENTION = 0.55
    private const val FOCUSED_ATTENTION = 0.3
    private const val FAMILIAR = 0.25
    private const val ROLE_ATTENTION = 0.2
    private const val CROWD = 4
    private const val CROWD_BONUS = 0.1
}
