package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime

/**
 * An immutable snapshot of the living hotel at one instant: the seeded clock
 * and every person's full state. Everything else (occupancy, who is where) is
 * derived, so a [WorldState] is the single source of truth a tick transforms.
 */
data class WorldState(
    val seed: Long,
    val clock: SimTime,
    val people: List<Person>,
) {
    fun person(id: PersonId): Person? = people.firstOrNull { it.id == id }

    /** Who is currently standing in each room. */
    fun occupancy(): Map<RoomId, List<PersonId>> {
        val map = LinkedHashMap<RoomId, MutableList<PersonId>>()
        for (p in people) {
            val room = p.location.roomId ?: continue
            map.getOrPut(room) { mutableListOf() }.add(p.id)
        }
        return map
    }
}
