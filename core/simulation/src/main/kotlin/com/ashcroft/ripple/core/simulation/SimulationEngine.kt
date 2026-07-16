package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.decision.DecisionMaker
import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.Belief
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.DeterministicRandom
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.FactTopic
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.InformationSource
import com.ashcroft.ripple.core.model.Memory
import com.ashcroft.ripple.core.model.MemoryId
import com.ashcroft.ripple.core.model.MemoryKind
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension
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

        val advanced = state.people.map { advance(it, state.people, world, decider, state.seed, now) }
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

    private fun advance(
        person: Person,
        everyone: List<Person>,
        world: HotelWorldQueries,
        decider: DecisionMaker,
        seed: Long,
        now: SimTime,
    ): Advance {
        val effective = effectiveActivity(person)
        val withNeeds = person.copy(
            needs = NeedDynamics.tick(person.needs, effective),
            knowledge = Perception.observe(person, everyone, now),
            emotions = person.emotions.decayed(1),
        )

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
            val present = byId.values.filter { it.location.roomId == actor.location.roomId }.map { it.id }.toSet()
            val shared = sharedBeliefs(actor, present, now)
            byId[event.actor] = actor.copy(
                needs = actor.needs.with(NeedKind.SOCIAL, actor.needs[NeedKind.SOCIAL] + 0.12f),
                memories = remember(actor, MemoryKind.HAD_PLEASANT_CHAT, target.id, valence = 0.6, importance = 0.5, now = now),
                acquaintances = actor.acquaintances + target.id,
                relationships = actor.relationships.adjust(target.id, WARMED_INITIATOR),
                emotions = actor.emotions.stirred(mapOf(EmotionKind.HAPPINESS to 0.15, EmotionKind.LONELINESS to -0.2)),
            )
            byId[event.target] = target.copy(
                needs = target.needs.with(NeedKind.SOCIAL, target.needs[NeedKind.SOCIAL] + 0.08f),
                memories = remember(target, MemoryKind.HAD_PLEASANT_CHAT, actor.id, valence = 0.5, importance = 0.4, now = now),
                acquaintances = target.acquaintances + actor.id,
                relationships = target.relationships.adjust(actor.id, WARMED_RECIPIENT),
                emotions = target.emotions.stirred(mapOf(EmotionKind.HAPPINESS to 0.1, EmotionKind.LONELINESS to -0.15)),
                // The recipient picks up what the initiator mentions — second-hand, so held as rumour.
                knowledge = shared.fold(target.knowledge) { kb, belief -> kb.learn(belief) },
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
        relationships = actor.relationships.adjust(target, REBUFFED),
        emotions = actor.emotions.stirred(
            mapOf(EmotionKind.EMBARRASSMENT to 0.2, EmotionKind.FRUSTRATION to 0.12, EmotionKind.LONELINESS to 0.05),
        ),
    )

    /**
     * The single belief the initiator brings up — news the listener probably
     * cannot see for themselves: a notable arrival if they know of one, else
     * something about a person or place *not* in the room. The recipient takes it
     * on at reduced confidence, so a stale or mistaken belief spreads as a rumour.
     */
    private fun sharedBeliefs(actor: Person, present: Set<PersonId>, now: SimTime): List<Belief> {
        val elsewhere = actor.knowledge.all.filter { belief ->
            when (val topic = belief.claim.topic) {
                is FactTopic.NotableGuest -> true
                is FactTopic.Whereabouts -> topic.person !in present
                is FactTopic.PersonMood -> topic.person !in present
                is FactTopic.RoomOccupancy -> topic.room != actor.location.roomId
            }
        }
        // Lead with any notable arrival, then the things they are most sure of.
        val picks = (
            elsewhere.filter { it.claim.topic is FactTopic.NotableGuest } +
                elsewhere.sortedByDescending { it.confidence }
        ).distinctBy { it.topicKey }.take(SHARE_LIMIT)
        return picks.map { pick ->
            Belief(
                claim = pick.claim,
                confidence = (pick.confidence * HEARSAY_DECAY).coerceIn(0.0, 1.0),
                source = InformationSource.CONVERSATION,
                acquiredAt = now,
                fromPerson = actor.id,
            )
        }
    }

    private fun recipientWillingness(target: Person, actor: Person): Float {
        val base = target.personality[TraitKind.SOCIABILITY] * 0.5f
        val loneliness = (1f - target.needs[NeedKind.SOCIAL]) * 0.35f
        val busy = if (target.action.verb == ActionVerb.WORK && target.schedule.any { it.kind == CommitmentKind.SHIFT }) -0.25f else 0f
        val sentiment = target.sentimentToward(actor.id).coerceIn(-0.5, 0.5).toFloat() * 0.3f
        val known = if (target.acquaintances.contains(actor.id)) 0.1f else 0f
        val warmth = target.relationships.with(actor.id).warmth().coerceIn(-0.5, 0.5).toFloat() * 0.2f
        return base + loneliness + busy + sentiment + known + warmth
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
        const val HEARSAY_DECAY = 0.6
        const val SHARE_LIMIT = 2

        // A good exchange warms several axes at once — never a single friendship score.
        val WARMED_INITIATOR = mapOf(
            RelationDimension.FAMILIARITY to 0.06,
            RelationDimension.AFFECTION to 0.04,
            RelationDimension.TRUST to 0.02,
        )
        val WARMED_RECIPIENT = mapOf(
            RelationDimension.FAMILIARITY to 0.06,
            RelationDimension.AFFECTION to 0.03,
            RelationDimension.TRUST to 0.015,
        )
        val REBUFFED = mapOf(
            RelationDimension.FAMILIARITY to 0.02,
            RelationDimension.RESENTMENT to 0.05,
        )
    }
}
