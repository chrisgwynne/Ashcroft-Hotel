package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.decision.DecisionMaker
import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.Belief
import com.ashcroft.ripple.core.model.CausalGraph
import com.ashcroft.ripple.core.model.CauseId
import com.ashcroft.ripple.core.model.CauseRelation
import com.ashcroft.ripple.core.model.CauseType
import com.ashcroft.ripple.core.model.ChronicleEntry
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.ConversationAct
import com.ashcroft.ripple.core.model.ConversationReception
import com.ashcroft.ripple.core.model.ConversationRecord
import com.ashcroft.ripple.core.model.DeterministicRandom
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.EvidenceDimension
import com.ashcroft.ripple.core.model.FactTopic
import com.ashcroft.ripple.core.model.GoalTarget
import com.ashcroft.ripple.core.model.GuestStay
import com.ashcroft.ripple.core.model.GuestValue
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.HotelTask
import com.ashcroft.ripple.core.model.HotelTaskStatus
import com.ashcroft.ripple.core.model.HotelTaskType
import com.ashcroft.ripple.core.model.InformationSource
import com.ashcroft.ripple.core.model.KnowledgeBase
import com.ashcroft.ripple.core.model.Memory
import com.ashcroft.ripple.core.model.MemoryId
import com.ashcroft.ripple.core.model.MemoryKind
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.PerceptionDimension
import com.ashcroft.ripple.core.model.PerceptionWeights
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.PracticeRegistry
import com.ashcroft.ripple.core.model.ProfessionalDimension
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.ReturnStage
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.StandingDimension
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
        val world = HotelWorldQueries(layout, graph, locator, state.people, state.tasks, state.culture, state.practices)
        val decider = DecisionMaker(world)
        val log = CauseLog(now)
        val evidence = EvidenceLog(now)

        val advanced = state.people.map { advance(it, state.people, world, decider, state.seed, now, log) }
        val byId = advanced.associate { it.person.id to it.person }.toMutableMap()
        val socials = advanced.mapNotNull { it.social }.sortedBy { it.actor.value }
        for (event in socials) resolveSocial(event, byId, state.seed, now, log, evidence)

        val resolvedTasks = resolveTasks(byId, state.tasks, now, log, evidence)
        val peopleResolved = state.people.map { byId.getValue(it.id) }
        val generated = HotelOperations.generate(layout, peopleResolved, resolvedTasks, now)
        val tasks = recordNewTasks(state.tasks, generated, log)
        val served = applyOverdueConsequences(resolvedTasks, tasks, peopleResolved, now, log, evidence)
        // Evidence gathered this tick folds into the witnesses' observer-specific standings.
        val people = applyEvidence(served, evidence)
        // Doing and watching turn into habits, and the aggregate of habits into customs.
        val (habituated, practices) = SocialLearning.apply(state.people, people, state.practices, now)
        // Careers grow from that record; leaders recognise whom they believe has earned it.
        val learned = Careers.apply(habituated, now)
        val chronicle = recordChronicle(state.people, learned, state.chronicle, state.causes, now, log, state.practices, practices)
        val merged = log.foldInto(state.causes)
        // Prune only when the cap is exceeded, keeping the live present, everything
        // still referenced, and the most significant of the rest — so the O(n) prune
        // runs rarely and forgets churn, not history.
        val causes = if (merged.size > GRAPH_CAP) {
            merged.retain(GRAPH_LOW, GRAPH_RECENT, pinnedCauses(learned, chronicle, tasks))
        } else {
            merged
        }
        return state.copy(
            clock = now,
            people = learned,
            chronicle = chronicle,
            tasks = tasks,
            causes = causes,
            evidence = evidence.foldInto(state.evidence),
            // The same entity evidence that soured or warmed a guest also, in slow
            // aggregate, becomes the character of the departments and the hotel.
            culture = evidence.applyCulture(state.culture),
            practices = practices,
        )
    }

    /**
     * Every cause a still-living part of the world points at: a memory or belief's
     * provenance, a task's origin, a chronicle milestone's chain. The pruner must
     * keep all of these so nothing anyone still holds is left dangling.
     */
    private fun pinnedCauses(people: List<Person>, chronicle: List<ChronicleEntry>, tasks: List<HotelTask>): Set<CauseId> {
        val pinned = HashSet<CauseId>()
        for (person in people) {
            person.memories.forEach { m -> m.causeId?.let { pinned.add(it) } }
            person.knowledge.all.forEach { b -> b.causeId?.let { pinned.add(it) } }
        }
        chronicle.forEach { pinned.addAll(it.causeIds) }
        tasks.forEach { pinned.addAll(it.causeIds) }
        return pinned
    }

    /**
     * A cause node for each task the operation newly opened, stamped onto the task
     * itself so everything it later leads to — its completion, or its going overdue
     * and souring a guest — can trace back to when it arose.
     */
    private fun recordNewTasks(before: List<HotelTask>, after: List<HotelTask>, log: CauseLog): List<HotelTask> {
        val known = before.map { it.id }.toSet()
        return after.map { task ->
            if (task.id in known) return@map task
            val cause = log.emit(
                CauseType.TASK_CREATED,
                summaryKey = "task.${task.type.name.lowercase()}",
                significance = task.priority * 0.4,
                actors = task.requestedBy?.let { setOf(it) } ?: emptySet(),
                subjects = setOf("task:${task.id.value}"),
                location = task.locationId,
                metadata = mapOf("id" to task.id.value, "type" to task.type.name),
            )
            task.copy(causeIds = task.causeIds + cause)
        }
    }

    /**
     * A guest-facing task the guest asked for that has just lapsed past its
     * deadline unattended is real neglect: the guest's satisfaction falls and their
     * frustration rises — and *whether they complain about it remains their own
     * decision*, taken later by the ordinary conversation logic, never forced here.
     * The souring reflects significance back on the task that went unserved.
     */
    private fun applyOverdueConsequences(
        before: List<HotelTask>,
        after: List<HotelTask>,
        people: List<Person>,
        now: SimTime,
        log: CauseLog,
        evidence: EvidenceLog,
    ): List<Person> {
        val wasOpen = before.filter { it.isOpen }.map { it.id }.toSet()
        val lapsed = after.filter {
            it.status == HotelTaskStatus.EXPIRED && it.id in wasOpen && it.type.guestFacing && it.requestedBy != null
        }
        if (lapsed.isEmpty()) return people
        val byId = people.associateBy { it.id }.toMutableMap()
        for (task in lapsed) {
            val requesterId = task.requestedBy ?: continue
            val requester = byId[requesterId] ?: continue
            val stay = requester.stay ?: continue
            // Only real neglect sours a guest: they must still be there, waiting, when
            // the request lapses. A guest who has moved on was not kept waiting.
            if (requester.location.roomId != task.locationId) continue
            val overdue = log.emit(
                CauseType.TASK_OVERDUE,
                summaryKey = "task.overdue.${task.type.name.lowercase()}",
                significance = task.priority * 0.5,
                actors = setOf(requesterId),
                subjects = setOf("task:${task.id.value}"),
                location = task.locationId,
                parents = task.causeIds.map { it to CauseRelation.CAUSED },
            )
            val drop = if (stay.expectations.contains(GuestValue.SPEED)) OVERDUE_SATISFACTION_SPEED else OVERDUE_SATISFACTION
            log.emit(
                CauseType.SATISFACTION_CHANGE,
                summaryKey = "satisfaction.neglected",
                significance = drop * 2,
                actors = setOf(requesterId),
                subjects = setOf("task:${task.id.value}"),
                location = task.locationId,
                parents = listOf(overdue to CauseRelation.CAUSED),
            )
            // The consequence reflects back: the unattended task mattered because it soured a guest.
            log.reinforce(overdue, drop)
            // The guest's view of the hotel — and the responsible department — takes a knock.
            evidence.entity(EntityId.HOTEL, EvidenceDimension.RESPONSIVENESS, BAD, drop * 4, requesterId, setOf(overdue))
            evidence.entity(EntityId.HOTEL, EvidenceDimension.RELIABILITY, BAD, drop * 3, requesterId, setOf(overdue))
            evidence.entity(EntityId.department(task.department), EvidenceDimension.RELIABILITY, BAD, drop * 4, requesterId, setOf(overdue))
            byId[requesterId] = requester.copy(
                // Neglect bears only on speed and reliability — it does not rewrite the whole opinion.
                stay = stay.witnessing(
                    Triple(PerceptionDimension.SPEED, BAD, PERCEPTION_UNIT),
                    Triple(PerceptionDimension.RELIABILITY, BAD, PERCEPTION_UNIT * 0.8),
                ),
                emotions = requester.emotions.stirred(mapOf(EmotionKind.FRUSTRATION to drop * 2)),
            )
        }
        return people.map { byId.getValue(it.id) }
    }

    /**
     * Turn just-completed ATTEND actions into resolved tasks. A guest-facing task
     * finished while its requester is present becomes a small service interaction —
     * both remember it, gratitude and goodwill grow — which is how ordinary work
     * puts staff and guests together.
     */
    private fun resolveTasks(
        byId: MutableMap<PersonId, Person>,
        tasks: List<HotelTask>,
        now: SimTime,
        log: CauseLog,
        evidence: EvidenceLog,
    ): List<HotelTask> {
        val open = tasks.associateBy { it.id }.toMutableMap()
        for (person in byId.values.sortedBy { it.id.value }) {
            val action = person.action
            if (action.verb != ActionVerb.ATTEND || action.phase != ActionPhase.COMPLETED) continue
            val task = action.targetTaskId?.let { open[it] } ?: continue
            if (!task.isOpen) continue
            val completedCause = log.emit(
                CauseType.TASK_COMPLETED,
                summaryKey = "task.completed.${task.type.name.lowercase()}",
                significance = task.priority * 0.5,
                actors = setOf(person.id),
                subjects = setOf("task:${task.id.value}"),
                location = task.locationId,
            )
            recordWorkEvidence(task, person.id, byId, completedCause, evidence)
            open[task.id] = task.copy(status = HotelTaskStatus.COMPLETED, assignedTo = person.id, causeIds = task.causeIds + completedCause)
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
                serveGuest(task, person.id, requesterId, byId, now, log, completedCause, evidence)
            }
        }
        return open.values.toList()
    }

    /** Completing work is professional evidence — competence and reliability — for the staff who saw it. */
    private fun recordWorkEvidence(
        task: HotelTask,
        workerId: PersonId,
        byId: MutableMap<PersonId, Person>,
        completedCause: CauseId,
        evidence: EvidenceLog,
    ) {
        // Work done is the department's character too — whether or not anyone was watching.
        val dept = EntityId.department(task.department)
        evidence.entity(dept, EvidenceDimension.COMPETENCE, GOOD, WORK_EVIDENCE, workerId, setOf(completedCause))
        evidence.entity(dept, EvidenceDimension.RELIABILITY, GOOD, WORK_EVIDENCE * 0.8, workerId, setOf(completedCause))
        val observers = byId.values
            .filter { it.role.isStaff && it.id != workerId && it.location.roomId == task.locationId }
            .map { it.id }.toSet()
        if (observers.isEmpty()) return
        evidence.professional(workerId, ProfessionalDimension.COMPETENCE, GOOD, WORK_EVIDENCE, observers, setOf(completedCause))
        evidence.professional(workerId, ProfessionalDimension.RELIABILITY, GOOD, WORK_EVIDENCE, observers, setOf(completedCause))
        if (task.type == HotelTaskType.SHIFT_HANDOVER) {
            evidence.professional(workerId, ProfessionalDimension.PUNCTUALITY, GOOD, WORK_EVIDENCE, observers, setOf(completedCause))
        }
    }

    /**
     * Let the chronicler notice its rare milestones, then anchor each new line to
     * the real interactions that produced it: the entry carries the contributing
     * cause ids, a CHRONICLE_SIGNIFICANCE node ties them together, and each
     * contributing cause is reinforced — a bond written into the chronicle is proof
     * that the exchanges which built it truly mattered. The chronicler stays pure;
     * all causal wiring lives here, where the graph is.
     */
    private fun recordChronicle(
        before: List<Person>,
        current: List<Person>,
        previous: List<ChronicleEntry>,
        graph: CausalGraph,
        now: SimTime,
        log: CauseLog,
        practicesBefore: PracticeRegistry,
        practicesAfter: PracticeRegistry,
    ): List<ChronicleEntry> {
        val updated = Chronicler.update(before, current, previous, now, practicesBefore, practicesAfter)
        if (updated.size == previous.size) return updated
        val result = previous.toMutableList()
        for (entry in updated.drop(previous.size)) {
            val causes = relevantCauses(graph, entry.involved)
            val marker = log.emit(
                CauseType.CHRONICLE_SIGNIFICANCE,
                summaryKey = "chronicle.recorded",
                significance = entry.significance,
                actors = entry.involved,
                metadata = mapOf("headline" to entry.headline),
                parents = causes.map { it to CauseRelation.REVEALED },
            )
            causes.forEach { log.reinforce(it, CHRONICLE_REINFORCE) }
            result += entry.copy(causeIds = (causes + marker).toSet())
        }
        return result
    }

    /** The recent, most significant interactions in the graph that concern the people in a milestone. */
    private fun relevantCauses(graph: CausalGraph, involved: Set<PersonId>): List<CauseId> {
        val subjectKeys = involved.map { "person:${it.value}" }.toSet()
        return graph.nodes.values
            .filter { it.type in CHRONICLE_SOURCE_TYPES }
            .filter { node -> node.actorIds.any { it in involved } || node.subjectIds.any { it in subjectKeys } }
            .sortedByDescending { it.simTime.epochMinutes }
            .take(CHRONICLE_CAUSE_LIMIT)
            .map { it.id }
    }

    /** A guest-facing task finished with the guest present: record the service and its consequences. */
    private fun serveGuest(
        task: HotelTask,
        serverId: PersonId,
        requesterId: PersonId,
        byId: MutableMap<PersonId, Person>,
        now: SimTime,
        log: CauseLog,
        completedCause: CauseId,
        evidence: EvidenceLog,
    ) {
        val requester = byId.getValue(requesterId)
        val service = log.emit(
            CauseType.SERVICE_INTERACTION,
            summaryKey = "service.${task.type.name.lowercase()}",
            significance = 0.4,
            actors = setOf(serverId, requesterId),
            location = task.locationId,
            parents = listOf(completedCause to CauseRelation.CAUSED),
        )
        // The guest forms a firsthand opinion of the server; co-located staff judge the
        // handling professionally; and the guest's view of the hotel warms a little.
        evidence.personal(serverId, StandingDimension.RESPONSIVENESS, GOOD, SERVICE_EVIDENCE, requesterId, causes = setOf(service))
        evidence.personal(serverId, StandingDimension.WARMTH, GOOD, SERVICE_EVIDENCE * 0.6, requesterId, causes = setOf(service))
        val staffObservers = byId.values.filter { it.role.isStaff && it.id != serverId && it.location.roomId == task.locationId }
            .map { it.id }.toSet()
        if (staffObservers.isNotEmpty()) {
            val cause = setOf(completedCause)
            evidence.professional(serverId, ProfessionalDimension.GUEST_HANDLING, GOOD, SERVICE_EVIDENCE, staffObservers, cause)
            evidence.professional(serverId, ProfessionalDimension.COMPETENCE, GOOD, SERVICE_EVIDENCE * 0.7, staffObservers, cause)
        }
        evidence.entity(EntityId.HOTEL, EvidenceDimension.RESPONSIVENESS, GOOD, SERVICE_EVIDENCE * 0.8, requesterId, setOf(service))
        // The department that did the work earns its own character for it — brisk, warm.
        val dept = EntityId.department(task.department)
        evidence.entity(dept, EvidenceDimension.RESPONSIVENESS, GOOD, SERVICE_EVIDENCE * 0.8, requesterId, setOf(service))
        evidence.entity(dept, EvidenceDimension.WARMTH, GOOD, SERVICE_EVIDENCE * 0.5, requesterId, setOf(service))
        val memCause = log.emit(
            CauseType.MEMORY_CREATED,
            summaryKey = "memory.was_helped",
            significance = 0.3,
            actors = setOf(requesterId),
            parents = listOf(service to CauseRelation.CAUSED),
        )
        log.emit(
            CauseType.SATISFACTION_CHANGE,
            summaryKey = "satisfaction.served",
            significance = 0.2,
            actors = setOf(requesterId),
            parents = listOf(service to CauseRelation.CAUSED),
        )
        // Attentive service is evidence on the axes it bears on — never a blanket bump.
        byId[requesterId] = stampLastMemory(
            requester.copy(
                memories = remember(requester, MemoryKind.WAS_HELPED, serverId, valence = 0.5, importance = 0.4, now = now),
                relationships = requester.relationships.adjust(
                    serverId,
                    mapOf(RelationDimension.GRATITUDE to 0.08, RelationDimension.FAMILIARITY to 0.05),
                ),
                acquaintances = requester.acquaintances + serverId,
                stay = requester.stay?.witnessing(
                    Triple(PerceptionDimension.SPEED, GOOD, PERCEPTION_UNIT),
                    Triple(PerceptionDimension.WELCOME, GOOD, PERCEPTION_UNIT * 0.6),
                    Triple(PerceptionDimension.STAFF_WARMTH, GOOD, PERCEPTION_UNIT * 0.8),
                    Triple(PerceptionDimension.RELIABILITY, GOOD, PERCEPTION_UNIT * 0.6),
                ),
            ),
            memCause,
        )
        val server = byId.getValue(serverId)
        byId[serverId] = server.copy(
            memories = remember(server, MemoryKind.HELPED_SOMEONE, requesterId, 0.4, 0.3, now),
            relationships = server.relationships.adjust(requesterId, mapOf(RelationDimension.FAMILIARITY to 0.05)),
            acquaintances = server.acquaintances + requesterId,
        )
    }

    /** Fold one or more perception observations into a guest's stay. */
    private fun GuestStay.witnessing(vararg updates: Triple<PerceptionDimension, Double, Double>): GuestStay {
        var picture = perception
        for ((dimension, direction, weight) in updates) picture = picture.witness(dimension, direction, weight)
        return copy(perception = picture)
    }

    /**
     * Re-read a guest's satisfaction from the multi-dimensional picture they have
     * formed, weighted by what they care about, and let their wish to return drift
     * toward how the stay is going and their warmth to the staff. Intention only
     * ever grows into an *option* — never a guaranteed booking.
     */
    private fun refreshGuestOutlook(stay: GuestStay, person: Person, everyone: List<Person>): GuestStay {
        val weights = PerceptionWeights.forGuest(stay.purpose, stay.expectations, person.personality)
        val satisfaction = stay.perception.satisfaction(weights)
        val staff = everyone.filter { it.role.isStaff }.map { it.id }.toSet()
        val warmth = person.relationships.all.filter { it.other in staff }.maxOfOrNull { it.warmth() } ?: 0.0
        val target = (satisfaction * 0.7 + warmth.coerceIn(0.0, 1.0) * 0.3).coerceIn(0.0, 1.0)
        val value = stay.returnIntention.value + (target - stay.returnIntention.value) * RETURN_DRIFT
        val implied = when {
            value >= RETURN_PLANNING -> ReturnStage.PLANNING
            value >= RETURN_INTENDING -> ReturnStage.INTENDING
            else -> ReturnStage.NONE
        }
        val stage = if (implied.ordinal > stay.returnIntention.stage.ordinal) implied else stay.returnIntention.stage
        return stay.copy(
            satisfaction = satisfaction,
            returnIntention = stay.returnIntention.copy(
                value = value,
                confidence = (stay.returnIntention.confidence + RETURN_CONFIDENCE_STEP).coerceAtMost(1.0),
                stage = stage,
            ),
        )
    }

    /** Attach a cause to the memory a sub-step just created (the most recent, still-unstamped one). */
    private fun stampLastMemory(person: Person, causeId: CauseId): Person {
        val memories = person.memories
        if (memories.isEmpty() || memories.last().causeId != null) return person
        return person.copy(memories = memories.dropLast(1) + memories.last().copy(causeId = causeId))
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
        log: CauseLog,
    ): Advance {
        val effective = effectiveActivity(person)
        val knowledge = perceive(person, everyone, now, log)
        val perceived = person.copy(
            needs = NeedDynamics.tick(person.needs, effective),
            knowledge = knowledge,
            emotions = EmotionDynamics.tick(person),
            // Satisfaction is a weighted read of the multi-dimensional picture the guest
            // has formed; return intention drifts toward that picture and their bonds.
            stay = person.stay?.let { refreshGuestOutlook(it, person, everyone) },
        )
        val withNeeds = applyRecall(perceived, everyone, now, log)

        val mustDecide = withNeeds.action.phase.isTerminal || reconsider(withNeeds, now)
        if (mustDecide) {
            val commitments = withNeeds.schedule.filter { it.isActiveAt(now.minuteOfDay) }
            val result = decider.decide(withNeeds, commitments, now, seed)
            // A selected decision is a cause, linked to the memories that materially informed it.
            val memoryCauses = withNeeds.memories
                .filter { it.id in result.record.relevantMemoryIds && it.causeId != null }
                .mapNotNull { it.causeId }
                .map { it to CauseRelation.MOTIVATED }
            val decisionCause = log.emit(
                CauseType.DECISION,
                summaryKey = "decision.${result.action.verb.name.lowercase()}",
                significance = DECISION_SIGNIFICANCE,
                actors = setOf(person.id),
                location = withNeeds.location.roomId,
                metadata = mapOf("verb" to result.action.verb.name),
                parents = memoryCauses,
            )
            // Close the loop: the record points at the cause it produced, so the
            // "Why?" layer can walk from a decision to its place in the graph.
            val record = result.record.copy(resultingCauseIds = result.record.resultingCauseIds + decisionCause)
            return applyDecision(withNeeds.copy(goals = result.goals, lastDecision = record), result.action, now)
        }
        return Advance(advanceAction(withNeeds, now), social = null)
    }

    /**
     * Update a person's knowledge from what they can see, and record any *firsthand
     * correction* — a confident belief they have just seen to be wrong — as a cause
     * node, stamping the corrected belief with its provenance. Routine re-observation
     * and first-time learning leave no node; only a real overturning does.
     */
    private fun perceive(person: Person, everyone: List<Person>, now: SimTime, log: CauseLog): KnowledgeBase {
        var kb = Perception.observe(person, everyone, now)
        val corrections = Perception.firsthandCorrections(person.knowledge, kb, now)
        for (correction in corrections) {
            val cause = log.emit(
                CauseType.BELIEF_CORRECTED,
                summaryKey = "belief.corrected.${topicKind(correction.topic)}",
                significance = BELIEF_CORRECTION_SIGNIFICANCE,
                actors = setOf(person.id),
                subjects = setOf(subjectKeyOf(correction.topic)),
                location = person.location.roomId,
                metadata = mapOf("was" to correction.oldValue, "now" to correction.newValue),
            )
            kb = kb.stamp(correction.topic, cause)
        }
        return kb
    }

    private fun topicKind(topic: FactTopic): String = when (topic) {
        is FactTopic.RoomOccupancy -> "occupancy"
        is FactTopic.Whereabouts -> "whereabouts"
        is FactTopic.PersonMood -> "mood"
        is FactTopic.NotableGuest -> "notable"
    }

    private fun subjectKeyOf(topic: FactTopic): String = when (topic) {
        is FactTopic.RoomOccupancy -> "room:${topic.room.value}"
        is FactTopic.Whereabouts -> "person:${topic.person.value}"
        is FactTopic.PersonMood -> "person:${topic.person.value}"
        is FactTopic.NotableGuest -> "person:${topic.person.value}"
    }

    /**
     * Bring a memory to mind if the present moment calls for one, letting it
     * colour the person's feelings (and, through those, their next choice). Only
     * a contextually relevant memory surfaces, and only its recall stats and the
     * rememberer's emotions change — never the objective record.
     */
    private fun applyRecall(person: Person, everyone: List<Person>, now: SimTime, log: CauseLog): Person {
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
        // Recall is a cause only when it genuinely moves the person — a faint,
        // half-noticed memory colouring the mood is background churn, not a recorded
        // consequence. Recording only material stirs keeps meaningful history from
        // being buried (and pruned away) under routine reminiscence.
        val stir = result.emotionDeltas.values.maxOfOrNull { kotlin.math.abs(it) } ?: 0.0
        if (stir >= RECALL_MATERIAL) {
            log.emit(
                CauseType.MEMORY_RECALLED,
                summaryKey = "recall.${recalled.kind.name.lowercase()}",
                significance = recalled.importance * 0.4,
                actors = setOf(person.id),
                location = person.location.roomId,
                parents = recalled.causeId?.let { listOf(it to CauseRelation.REMEMBERED_FROM) } ?: emptyList(),
            )
        }
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

    private fun resolveSocial(
        event: PendingSocial,
        byId: MutableMap<PersonId, Person>,
        seed: Long,
        now: SimTime,
        log: CauseLog,
        evidence: EvidenceLog,
    ) {
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

        // The exchange is a cause; the memories and relationship shifts it produced hang off it.
        val convCause = log.emit(
            CauseType.CONVERSATION_ACT,
            summaryKey = "conversation.${updatedActor.lastConversation?.act?.name?.lowercase() ?: "talk"}",
            significance = CONVERSATION_SIGNIFICANCE,
            actors = setOf(actor.id, target.id),
            location = actor.location.roomId,
            metadata = mapOf("reception" to (updatedActor.lastConversation?.reception?.name ?: "")),
        )
        val actorStamped = recordConversationEffects(actor, updatedActor, target.id, convCause, log)
        byId[event.target] = recordConversationEffects(target, updatedTarget, actor.id, convCause, log)
        recordConversationEvidence(actor.id, target.id, updatedActor.lastConversation, present, convCause, evidence)

        // A refused overture is a failed action; anything received lets the action run its course.
        byId[event.actor] = if (actorStamped.lastConversation?.reception == ConversationReception.REFUSED) {
            actorStamped.copy(
                action = actorStamped.action.copy(phase = ActionPhase.FAILED),
                behaviour = actorStamped.behaviour.recordFailure(actorStamped.action.signature()),
            )
        } else {
            actorStamped
        }
    }

    /** Attach causes to the memory and relationship changes a conversation produced for one party. */
    private fun recordConversationEffects(before: Person, after: Person, other: PersonId, convCause: CauseId, log: CauseLog): Person {
        var result = after
        if (after.memories.size > before.memories.size) {
            val memCause = log.emit(
                CauseType.MEMORY_CREATED,
                summaryKey = "memory.${after.memories.last().kind.name.lowercase()}",
                significance = after.memories.last().importance * 0.4,
                actors = setOf(after.id),
                parents = listOf(convCause to CauseRelation.CAUSED),
            )
            result = stampLastMemory(result, memCause)
        }
        // Group all the axis moves from this one exchange into a single relationship-change node.
        val moved = RelationDimension.entries.filter {
            kotlin.math.abs(after.relationships.with(other)[it] - before.relationships.with(other)[it]) > REL_MATERIAL
        }
        if (moved.isNotEmpty()) {
            log.emit(
                CauseType.RELATIONSHIP_CHANGE,
                summaryKey = "relationship.shift",
                significance = REL_CHANGE_SIGNIFICANCE,
                actors = setOf(after.id),
                subjects = setOf("person:${other.value}"),
                metadata = mapOf("axes" to moved.joinToString(",") { it.name.lowercase() }),
                parents = listOf(convCause to CauseRelation.CAUSED),
            )
        }
        return result
    }

    /**
     * A conversation is evidence about the people in it: a hand offered reads as
     * generosity and warmth, an agreed request as reliability, a bout of complaining
     * as a lapse of calm, a rebuff as coldness. Whoever was in earshot forms a
     * (weaker) view too. Nothing here decides a reputation — it records the basis.
     */
    private fun recordConversationEvidence(
        actorId: PersonId,
        targetId: PersonId,
        record: ConversationRecord?,
        present: Set<PersonId>,
        convCause: CauseId,
        evidence: EvidenceLog,
    ) {
        val conv = record ?: return
        val witnesses = present - actorId - targetId
        if (conv.reception == ConversationReception.REFUSED) {
            if (conv.act !in NEUTRAL_ON_REFUSAL) {
                evidence.personal(targetId, StandingDimension.WARMTH, BAD, CONVERSATION_EVIDENCE, actorId, causes = setOf(convCause))
            }
            return
        }
        if (conv.reception != ConversationReception.ACCEPTED) return
        val c = setOf(convCause)
        val w = witnesses
        when (conv.act) {
            ConversationAct.OFFER_HELP -> {
                evidence.personal(actorId, StandingDimension.GENEROSITY, GOOD, CONVERSATION_EVIDENCE, targetId, w, c)
                evidence.personal(actorId, StandingDimension.WARMTH, GOOD, CONVERSATION_EVIDENCE * 0.6, targetId, w, c)
            }
            ConversationAct.REQUEST_HELP -> {
                evidence.personal(targetId, StandingDimension.RELIABILITY, GOOD, CONVERSATION_EVIDENCE, actorId, w, c)
                evidence.personal(targetId, StandingDimension.WARMTH, GOOD, CONVERSATION_EVIDENCE * 0.6, actorId, w, c)
            }
            ConversationAct.THANK, ConversationAct.PRAISE ->
                evidence.personal(actorId, StandingDimension.WARMTH, GOOD, CONVERSATION_EVIDENCE * 0.5, targetId, w, c)
            ConversationAct.COMPLAIN ->
                evidence.personal(actorId, StandingDimension.CALMNESS, BAD, CONVERSATION_EVIDENCE, targetId, w, c)
            else -> Unit
        }
    }

    private fun applyEvidence(people: List<Person>, evidence: EvidenceLog): List<Person> {
        if (evidence.isEmpty) return people
        val byId = people.associateBy { it.id }.toMutableMap()
        evidence.applyStandings(byId)
        return people.map { byId.getValue(it.id) }
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
        const val PERCEPTION_UNIT = 1.0
        const val RETURN_DRIFT = 0.003
        const val RETURN_CONFIDENCE_STEP = 0.001
        const val RETURN_INTENDING = 0.5
        const val RETURN_PLANNING = 0.75
        const val OVERDUE_SATISFACTION = 0.03
        const val OVERDUE_SATISFACTION_SPEED = 0.05
        const val DECISION_SIGNIFICANCE = 0.3
        const val BELIEF_CORRECTION_SIGNIFICANCE = 0.15
        const val RECALL_MATERIAL = 0.08
        const val CONVERSATION_SIGNIFICANCE = 0.35
        const val REL_CHANGE_SIGNIFICANCE = 0.3
        const val REL_MATERIAL = 0.03
        const val GRAPH_CAP = 6_000
        const val GRAPH_LOW = 4_000
        const val GRAPH_RECENT = 3_000
        const val CHRONICLE_REINFORCE = 0.2
        const val CHRONICLE_CAUSE_LIMIT = 6

        // Evidence direction and per-event weights (Phase 7B).
        const val GOOD = 1.0
        const val BAD = -1.0
        const val SERVICE_EVIDENCE = 1.0
        const val WORK_EVIDENCE = 0.6
        const val CONVERSATION_EVIDENCE = 0.8

        // Conversation acts that carry no coldness when turned away (they were already prickly).
        val NEUTRAL_ON_REFUSAL = setOf(
            ConversationAct.COMPLAIN,
            ConversationAct.DISAGREE,
            ConversationAct.END_CONVERSATION,
            ConversationAct.REBUFF,
        )
        val CHRONICLE_SOURCE_TYPES = setOf(
            CauseType.RELATIONSHIP_CHANGE,
            CauseType.CONVERSATION_ACT,
            CauseType.SERVICE_INTERACTION,
            CauseType.MEMORY_CREATED,
        )

        // A rebuffed overture leaves a small sting — a little resentment, and a face now known.
        val REBUFFED = mapOf(
            RelationDimension.FAMILIARITY to 0.02,
            RelationDimension.RESENTMENT to 0.05,
        )
    }
}
