package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CausalGraph
import com.ashcroft.ripple.core.model.ChronicleEntry
import com.ashcroft.ripple.core.model.HotelTask
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime
import kotlinx.serialization.Serializable

/**
 * An immutable snapshot of the living hotel at one instant: the seeded clock,
 * every person's full state, and the accumulated [chronicle] of outcomes that
 * mattered. Everything else (occupancy, who is where) is derived, so a
 * [WorldState] is the single source of truth a tick transforms. Being fully
 * serialisable, a snapshot can be saved, reloaded and replayed to the identical
 * future — the deterministic engine guarantees it.
 */
@Serializable
data class WorldState(
    val seed: Long,
    val clock: SimTime,
    val people: List<Person>,
    val chronicle: List<ChronicleEntry> = emptyList(),
    val tasks: List<HotelTask> = emptyList(),
    val causes: CausalGraph = CausalGraph(),
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
