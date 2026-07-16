package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.decision.WorldQueries
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind
import com.ashcroft.ripple.core.model.WorldPos

/**
 * Implements the decision engine's [WorldQueries] against the real hotel. It is
 * rebuilt each tick from the current [WorldState] snapshot, so "who is in this
 * room" reflects the live world — but the engine only ever asks about the
 * actor's own room, preserving non-omniscience. Access rules encode who may
 * enter where (staff-only back-of-house, guests confined to public areas and
 * their own room).
 */
class HotelWorldQueries(
    private val layout: HotelLayout,
    private val graph: NavGraph,
    private val locator: RoomLocator,
    people: List<Person>,
) : WorldQueries {
    private val peopleById: Map<PersonId, Person> = people.associateBy { it.id }
    private val byRoom: Map<RoomId, List<PersonId>> =
        people.groupBy { it.location.roomId }.mapNotNull { (room, ps) -> room?.let { it to ps.map { p -> p.id } } }.toMap()

    override fun roomKind(room: RoomId): RoomKind? = layout.room(room)?.kind

    override fun roomName(room: RoomId): String = layout.room(room)?.displayName ?: room.value

    override fun firstRoomOfKind(kind: RoomKind): RoomId? = locator.firstOfKind(kind)

    override fun canAccess(actor: Person, room: RoomId): Boolean {
        val kind = roomKind(room) ?: return false
        if (kind in STAFF_ONLY && !actor.role.isStaff) return false
        if (kind in GUEST_ROOMS && room != actor.homeRoom && !actor.role.isStaff) return false
        return true
    }

    override fun travelMinutes(from: WorldPos, room: RoomId): Int? {
        val access = graph.accessPos(room) ?: return null
        if (from == access) return 0
        val path = Pathfinder.findPath(graph, from, access)
        return if (path.isEmpty()) null else path.size
    }

    override fun peopleInRoom(room: RoomId): List<PersonId> = byRoom[room] ?: emptyList()

    override fun person(id: PersonId): Person? = peopleById[id]

    private companion object {
        val STAFF_ONLY = setOf(
            RoomKind.STAFF_ROOM,
            RoomKind.HOUSEKEEPING_STORE,
            RoomKind.KITCHEN,
            RoomKind.MANAGER_OFFICE,
        )
        val GUEST_ROOMS = setOf(
            RoomKind.GUEST_SINGLE,
            RoomKind.GUEST_DOUBLE,
            RoomKind.GUEST_TWIN,
            RoomKind.SUITE,
        )
    }
}
