package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.Commitment
import com.ashcroft.ripple.core.model.DecisionId
import com.ashcroft.ripple.core.model.DecisionRecord
import com.ashcroft.ripple.core.model.Goal
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.SimTime

/** The product of one decision: the committed action, its record, and refreshed goals. */
data class DecisionResult(
    val action: ActionState,
    val record: DecisionRecord,
    val goals: List<Goal>,
)

/**
 * The general-purpose decision engine. Given what a person currently knows,
 * needs, wants, remembers and can physically do, it decides which action they
 * would choose next — not what story should happen. It is a pure function of
 * the actor, the world view and the seed, so decisions are fully reproducible.
 */
class DecisionMaker(private val world: WorldQueries) {
    private val goalGenerator = GoalGenerator()
    private val candidateProvider = ActionCandidateProvider(world)
    private val scorer = ActionScorer()
    private val explanations = ExplanationBuilder(
        roomName = { world.roomName(it) },
        personName = { id -> world.person(id)?.name ?: "someone" },
    )

    fun decide(actor: Person, activeCommitments: List<Commitment>, now: SimTime, seed: Long): DecisionResult {
        val goals = mergeGoals(actor, goalGenerator.generate(actor, now, activeCommitments))
        val currentRoom = actor.location.roomId
        val perceived = perceive(actor, currentRoom)
        val opportunities = candidateProvider.knownOpportunities(actor, activeCommitments)
        val openTasks = world.openTasksFor(actor)
        val candidates = candidateProvider.candidates(actor, currentRoom, perceived, opportunities, openTasks)

        val context = DecisionContext(
            actor = actor,
            currentRoom = currentRoom,
            perceivedPeople = perceived,
            knownOpportunities = opportunities,
            activeGoals = goals,
            activeCommitments = activeCommitments,
            recentMemories = actor.memories.takeLast(RECENT_MEMORY_WINDOW),
            availableActions = candidates,
            availableTasks = openTasks,
            simTime = now,
            culture = world.cultureFor(actor),
            customaryPractices = world.customaryPracticesFor(actor),
        )

        if (candidates.isEmpty()) {
            return DecisionResult(waiting(actor, now), emptyRecord(actor, now), goals)
        }

        val scores = candidates.map { scorer.score(it, context, seed) }
        val chosen = scores.maxByOrNull { it.finalScore }!!

        val action = ActionState(
            verb = chosen.candidate.verb,
            targetRoom = chosen.candidate.targetRoom,
            targetPerson = chosen.candidate.targetPerson,
            phase = ActionPhase.ACCEPTED,
            startedAt = now,
            plannedMinutes = chosen.candidate.plannedMinutes,
            elapsedMinutes = 0,
            reasonSummary = explanations.summaryLine(chosen),
            targetTaskId = chosen.candidate.targetTaskId,
        )
        val record = DecisionRecord(
            id = DecisionId("d:${actor.id.value}:${now.epochMinutes}"),
            actorId = actor.id,
            simTime = now,
            chosenAction = chosen.candidate,
            consideredActions = scores,
            relevantGoalIds = goals.map { it.id }.toSet(),
            relevantMemoryIds = actor.memories.filter { it.subjectId == chosen.candidate.targetPerson }.map { it.id }.toSet(),
            resultingCauseIds = emptySet(),
        )
        return DecisionResult(action, record, goals)
    }

    /**
     * People the actor is actually aware of right now — those in the same room
     * *and* within their attention (a busy worker notices little, an idle one
     * notices all). Only public attributes are read; sharing a room is necessary
     * but not sufficient for a social approach.
     */
    private fun perceive(actor: Person, currentRoom: com.ashcroft.ripple.core.model.RoomId?): List<PerceivedPerson> {
        if (currentRoom == null) return emptyList()
        val inRoom = world.peopleInRoom(currentRoom).filter { it != actor.id }
        val crowding = inRoom.size
        return inRoom.mapNotNull { id ->
            val other = world.person(id) ?: return@mapNotNull null
            val known = actor.acquaintances.contains(id)
            if (!Awareness.notices(actor, other.role, known, crowding)) return@mapNotNull null
            PerceivedPerson(
                id = id,
                name = other.name,
                role = other.role,
                sentiment = actor.sentimentToward(id),
                alreadyKnown = known,
            )
        }
    }

    private fun mergeGoals(actor: Person, fresh: List<Goal>): List<Goal> {
        val existing = actor.goals.associateBy { it.id }
        return fresh.map { g -> existing[g.id]?.let { g.copy(createdAt = it.createdAt, originCauseIds = it.originCauseIds) } ?: g }
    }

    private fun waiting(actor: Person, now: SimTime): ActionState =
        ActionState.IDLE.copy(startedAt = now, phase = ActionPhase.IN_PROGRESS, plannedMinutes = 5, reasonSummary = "Nothing to do here")

    private fun emptyRecord(actor: Person, now: SimTime): DecisionRecord = DecisionRecord(
        id = DecisionId("d:${actor.id.value}:${now.epochMinutes}"),
        actorId = actor.id,
        simTime = now,
        chosenAction = com.ashcroft.ripple.core.model.ActionCandidate(
            com.ashcroft.ripple.core.model.ActionId("WAIT||"),
            com.ashcroft.ripple.core.model.ActionVerb.WAIT,
            null,
            null,
            5,
        ),
        consideredActions = emptyList(),
        relevantGoalIds = emptySet(),
        relevantMemoryIds = emptySet(),
        resultingCauseIds = emptySet(),
    )

    fun explanationFor(record: DecisionRecord): ActionExplanation = explanations.explain(record)

    private companion object {
        const val RECENT_MEMORY_WINDOW = 12
    }
}
