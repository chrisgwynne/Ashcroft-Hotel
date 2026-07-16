package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Belief
import com.ashcroft.ripple.core.model.FactTopic
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId

/**
 * The objective simulation, read as claims. There is no separate "truth store":
 * truth *is* the [WorldState]. This object answers what is actually the case for
 * a topic so that a person's [Belief] can be judged right, wrong or simply
 * unknown — which is what makes false beliefs and rumour measurable.
 */
object WorldTruth {
    fun occupancyOf(state: WorldState, room: RoomId): String =
        if (state.people.count { it.location.roomId == room } > 1) "occupied" else "empty"

    fun whereaboutsOf(state: WorldState, person: PersonId): String? =
        state.person(person)?.location?.roomId?.value

    /** The true value for a topic, or null if the simulation has no answer for it. */
    fun valueFor(state: WorldState, topic: FactTopic): String? = when (topic) {
        is FactTopic.RoomOccupancy -> occupancyOf(state, topic.room)
        is FactTopic.Whereabouts -> whereaboutsOf(state, topic.person)
        is FactTopic.NotableGuest -> if (state.person(topic.person)?.let(Perception::isNotable) == true) "notable" else "ordinary"
        is FactTopic.PersonMood -> state.person(topic.person)?.let(Perception::moodWord)
    }

    /** Whether [belief] contradicts what is actually the case right now. */
    fun isFalse(state: WorldState, belief: Belief): Boolean {
        val truth = valueFor(state, belief.claim.topic) ?: return false
        return truth != belief.claim.value
    }

    /** Everyone's currently-held beliefs that are out of step with the world. */
    fun falseBeliefs(state: WorldState): List<Pair<PersonId, Belief>> =
        state.people.flatMap { person -> person.knowledge.all.filter { isFalse(state, it) }.map { person.id to it } }
}
