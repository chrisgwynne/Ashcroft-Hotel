package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.decision.DecisionMaker
import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.Belief
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.ConversationReception
import com.ashcroft.ripple.core.model.DeterministicRandom
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.FactTopic
import com.ashcroft.ripple.core.model.GoalTarget
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.HotelTask
import com.ashcroft.ripple.core.model.HotelTaskStatus
import com.ashcroft.ripple.core.model.HotelTaskType
import com.ashcroft.ripple.core.model.InformationSource
import com.ashcroft.ripple.core.model.Memory
import com.ashcroft.ripple.core.model.MemoryId
import com.ashcroft.ripple.core.model.MemoryKind
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.Tendencies
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
        val world = HotelWorldQueries(layout, graph, locator, state.people, state.tasks)
        val decider = DecisionMaker(world)

        val advanced = state.people.map { advance(it, state.people, world, decider, state.seed, now) }
        val byId = advanced.associate { it.person.id to it.person }.toMutableMap()
        val socials = advanced.mapNotNull { it.social }.sortedBy { it.actor.value }
        for (event in socials) resolveSocial(event, byId, state.seed, now)

        val resolvedTasks = resolveTasks(byId, state.tasks, now)
        val people = state.people.map { byId.getValue(it.id) }
        val tasks = HotelOperations.generate(layout, people, resolvedTasks, now)
        val chronicle = Chronicler.update(state.people, people, state.chronicle, now)
        return state.copy(clock = now, people = people, chronicle = chronicle, tasks = tasks)
    }

    /**
     * Turn just-completed ATTEND actions into resolved tasks. A guest-facing task
     * finished while its requester is present becomes a small service interaction —
     * both remember it, gratitude and goodwill grow — which is how ordinary work
     * puts staff and guests together.
     */
    private fun resolveTasks(byId: MutableMap<PersonId, Person>, tasks: List<HotelTask>, now: SimTime): List<HotelTask> {
        val open = tasks.associateBy { it.id }.toMutableMap()
        for (person in byId.values.sortedBy { it.id.value }) {
            val action = person.action
            if (action.verb != ActionVerb.ATTEND || action.phase != ActionPhase.COMPLETED) continue
            val task = action.targetTaskId?.let { open[it] } ?: continue
            if (!task.isOpen) continue
            open[task.id] = task.copy(status = HotelTaskStatus.COMPLETED, assignedTo = person.id)
            byId[person.id] = person.copy(
                needs = person.needs.with(NeedKind.RECOGNITION, person.needs[NeedKind.RECOGNITION] + 0.05f),
                tendencyEvidence = person.tendencyEvidence + (Tendencies.HELP to (person.tendencyEvidence[Tendencies.HELP] ?: 0) + 1),
            )
            if (task.type == HotelTaskType.SHIFT_HANDOVER) {
                val outgoing = task.requestedBy?.let { byId[it] }
                if (outgoing != null) byId[person.id] = handoverKnowledge(byId.getValue(person.id), outgoing, now)
                continue
            }
            val requesterId = task.requestedBy
            val requester = requesterId?.let { byId[it] }
            if (task.type.guestFacing && requester != null && requester.location.roomId == task.locationId) {
                byId[requesterId] = requester.copy(
                    memories = remember(requester, MemoryKind.WAS_HELPED, person.id, valence = 0.5, importance = 0.4, now = now),
                    relationships = requester.relationships.adjust(
                        person.id,
                        mapOf(RelationDimension.GRATITUDE to 0.08, RelationDimension.FAMILIARITY to 0.05),
                    ),
                    acquaintances = requester.acquaintances + person.id,
                )
                val server = byId.getValue(person.id)
                byId[person.id] = server.copy(
                    memories = remember(server, MemoryKind.HELPED_SOMEONE, requesterId, 0.4, 0.3, now),
                    relationships = server.relationships.adjust(requesterId, mapOf(RelationDimension.FAMILIARITY to 0.05)),
                    acquaintances = server.acquaintances + requesterId,
                )
            }
        }
        return open.values.toList()
    }

    /**
     * A handover passes on what the outgoing member knows — but only what the
     * incoming member does not already hold more firmly. If nothing is new, the
     * handover communicates nothing (it was already known); otherwise the incoming
     * member takes it on at near-full fidelity and remembers being briefed.
     */
    private fun handoverKnowledge(incoming: Person, outgoing: Person, now: SimTime): Person {
        var kb = incoming.knowledge
        var learned = 0
        for (belief in outgoing.knowledge.all) {
            if (!kb.knows(belief.claim.topic)) learned++
            val relayed = Belief(
                belief.claim,
                (belief.confidence * HANDOVER_FIDELITY).coerceIn(0.0, 1.0),
                InformationSource.CONVERSATION,
                now,
                outgoing.id,
            )
            kb = kb.learn(relayed)
        }
        return if (learned == 0) {
            incoming
        } else {
            incoming.copy(knowledge = kb, memories = remember(incoming, MemoryKind.LEARNED_SOMETHING, outgoing.id, 0.2, 0.3, now))
        }
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
        val perceived = person.copy(
            needs = NeedDynamics.tick(person.needs, effective),
            knowledge = Perception.observe(person, everyone, now),
            emotions = person.emotions.decayed(1),
        )
        val withNeeds = applyRecall(perceived, everyone, now)

        val mustDecide = withNeeds.action.phase.isTerminal || reconsider(withNeeds, now)
        if (mustDecide) {
            val commitments = withNeeds.schedule.filter { it.isActiveAt(now.minuteOfDay) }
            val result = decider.decide(withNeeds, commitments, now, seed)
            return applyDecision(withNeeds.copy(goals = result.goals, lastDecision = result.record), result.action, now)
        }
        return Advance(advanceAction(withNeeds, now), social = null)
    }

    /**
     * Bring a memory to mind if the present moment calls for one, letting it
     * colour the person's feelings (and, through those, their next choice). Only
     * a contextually relevant memory surfaces, and only its recall stats and the
     * rememberer's emotions change — never the objective record.
     */
    private fun applyRecall(person: Person, everyone: List<Person>, now: SimTime): Person {
        if (person.memories.isEmpty()) return person.copy(recalledMemoryId = null)
        val present = everyone.filter { it.location.roomId == person.location.roomId && it.id != person.id }
            .map { it.id }.toSet()
        val rumourSubjects = person.knowledge.all.filter { it.isRumour }.mapNotNull { subjectOf(it.claim.topic) }.toSet()
        val ctx = MemoryRecall.Context(
            room = person.location.roomId,
            presentPeople = present,
            currentVerb = person.action.verb.takeIf { person.action.isPerforming },
            strongestEmotion = person.emotions.strongest,
            goalSubjects = person.goals.mapNotNull { (it.target as? GoalTarget.Person)?.id }.toSet(),
            goalTypes = person.goals.map { it.type }.toSet(),
            rumourSubjects = rumourSubjects,
        )
        val result = MemoryRecall.recall(person, ctx, now)
        val recalled = result.recalled ?: return person.copy(recalledMemoryId = null)
        return person.copy(
            memories = person.memories.map { if (it.id == recalled.id) it.recalled(now) else it },
            emotions = person.emotions.stirred(result.emotionDeltas),
            recalledMemoryId = recalled.id,
        )
    }

    private fun subjectOf(topic: FactTopic): PersonId? = when (topic) {
        is FactTopic.Whereabouts -> topic.person
        is FactTopic.PersonMood -> topic.person
        is FactTopic.NotableGuest -> topic.person
        is FactTopic.RoomOccupancy -> null
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
            // They set out to talk to someone who has since moved on.
            byId[event.actor] = failSocial(actor, event.target, now)
            return
        }
        val roll = DeterministicRandom(
            DeterministicRandom.seedOf(
                seed,
                event.actor.value.hashCode().toLong(),
                event.target.value.hashCode().toLong(),
                now.epochMinutes,
            ),
        ).nextFloat()
        val present = byId.values.filter { it.location.roomId == actor.location.roomId }.map { it.id }.toSet()
        val (updatedActor, updatedTarget) = ConversationSystem.converse(actor, target, present, roll, now)
        // A refused overture is a failed action; anything received lets the action run its course.
        byId[event.actor] = if (updatedActor.lastConversation?.reception == ConversationReception.REFUSED) {
            updatedActor.copy(
                action = updatedActor.action.copy(phase = ActionPhase.FAILED),
                behaviour = updatedActor.behaviour.recordFailure(updatedActor.action.signature()),
            )
        } else {
            updatedActor
        }
        byId[event.target] = updatedTarget
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
        const val HANDOVER_FIDELITY = 0.9

        // A rebuffed overture leaves a small sting — a little resentment, and a face now known.
        val REBUFFED = mapOf(
            RelationDimension.FAMILIARITY to 0.02,
            RelationDimension.RESENTMENT to 0.05,
        )
    }
}
