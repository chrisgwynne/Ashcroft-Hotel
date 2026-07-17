package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.department
import kotlinx.serialization.Serializable

/**
 * Something the player has chosen to keep in view. Following changes nothing about
 * the simulation — it only decides what the interface keeps surfacing. Serialisable
 * so a follow survives the app being closed and reopened.
 */
@Serializable
sealed interface FollowTarget {
    @Serializable
    data class OfPerson(val id: PersonId) : FollowTarget

    @Serializable
    data class OfRoom(val id: RoomId) : FollowTarget

    @Serializable
    data class OfDepartment(val department: Department) : FollowTarget

    @Serializable
    data class OfRelationship(val a: PersonId, val b: PersonId) : FollowTarget

    /** The entity this target concerns, for filtering the pulse to what a follower cares about. */
    fun entity(): EntityId = when (this) {
        is OfPerson -> EntityId.person(id)
        is OfRoom -> EntityId.room(id)
        is OfDepartment -> EntityId.department(department)
        is OfRelationship -> EntityId.person(a)
    }
}

/** The player's persisted set of follows. Order is preserved so the most-recent follow can lead. */
@Serializable
data class FollowState(
    val targets: List<FollowTarget> = emptyList(),
) {
    fun follow(target: FollowTarget): FollowState =
        if (target in targets) this else copy(targets = targets + target)

    fun unfollow(target: FollowTarget): FollowState = copy(targets = targets - target)

    fun isFollowing(target: FollowTarget): Boolean = target in targets

    companion object {
        val EMPTY = FollowState()
    }
}

/** A readable digest of a followed entity's current state and recent developments. */
data class FollowDigest(
    val title: String,
    val lines: List<String>,
    val relevantPulse: List<PulseEvent>,
)

/**
 * Projects a followed target into a readable digest from the current world — the
 * "keep me posted" view. It reads only what the observation layer is allowed to
 * show and reuses the same [Inspectors] the rest of the UI does, so nothing here
 * is invented. [recentPulse] is filtered to the events that concern the target.
 */
object FollowProjector {
    fun digest(state: WorldState, target: FollowTarget, recentPulse: List<PulseEvent> = emptyList()): FollowDigest {
        val relevant = recentPulse.filter { target.entity() in it.subjects || relatesToRelationship(target, it) }
        return when (target) {
            is FollowTarget.OfPerson -> personDigest(state, target.id, relevant)
            is FollowTarget.OfRoom -> roomDigest(state, target.id, relevant)
            is FollowTarget.OfDepartment -> departmentDigest(state, target.department, relevant)
            is FollowTarget.OfRelationship -> relationshipDigest(state, target.a, target.b, relevant)
        }
    }

    private fun personDigest(state: WorldState, id: PersonId, pulse: List<PulseEvent>): FollowDigest {
        val person = state.person(id) ?: return FollowDigest("Unknown", emptyList(), pulse)
        val lines = buildList {
            add("where: ${roomName(person.location.roomId)}")
            add("now: ${person.action.verb.name.lowercase().replace('_', ' ')} (${person.action.phase.name.lowercase()})")
            person.goals.maxByOrNull { it.priority }?.let { add("wants: ${it.type.name.lowercase().replace('_', ' ')}") }
            person.emotions.strongest?.let { add("feeling: ${it.name.lowercase()}") }
            person.recalledMemoryId?.let { mid ->
                person.memories.firstOrNull { it.id == mid }?.let { add("just remembered: ${it.kind.name.lowercase().replace('_', ' ')}") }
            }
            person.stay?.let { add("stay: ${describeReturn(it.returnIntention.stage.name)}") }
            person.aspiration?.takeIf { it.isPursuing }?.let { add("aspires: ${it.focus.name.lowercase()} (drive ${round(it.drive)})") }
        }
        return FollowDigest(person.name, lines, pulse)
    }

    private fun roomDigest(state: WorldState, id: RoomId, pulse: List<PulseEvent>): FollowDigest {
        val occupants = state.people.filter { it.location.roomId == id }
        val lines = buildList {
            add("occupants: ${if (occupants.isEmpty()) "empty" else occupants.joinToString(", ") { it.name }}")
            val activities = occupants.map { it.action.verb.name.lowercase() }.distinct()
            if (activities.isNotEmpty()) add("activity: ${activities.joinToString(", ")}")
            val open = state.tasks.count { it.locationId == id && it.isOpen }
            if (open > 0) add("waiting work: $open")
            addAll(Inspectors.cultureOf(state, EntityId.room(id)).take(3))
        }
        return FollowDigest(roomName(id), lines, pulse)
    }

    private fun departmentDigest(state: WorldState, dept: Department, pulse: List<PulseEvent>): FollowDigest {
        val team = state.people.filter { it.role.isStaff && it.role.department() == dept }
        val onShift = team.count { it.action.verb.name == "WORK" || it.action.verb.name == "ATTEND" }
        val lines = buildList {
            add("team: ${team.joinToString(", ") { it.name }}")
            add("at work now: $onShift of ${team.size}")
            add("waiting work: ${state.tasks.count { it.department == dept && it.isOpen }}")
            addAll(Inspectors.departmentIdentity(state, dept).drop(1).take(6))
        }
        return FollowDigest("The ${dept.name.lowercase().replace('_', ' ')} team", lines, pulse)
    }

    private fun relationshipDigest(state: WorldState, a: PersonId, b: PersonId, pulse: List<PulseEvent>): FollowDigest {
        val pa = state.person(a)
        val pb = state.person(b)
        val title = "${pa?.name ?: a.value} & ${pb?.name ?: b.value}"
        val lines = buildList {
            if (pa != null) add("${pa.name} → ${pb?.name ?: b.value}: ${readEdge(pa, b)}")
            if (pb != null) add("${pb.name} → ${pa?.name ?: a.value}: ${readEdge(pb, a)}")
        }
        return FollowDigest(title, lines, pulse)
    }

    private fun readEdge(person: com.ashcroft.ripple.core.model.Person, other: PersonId): String {
        val edge = person.relationships.with(other)
        val warmth = edge.warmth()
        val resentment = edge[com.ashcroft.ripple.core.model.RelationDimension.RESENTMENT]
        return when {
            resentment >= 0.5 -> "cool (resentment ${round(resentment)})"
            warmth >= 1.2 -> "close (warmth ${round(warmth)})"
            warmth >= 0.4 -> "friendly (warmth ${round(warmth)})"
            else -> "acquainted"
        }
    }

    private fun relatesToRelationship(target: FollowTarget, event: PulseEvent): Boolean {
        if (target !is FollowTarget.OfRelationship) return false
        return EntityId.person(target.a) in event.subjects && EntityId.person(target.b) in event.subjects
    }

    private fun describeReturn(stage: String): String = when (stage) {
        "PLANNING" -> "thinking hard about a return"
        "INTENDING" -> "warming to a return"
        "BOOKED" -> "booked to return"
        "ARRIVED" -> "back again"
        else -> "settling in"
    }

    // The observation layer works from WorldState alone; the room's display name lives
    // in the layout and is resolved by the UI. Here we key by the stable room id.
    private fun roomName(id: RoomId?): String = id?.value ?: "—"

    private fun round(v: Double): Double = kotlin.math.round(v * 100) / 100.0
}
