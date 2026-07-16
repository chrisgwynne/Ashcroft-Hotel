package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionCandidate
import com.ashcroft.ripple.core.model.ActionId
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.Commitment
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind

/**
 * Turns what a person can perceive and reach into the concrete set of actions
 * they could attempt next — affordances, not hardcoded story cases. A person is
 * never offered actions they cannot access, cannot reach, or do not know about
 * (e.g. sleeping in a locked suite, or talking to someone on another floor
 * without first travelling).
 */
class ActionCandidateProvider(private val world: WorldQueries) {
    fun knownOpportunities(actor: Person, activeCommitments: List<Commitment>): List<KnownOpportunity> {
        val out = mutableListOf<KnownOpportunity>()

        workRoom(actor, activeCommitments)?.let { out += KnownOpportunity(ActionVerb.WORK, it, "work at ${world.roomName(it)}") }
        world.firstRoomOfKind(RoomKind.RESTAURANT)?.let { out += KnownOpportunity(ActionVerb.EAT, it, "eat at ${world.roomName(it)}") }
        world.firstRoomOfKind(RoomKind.BAR)?.let { out += KnownOpportunity(ActionVerb.SOCIALISE, it, "sit at ${world.roomName(it)}") }
        actor.homeRoom?.let { home ->
            out += KnownOpportunity(ActionVerb.SLEEP, home, "rest in ${world.roomName(home)}")
            out += KnownOpportunity(ActionVerb.WASH, home, "freshen up in ${world.roomName(home)}")
            out += KnownOpportunity(ActionVerb.RETURN_HOME, home, "retreat to ${world.roomName(home)}")
        }
        return out
    }

    fun candidates(
        actor: Person,
        currentRoom: RoomId?,
        perceivedPeople: List<PerceivedPerson>,
        opportunities: List<KnownOpportunity>,
        openTasks: List<TaskOffer> = emptyList(),
    ): List<ActionCandidate> {
        val out = LinkedHashMap<String, ActionCandidate>()

        fun offer(verb: ActionVerb, room: RoomId?, person: com.ashcroft.ripple.core.model.PersonId?, task: TaskOffer? = null) {
            if (room != null && !world.canAccess(actor, room)) return
            if (room != null && room != currentRoom && world.travelMinutes(actor.location.pos, room) == null) return
            val candidate = ActionCandidate(
                id = ActionId("${verb.name}|${room?.value ?: ""}|${person?.value ?: ""}|${task?.taskId?.value ?: ""}"),
                verb = verb,
                targetRoom = room,
                targetPerson = person,
                plannedMinutes = if (task != null) durationForTask(task) else durationFor(verb),
                targetTaskId = task?.taskId,
            )
            out.putIfAbsent(candidate.id.value, candidate)
        }

        for (opportunity in opportunities) offer(opportunity.verb, opportunity.room, null)

        // Hotel work waiting to be done — the reason staff move about and meet guests.
        for (task in openTasks) offer(ActionVerb.ATTEND, task.room, null, task)

        // Social candidates only for people in the same room (perceived, not global).
        for (other in perceivedPeople) {
            val verb = if (other.alreadyKnown) ActionVerb.CONVERSE else ActionVerb.GREET
            offer(verb, currentRoom, other.id)
        }

        // Low-key defaults that are always feasible where the person stands.
        offer(ActionVerb.TAKE_BREAK, currentRoom, null)
        offer(ActionVerb.WAIT, currentRoom, null)
        return out.values.toList()
    }

    private fun durationForTask(task: TaskOffer): Int = when (task.department) {
        com.ashcroft.ripple.core.model.Department.HOUSEKEEPING -> 35
        com.ashcroft.ripple.core.model.Department.KITCHEN -> 25
        com.ashcroft.ripple.core.model.Department.MANAGEMENT -> 20
        else -> 15
    }

    private fun workRoom(actor: Person, activeCommitments: List<Commitment>): RoomId? {
        activeCommitments.firstOrNull { it.kind == CommitmentKind.SHIFT }?.location?.let { return it }
        val kind = roleWorkKind(actor.role) ?: return null
        return world.firstRoomOfKind(kind)
    }

    private fun roleWorkKind(role: RoleKind): RoomKind? = when (role) {
        RoleKind.RECEPTIONIST, RoleKind.DUTY_MANAGER -> RoomKind.RECEPTION
        RoleKind.CONCIERGE -> RoomKind.LOBBY
        RoleKind.HOUSEKEEPER -> RoomKind.HOUSEKEEPING_STORE
        RoleKind.CHEF -> RoomKind.KITCHEN
        RoleKind.BARTENDER -> RoomKind.BAR
        RoleKind.GENERAL_MANAGER, RoleKind.OWNER -> RoomKind.MANAGER_OFFICE
        else -> null
    }

    private fun durationFor(verb: ActionVerb): Int = when (verb) {
        ActionVerb.SLEEP -> 300
        ActionVerb.WORK -> 120
        ActionVerb.SOCIALISE -> 60
        ActionVerb.RELAX, ActionVerb.RETURN_HOME -> 45
        ActionVerb.EAT -> 40
        ActionVerb.CONVERSE -> 25
        ActionVerb.WASH, ActionVerb.TAKE_BREAK -> 20
        ActionVerb.WANDER -> 15
        ActionVerb.WAIT -> 10
        ActionVerb.GREET -> 5
        ActionVerb.ATTEND -> 15
    }
}
