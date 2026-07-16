package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind
import com.ashcroft.ripple.core.model.WorldPos

/**
 * The narrow window the decision engine has onto the world. Keeping it an
 * interface means the engine never depends on rendering, pathfinding or the
 * concrete hotel — and, crucially, it exposes only what a person could plausibly
 * come to know (room layout, who is *in the same room*), not omniscient global
 * state. The simulation module implements it.
 */
interface WorldQueries {
    fun roomKind(room: RoomId): RoomKind?

    fun roomName(room: RoomId): String

    fun firstRoomOfKind(kind: RoomKind): RoomId?

    /** Whether [actor] is permitted to enter [room]. */
    fun canAccess(actor: Person, room: RoomId): Boolean

    /** Estimated walking time to [room], or null if unreachable. */
    fun travelMinutes(from: WorldPos, room: RoomId): Int?

    /** The ids of people currently standing in [room] (used only for the actor's own room). */
    fun peopleInRoom(room: RoomId): List<PersonId>

    /** Read another person — the engine only ever copies public attributes (name/role). */
    fun person(id: PersonId): Person?
}
