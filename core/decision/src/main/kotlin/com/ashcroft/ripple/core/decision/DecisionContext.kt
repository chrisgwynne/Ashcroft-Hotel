package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionCandidate
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.Commitment
import com.ashcroft.ripple.core.model.Goal
import com.ashcroft.ripple.core.model.Memory
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime

/**
 * A person the actor can actually perceive — only those in the same room. It
 * carries public attributes plus the actor's *own* remembered feeling toward
 * them. It deliberately does NOT expose the other person's needs, goals or
 * intentions: a decider is not omniscient about anyone else's interior.
 */
data class PerceivedPerson(
    val id: PersonId,
    val name: String,
    val role: RoleKind,
    val sentiment: Double,
    val alreadyKnown: Boolean,
)

/** Something the actor knows they could do somewhere, from role and familiar areas. */
data class KnownOpportunity(
    val verb: ActionVerb,
    val room: RoomId,
    val label: String,
)

/**
 * Everything a single decision is made from — the actor's perceived world, not
 * the true world. A decider may be wrong (e.g. expect someone to be receptive);
 * incorrect beliefs surface as failed or revised actions, never silent
 * omniscient correction.
 */
data class DecisionContext(
    val actor: Person,
    val currentRoom: RoomId?,
    val perceivedPeople: List<PerceivedPerson>,
    val knownOpportunities: List<KnownOpportunity>,
    val activeGoals: List<Goal>,
    val activeCommitments: List<Commitment>,
    val recentMemories: List<Memory>,
    val availableActions: List<ActionCandidate>,
    val simTime: SimTime,
)
