package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.HotelTask
import com.ashcroft.ripple.core.model.HotelTaskId
import com.ashcroft.ripple.core.model.HotelTaskStatus
import com.ashcroft.ripple.core.model.HotelTaskType
import com.ashcroft.ripple.core.model.Person
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

        fun add(key: String, type: HotelTaskType, room: RoomId, requester: Person?, priority: Double, deadline: Long) {
            if (blocked(key)) return
            kept += HotelTask(
                id = HotelTaskId(key),
                type = type,
                locationId = room,
                createdBy = "operations",
                requestedBy = requester?.id,
                requiredRoles = rolesFor(type.department),
                priority = priority,
                createdAt = now,
                deadline = SimTime(now.epochMinutes + deadline),
            )
        }

        // Guests present in a service area generate a request for service there.
        for (guest in people.filter { it.role == RoleKind.GUEST && !it.location.isMoving }) {
            val room = guest.location.roomId ?: continue
            when (layout.room(room)?.kind) {
                RoomKind.BAR -> add("bar:${guest.id.value}", HotelTaskType.BAR_SERVICE, room, guest, 0.6, 90)
                RoomKind.RESTAURANT -> add("table:${guest.id.value}", HotelTaskType.TABLE_SERVICE, room, guest, 0.65, 90)
                RoomKind.LOBBY, RoomKind.RECEPTION ->
                    add("ask:${guest.id.value}", HotelTaskType.RECEPTION_QUESTION, reception(layout) ?: room, guest, 0.5, 120)
                else -> Unit
            }
        }

        // Each occupied guest room is due a clean once a day, at a per-room fixed minute.
        for (guest in people.filter { it.role == RoleKind.GUEST }) {
            val home = guest.homeRoom ?: continue
            val slot = (home.value.hashCode().mod(12)) * 60 + 9 * 60 // staggered mid-morning
            if (now.minuteOfDay == slot) {
                add("clean:${home.value}:${now.dayIndex}", HotelTaskType.CLEAN_ROOM, home, null, 0.4, DAY / 2)
            }
        }
        return kept
    }

    private fun reception(layout: HotelLayout): RoomId? =
        layout.allRooms.firstOrNull { it.kind == RoomKind.RECEPTION }?.id
}
