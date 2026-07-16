package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.HotelTask
import com.ashcroft.ripple.core.model.HotelTaskId
import com.ashcroft.ripple.core.model.HotelTaskStatus
import com.ashcroft.ripple.core.model.HotelTaskType
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind
import com.ashcroft.ripple.core.model.SimTime

/**
 * The hotel's ordinary operation, expressed as tasks. Tasks are never random
 * incidents: each one is read deterministically off existing world state — a
 * guest sitting in the bar, a guest in the lobby, a room due its daily clean.
 * Whoever is willing and able attends to them (via the ATTEND action); attending
 * a guest-facing task puts staff and guest together, which is the whole point.
 *
 * Nothing here schedules a story or forces a meeting. It only turns the state
 * the world is already in into work that people may choose to do.
 */
internal object HotelOperations {
    private const val COOLDOWN = 180L
    private const val PURGE = 300L
    private const val DAY = 1440L

    /** Which roles may serve each department. */
    fun rolesFor(department: Department): Set<RoleKind> = when (department) {
        Department.FRONT_DESK -> setOf(RoleKind.RECEPTIONIST, RoleKind.DUTY_MANAGER)
        Department.CONCIERGE -> setOf(RoleKind.CONCIERGE, RoleKind.DUTY_MANAGER)
        Department.HOUSEKEEPING -> setOf(RoleKind.HOUSEKEEPER)
        Department.KITCHEN -> setOf(RoleKind.CHEF)
        Department.RESTAURANT -> setOf(RoleKind.CHEF, RoleKind.BARTENDER)
        Department.BAR -> setOf(RoleKind.BARTENDER)
        Department.MAINTENANCE -> setOf(RoleKind.CONCIERGE, RoleKind.HOUSEKEEPER)
        Department.MANAGEMENT -> setOf(RoleKind.GENERAL_MANAGER, RoleKind.DUTY_MANAGER, RoleKind.OWNER)
    }

    /**
     * Read the current state and return the updated task list: expire the stale,
     * drop the long-finished, and open any new task the world now warrants.
     */
    fun generate(layout: HotelLayout, people: List<Person>, existing: List<HotelTask>, now: SimTime): List<HotelTask> {
        val kept = existing
            .map { if (it.isOverdue(now)) it.copy(status = HotelTaskStatus.EXPIRED) else it }
            .filter { it.isOpen || it.ageMinutes(now) < PURGE }
            .toMutableList()

        fun blocked(key: String): Boolean =
            kept.any { it.id.value == key && (it.isOpen || it.ageMinutes(now) < COOLDOWN) }

        val candidates = serviceRequests(layout, people, now) + cleaningDue(people, now) + handoverTasks(people, now)
        for (candidate in candidates) if (!blocked(candidate.id.value)) kept += candidate
        return kept
    }

    private fun task(
        key: String,
        type: HotelTaskType,
        room: RoomId,
        requester: PersonId?,
        roles: Set<RoleKind>,
        priority: Double,
        deadline: Long,
        now: SimTime,
    ): HotelTask = HotelTask(
        id = HotelTaskId(key),
        type = type,
        locationId = room,
        createdBy = "operations",
        requestedBy = requester,
        requiredRoles = roles,
        priority = priority,
        createdAt = now,
        deadline = SimTime(now.epochMinutes + deadline),
    )

    /** Guests present in a service area generate a request for service there. */
    private fun serviceRequests(layout: HotelLayout, people: List<Person>, now: SimTime): List<HotelTask> =
        people.filter { it.role == RoleKind.GUEST && !it.location.isMoving }.mapNotNull { guest ->
            val room = guest.location.roomId ?: return@mapNotNull null
            when (layout.room(room)?.kind) {
                RoomKind.BAR -> serviceTask("bar", HotelTaskType.BAR_SERVICE, room, guest, 0.6, now)
                RoomKind.RESTAURANT -> serviceTask("table", HotelTaskType.TABLE_SERVICE, room, guest, 0.65, now)
                RoomKind.LOBBY, RoomKind.RECEPTION ->
                    serviceTask("ask", HotelTaskType.RECEPTION_QUESTION, reception(layout) ?: room, guest, 0.5, now)
                else -> null
            }
        }

    private fun serviceTask(prefix: String, type: HotelTaskType, room: RoomId, guest: Person, priority: Double, now: SimTime): HotelTask =
        task("$prefix:${guest.id.value}", type, room, guest.id, rolesFor(type.department), priority, 100, now)

    /** Each occupied guest room is due a clean once a day, at a per-room fixed minute. */
    private fun cleaningDue(people: List<Person>, now: SimTime): List<HotelTask> =
        people.filter { it.role == RoleKind.GUEST }.mapNotNull { guest ->
            val home = guest.homeRoom ?: return@mapNotNull null
            val slot = home.value.hashCode().mod(12) * 60 + 9 * 60 // staggered mid-morning
            if (now.minuteOfDay != slot) {
                null
            } else {
                val roles = rolesFor(Department.HOUSEKEEPING)
                task("clean:${home.value}:${now.dayIndex}", HotelTaskType.CLEAN_ROOM, home, null, roles, 0.4, DAY / 2, now)
            }
        }

    /**
     * When shifts overlap at the same post, the outgoing member has things worth
     * passing on. Handover is offered to the incoming member — a high-value action
     * only when there is unshared knowledge, never a guaranteed chat.
     */
    private fun handoverTasks(people: List<Person>, now: SimTime): List<HotelTask> =
        handovers(people, now).map { h ->
            task(
                "handover:${h.location.value}:${now.dayIndex}", HotelTaskType.SHIFT_HANDOVER, h.location,
                h.outgoing.id, setOf(h.incoming.role), 0.7, 60, now,
            )
        }

    private data class Handover(val location: RoomId, val outgoing: Person, val incoming: Person)

    private fun activeShift(person: Person, now: SimTime) =
        person.schedule.firstOrNull { it.kind == com.ashcroft.ripple.core.model.CommitmentKind.SHIFT && it.isActiveAt(now.minuteOfDay) }

    /** Pairs of staff whose shifts overlap at the same post right now: outgoing → incoming. */
    private fun handovers(people: List<Person>, now: SimTime): List<Handover> {
        val onShift = people.filter { it.role.isStaff }.mapNotNull { p -> activeShift(p, now)?.let { p to it } }
        return onShift.groupBy { it.second.location }
            .filter { it.value.size >= 2 }
            .mapNotNull { (loc, group) ->
                val room = loc ?: return@mapNotNull null
                val sorted = group.sortedBy { it.second.endMinuteOfDay }
                val outgoing = sorted.first().first
                val incoming = sorted.last().first
                if (outgoing.id == incoming.id) null else Handover(room, outgoing, incoming)
            }
    }

    private fun reception(layout: HotelLayout): RoomId? =
        layout.allRooms.firstOrNull { it.kind == RoomKind.RECEPTION }?.id
}
