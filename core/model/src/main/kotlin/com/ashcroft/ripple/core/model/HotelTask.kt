package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class HotelTaskId(val value: String)

/**
 * The working parts of the hotel. Departments give staff real responsibilities
 * and give tasks an owner — they are the reason people cross paths for work, not
 * a management game the player controls.
 */
@Serializable
enum class Department {
    FRONT_DESK,
    CONCIERGE,
    HOUSEKEEPING,
    KITCHEN,
    RESTAURANT,
    BAR,
    MAINTENANCE,
    MANAGEMENT,
}

/** The department a staff role most belongs to, for culture and practice; null for guests/residents. */
fun RoleKind.department(): Department? = when (this) {
    RoleKind.RECEPTIONIST -> Department.FRONT_DESK
    RoleKind.CONCIERGE -> Department.CONCIERGE
    RoleKind.HOUSEKEEPER -> Department.HOUSEKEEPING
    RoleKind.CHEF -> Department.KITCHEN
    RoleKind.BARTENDER -> Department.BAR
    RoleKind.DUTY_MANAGER, RoleKind.GENERAL_MANAGER, RoleKind.OWNER -> Department.MANAGEMENT
    RoleKind.GUEST, RoleKind.RESIDENT -> null
}

/**
 * A concrete piece of hotel work. Tasks are never random incidents — each one
 * arises from existing world state (a guest waiting, a room left dirty, an order
 * placed) and is resolved by whoever is willing and able. An unresolved task is
 * just unattended work; only when it lingers and clashes with expectations might
 * an ordinary complaint emerge.
 */
@Serializable
enum class HotelTaskType(val department: Department, val guestFacing: Boolean) {
    CHECK_IN(Department.FRONT_DESK, guestFacing = true),
    RECEPTION_QUESTION(Department.FRONT_DESK, guestFacing = true),
    LUGGAGE_DELIVERY(Department.CONCIERGE, guestFacing = true),
    DIRECTIONS(Department.CONCIERGE, guestFacing = true),
    CLEAN_ROOM(Department.HOUSEKEEPING, guestFacing = false),
    ROOM_INSPECTION(Department.HOUSEKEEPING, guestFacing = false),
    MEAL_ORDER(Department.KITCHEN, guestFacing = false),
    TABLE_SERVICE(Department.RESTAURANT, guestFacing = true),
    BAR_SERVICE(Department.BAR, guestFacing = true),
    MAINTENANCE_REQUEST(Department.MAINTENANCE, guestFacing = false),
    STOCK_DELIVERY(Department.KITCHEN, guestFacing = false),
    SHIFT_HANDOVER(Department.MANAGEMENT, guestFacing = false),
    MANAGER_APPROVAL(Department.MANAGEMENT, guestFacing = false),
}

@Serializable
enum class HotelTaskStatus { OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED, EXPIRED }

/**
 * A persistent operational task in the world. [requiredRoles] gates who can take
 * it; [priority] and [deadline] shape how urgently it is picked up; [causeIds]
 * links it to what gave rise to it (the causal graph proper arrives in Phase 6).
 */
@Serializable
data class HotelTask(
    val id: HotelTaskId,
    val type: HotelTaskType,
    val locationId: RoomId,
    val createdBy: String,
    val requestedBy: PersonId? = null,
    val assignedTo: PersonId? = null,
    val requiredRoles: Set<RoleKind>,
    val priority: Double,
    val createdAt: SimTime,
    val deadline: SimTime? = null,
    val status: HotelTaskStatus = HotelTaskStatus.OPEN,
    val causeIds: Set<CauseId> = emptySet(),
) {
    val isOpen: Boolean get() = status == HotelTaskStatus.OPEN || status == HotelTaskStatus.ASSIGNED
    val department: Department get() = type.department

    /** How long the task has been waiting, in minutes. */
    fun ageMinutes(now: SimTime): Long = now.epochMinutes - createdAt.epochMinutes

    /** Whether this task has waited past its deadline without being resolved. */
    fun isOverdue(now: SimTime): Boolean = deadline != null && now >= deadline && isOpen
}
