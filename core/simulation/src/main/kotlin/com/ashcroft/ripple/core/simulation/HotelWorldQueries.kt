package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.decision.CultureLens
import com.ashcroft.ripple.core.decision.TaskOffer
import com.ashcroft.ripple.core.decision.WorldQueries
import com.ashcroft.ripple.core.model.CultureRegistry
import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.EvidenceDimension
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.HotelTask
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoleKind
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
    private val tasks: List<HotelTask> = emptyList(),
    private val culture: CultureRegistry = CultureRegistry.EMPTY,
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

    override fun openTasksFor(actor: Person): List<TaskOffer> {
        if (!actor.role.isStaff) return emptyList()
        return tasks.asSequence()
            .filter { it.isOpen && actor.role in it.requiredRoles && (it.assignedTo == null || it.assignedTo == actor.id) }
            .filter { canAccess(actor, it.locationId) && travelMinutes(actor.location.pos, it.locationId) != null }
            .map { TaskOffer(it.id, it.locationId, taskLabel(it.type), it.priority, it.type.guestFacing, it.department) }
            .toList()
    }

    /**
     * The character pressing on this actor: their own department's culture, weighted
     * fully, blended with the hotel's, weighted less. Each trait is read as its mean
     * scaled by confidence, so an unformed culture pulls at nothing and a long-settled
     * one pulls in proportion to how sure of itself it has become. Guests and residents
     * feel only the hotel; staff feel their department more sharply.
     */
    override fun cultureFor(actor: Person): CultureLens {
        val hotel = culture.of(EntityId.HOTEL)
        val department = departmentOf(actor.role)?.let { culture.of(EntityId.department(it)) }
        if (hotel == null && department == null) return CultureLens.NONE
        val blended = HashMap<EvidenceDimension, Double>()

        fun fold(profile: com.ashcroft.ripple.core.model.CultureProfile?, weight: Double) {
            profile ?: return
            for ((dimension, standing) in profile.traits) {
                blended[dimension] = (blended[dimension] ?: 0.0) + standing.value * standing.confidence * weight
            }
        }
        fold(hotel, HOTEL_PULL)
        fold(department, DEPARTMENT_PULL)
        return CultureLens(blended)
    }

    /** The department whose character an actor most belongs to, for cultural pull. */
    private fun departmentOf(role: RoleKind): Department? = when (role) {
        RoleKind.RECEPTIONIST -> Department.FRONT_DESK
        RoleKind.CONCIERGE -> Department.CONCIERGE
        RoleKind.HOUSEKEEPER -> Department.HOUSEKEEPING
        RoleKind.CHEF -> Department.KITCHEN
        RoleKind.BARTENDER -> Department.BAR
        RoleKind.DUTY_MANAGER, RoleKind.GENERAL_MANAGER, RoleKind.OWNER -> Department.MANAGEMENT
        RoleKind.GUEST, RoleKind.RESIDENT -> null
    }

    private fun taskLabel(type: com.ashcroft.ripple.core.model.HotelTaskType): String =
        type.name.lowercase().replace('_', ' ')

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

        /** A department's own culture presses harder on its people than the whole hotel's. */
        const val DEPARTMENT_PULL = 1.0
        const val HOTEL_PULL = 0.5
    }
}
