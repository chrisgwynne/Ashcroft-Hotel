package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.BehaviourHistory
import com.ashcroft.ripple.core.model.Commitment
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.GridCell
import com.ashcroft.ripple.core.model.Identity
import com.ashcroft.ripple.core.model.LifeStage
import com.ashcroft.ripple.core.model.LocationState
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.NeedState
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.Personality
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind
import com.ashcroft.ripple.core.model.WorldPos

/** A small hand-built world for exercising the decision engine in isolation. */
class StubWorld(
    private val kinds: Map<String, RoomKind> = DEFAULT_ROOMS,
    private val reachable: Set<String> = DEFAULT_ROOMS.keys,
    private val occupants: Map<String, List<PersonId>> = emptyMap(),
    private val peopleMap: Map<PersonId, Person> = emptyMap(),
) : WorldQueries {
    override fun roomKind(room: RoomId): RoomKind? = kinds[room.value]

    override fun roomName(room: RoomId): String = room.value

    override fun firstRoomOfKind(kind: RoomKind): RoomId? =
        kinds.entries.firstOrNull { it.value == kind }?.let { RoomId(it.key) }

    override fun canAccess(actor: Person, room: RoomId): Boolean {
        val kind = kinds[room.value] ?: return false
        if (kind in STAFF_ONLY && !actor.role.isStaff) return false
        if (kind in GUEST_ROOMS && room != actor.homeRoom && !actor.role.isStaff) return false
        return true
    }

    override fun travelMinutes(from: WorldPos, room: RoomId): Int? = if (room.value in reachable) 5 else null

    override fun peopleInRoom(room: RoomId): List<PersonId> = occupants[room.value] ?: emptyList()

    override fun person(id: PersonId): Person? = peopleMap[id]

    companion object {
        val DEFAULT_ROOMS = mapOf(
            "reception" to RoomKind.RECEPTION,
            "restaurant" to RoomKind.RESTAURANT,
            "bar" to RoomKind.BAR,
            "kitchen" to RoomKind.KITCHEN,
            "staff_room" to RoomKind.STAFF_ROOM,
            "room_101" to RoomKind.GUEST_SINGLE,
        )
        val STAFF_ONLY = setOf(RoomKind.STAFF_ROOM, RoomKind.KITCHEN, RoomKind.MANAGER_OFFICE, RoomKind.HOUSEKEEPING_STORE)
        val GUEST_ROOMS = setOf(RoomKind.GUEST_SINGLE, RoomKind.GUEST_DOUBLE, RoomKind.GUEST_TWIN, RoomKind.SUITE)
    }
}

fun testPerson(
    id: String = "p",
    role: RoleKind = RoleKind.GUEST,
    homeRoom: String = "room_101",
    currentRoom: String = "reception",
    needs: NeedState = NeedState.of(),
    personality: Personality = Personality.of(),
    schedule: List<Commitment> = emptyList(),
    acquaintances: Set<PersonId> = emptySet(),
): Person = Person(
    id = PersonId(id),
    identity = Identity(id.replaceFirstChar { it.uppercase() }, 30),
    lifeStage = LifeStage.ADULT,
    role = role,
    personality = personality,
    needs = needs,
    homeRoom = RoomId(homeRoom),
    schedule = schedule,
    goals = emptyList(),
    memories = emptyList(),
    acquaintances = acquaintances,
    behaviour = BehaviourHistory(),
    money = 200,
    location = LocationState(WorldPos(0, GridCell(0, 0)), RoomId(currentRoom)),
    action = ActionState.IDLE,
    lastDecision = null,
)

fun shift(room: String, startHour: Int, endHour: Int): Commitment =
    Commitment(CommitmentKind.SHIFT, RoomId(room), startHour * 60, endHour * 60, strength = 0.85f)

fun needs(vararg pairs: Pair<NeedKind, Float>): NeedState = NeedState.of(*pairs)
