package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.decision.DecisionMaker
import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.DeterministicRandom
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.Memory
import com.ashcroft.ripple.core.model.MemoryId
import com.ashcroft.ripple.core.model.MemoryKind
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.TraitKind
import com.ashcroft.ripple.core.world.AshcroftNav

/**
 * The deterministic heart of the simulation. Each tick advances the world by
 * one simulated minute as a pure function of the state (and the seed it
 * carries). Movement, needs and the action lifecycle advance per person; when a
 * person becomes free — or an urgent need or an imminent shift warrants it — the
 * general-purpose [DecisionMaker] chooses their next action. Social actions are
 * resolved in a second pass so both parties are affected consistently.
 */
class SimulationEngine(
    private val layout: HotelLayout,
    private val graph: NavGraph = NavGraph.from(layout, AshcroftNav.stairPortals()),
    private val locator: RoomLocator = RoomLocator.from(layout),
) {
    private data class Advance(val person: Person, val social: PendingSocial?)

    private data class PendingSocial(val actor: PersonId, val target: PersonId, val verb: ActionVerb)

    fun step(state: WorldState): WorldState {
        val now = state.clock + 1
        val world = HotelWorldQueries(layout, graph, locator, state.people)
        val decider = DecisionMaker(world)

        val advanced = state.people.map { advance(it, world, decider, state.seed, now) }
        val byId = advanced.associate { it.person.id to it.person }.toMutableMap()
        val socials = advanced.mapNotNull { it.social }.sortedBy { it.actor.value }
        for (event in socials) resolveSocial(event, byId, state.seed, now)

        val people = state.people.map { byId.getValue(it.id) }
        return state.copy(clock = now, people = people)
    }

    fun run(state: WorldState, minutes: Int): WorldState {
        var current = state
        repeat(minutes) { current = step(current) }
        return current
    }

    /** Expose reasoning for a person for the "Why?" interface (rebuilds a world view). */
    fun explain(state: WorldState, id: PersonId) =
        state.person(id)?.lastDecision?.let { DecisionMaker(HotelWorldQueries(layout, graph, locator, state.people)).explanationFor(it) }

    private fun advance(person: Person, world: HotelWorldQueries, decider: DecisionMaker, seed: Long, now: SimTime): Advance {
        val effective = effectiveActivity(person)
        val withNeeds = person.copy(needs = NeedDynamics.tick(person.needs, effective))

        val mustDecide = withNeeds.action.phase.isTerminal || reconsider(withNeeds, now)
        if (mustDecide) {
            val commitments = withNeeds.schedule.filter { it.isActiveAt(now.minuteOfDay) }
            val result = decider.decide(withNeeds, commitments, now, seed)
            return applyDecision(withNeeds.copy(goals = result.goals, lastDecision = result.record), result.action, now)
        }
        return Advance(advanceAction(withNeeds, now), social = null)
    }

    private fun applyDecision(person: Person, action: ActionState, now: SimTime): Advance {
        val target = action.targetRoom
        if (action.verb.social && action.targetPerson != null) {
            val started = action.copy(phase = ActionPhase.IN_PROGRESS, startedAt = now, elapsedMinutes = 0)
            val located = person.copy(action = started, location = person.location.copy(path = emptyList()))
            return Advance(located, PendingSocial(person.id, action.targetPerson!!, action.verb))
        }
        if (target == null || person.location.roomId == target) {
            return Advance(person.copy(action = action.copy(phase = ActionPhase.IN_PROGRESS, startedAt = now)), null)
        }
        val access = graph.accessPos(target)
        val path = if (access != null) Pathfinder.findPath(graph, person.location.pos, access) else emptyList()
        if (path.isEmpty()) {
            return Advance(person.copy(action = action.copy(phase = ActionPhase.IN_PROGRESS, startedAt = now)), null)
        }
        return Advance(
            person.copy(action = action.copy(phase = ActionPhase.TRAVELLING), location = person.location.copy(path = path)),
            null,
        )
    }

    private fun advanceAction(person: Person, now: SimTime): Person {
        val a = person.action
        if (person.location.isMoving) {
            val next = person.location.path.first()
            val moved = person.location.copy(pos = next, roomId = graph.roomOf(next), path = person.location.path.drop(1))
            val arrived = moved.path.isEmpty() && a.phase == ActionPhase.TRAVELLING
            val action = if (arrived) a.copy(phase = ActionPhase.IN_PROGRESS, startedAt = now, elapsedMinutes = 0) else a
            return person.copy(location = moved, action = action)
        }
        if (a.phase == ActionPhase.TRAVELLING) {
            return person.copy(action = a.copy(phase = ActionPhase.IN_PROGRESS, startedAt = now, elapsedMinutes = 0))
        }
        if (a.isPerforming) {
            val elapsed = a.elapsedMinutes + 1
            return if (elapsed >= a.plannedMinutes) {
                person.copy(
                    action = a.copy(phase = ActionPhase.COMPLETED, elapsedMinutes = elapsed),
                    behaviour = person.behaviour.recordCompletion(a.signature(), person.location.roomId, a.targetPerson),
                )
            } else {
                person.copy(action = a.copy(elapsedMinutes = elapsed))
            }
        }
        return person.copy(action = a.copy(phase = ActionPhase.IN_PROGRESS, startedAt = now, elapsedMinutes = 0))
    }

    private fun resolveSocial(event: PendingSocial, byId: MutableMap<PersonId, Person>, seed: Long, now: SimTime) {
        val actor = byId[event.actor] ?: return
        val target = byId[event.target] ?: return
        if (target.location.roomId != actor.location.roomId) {
            byId[event.actor] = failSocial(actor, event.target, now)
            return
        }
        val willingness = recipientWillingness(target, actor)
        val roll = DeterministicRandom(
            DeterministicRandom.seedOf(
                seed,
                event.actor.value.hashCode().toLong(),
                event.target.value.hashCode().toLong(),
                now.epochMinutes,
            ),
        ).nextFloat()
        if (roll < willingness.coerceIn(0.05f, 0.95f)) {
            byId[event.actor] = actor.copy(
                needs = actor.needs.with(NeedKind.SOCIAL, actor.needs[NeedKind.SOCIAL] + 0.12f),
                memories = remember(actor, MemoryKind.HAD_PLEASANT_CHAT, target.id, valence = 0.6, importance = 0.5, now = now),
                acquaintances = actor.acquaintances + target.id,
            )
            byId[event.target] = target.copy(
                needs = target.needs.with(NeedKind.SOCIAL, target.needs[NeedKind.SOCIAL] + 0.08f),
                memories = remember(target, MemoryKind.HAD_PLEASANT_CHAT, actor.id, valence = 0.5, importance = 0.4, now = now),
                acquaintances = target.acquaintances + actor.id,
            )
        } else {
            byId[event.actor] = failSocial(actor, event.target, now)
        }
    }

    private fun failSocial(actor: Person, target: PersonId, now: SimTime): Person = actor.copy(
        needs = actor.needs.with(NeedKind.SOCIAL, actor.needs[NeedKind.SOCIAL] - 0.03f),
        action = actor.action.copy(phase = ActionPhase.FAILED),
        memories = remember(actor, MemoryKind.WAS_IGNORED, target, valence = -0.5, importance = 0.5, now = now),
        behaviour = actor.behaviour.recordFailure(actor.action.signature()),
    )

    private fun recipientWillingness(target: Person, actor: Person): Float {
        val base = target.personality[TraitKind.SOCIABILITY] * 0.5f
        val loneliness = (1f - target.needs[NeedKind.SOCIAL]) * 0.35f
        val busy = if (target.action.verb == ActionVerb.WORK && target.schedule.any { it.kind == CommitmentKind.SHIFT }) -0.25f else 0f
        val sentiment = target.sentimentToward(actor.id).coerceIn(-0.5, 0.5).toFloat() * 0.3f
        val known = if (target.acquaintances.contains(actor.id)) 0.1f else 0f
        return base + loneliness + busy + sentiment + known
    }

    private fun remember(
        owner: Person,
        kind: MemoryKind,
        subject: PersonId?,
        valence: Double,
        importance: Double,
        now: SimTime,
    ): List<Memory> {
        val memory = Memory(
            id = MemoryId("m:${owner.id.value}:${now.epochMinutes}:$kind"),
            ownerId = owner.id,
            occurredAt = now,
            kind = kind,
            subjectId = subject,
            valence = valence,
            importance = importance,
        )
        return (owner.memories + memory).takeLast(MAX_MEMORIES)
    }

    private fun effectiveActivity(person: Person): ActivityKind = when {
        person.location.isMoving -> ActivityKind.TRAVEL
        person.action.isPerforming -> person.action.verb.effect
        else -> ActivityKind.IDLE
    }

    private fun reconsider(person: Person, now: SimTime): Boolean {
        if (person.location.isMoving) return false
        val urgent = NeedKind.entries.filter { person.needs[it] < URGENT_FLOOR }
        if (urgent.isNotEmpty()) {
            val relieved = reliefNeeds(person.action.verb.effect)
            if (urgent.none { it in relieved }) return true
        }
        val shiftJustStarted = person.schedule.any { it.kind == CommitmentKind.SHIFT && it.startMinuteOfDay == now.minuteOfDay }
        return shiftJustStarted && person.action.verb != ActionVerb.WORK
    }

    private fun reliefNeeds(kind: ActivityKind): Set<NeedKind> = when (kind) {
        ActivityKind.EAT -> setOf(NeedKind.HUNGER)
        ActivityKind.SLEEP -> setOf(NeedKind.REST, NeedKind.COMFORT, NeedKind.SAFETY)
        ActivityKind.WASH -> setOf(NeedKind.HYGIENE)
        ActivityKind.RELAX -> setOf(NeedKind.PRIVACY, NeedKind.COMFORT, NeedKind.AUTONOMY)
        ActivityKind.SOCIALISE -> setOf(NeedKind.SOCIAL)
        ActivityKind.WORK -> setOf(NeedKind.PURPOSE, NeedKind.RECOGNITION)
        ActivityKind.TRAVEL, ActivityKind.IDLE -> emptySet()
    }

    private companion object {
        const val URGENT_FLOOR = 0.12f
        const val MAX_MEMORIES = 40
    }
}
