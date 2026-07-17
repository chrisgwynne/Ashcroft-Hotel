package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * The structured reasons an action scores as it does. Scoring is never a single
 * opaque number — it is a breakdown of these components, which the "Why?"
 * interface renders into plain language.
 */
@Serializable
enum class ScoreComponentType {
    NEED_RELIEF,
    COMMITMENT_FULFILMENT,
    GOAL_PROGRESS,
    PERSONALITY_FIT,
    HABIT_STRENGTH,
    EMOTIONAL_FIT,
    RELATIONSHIP_IMPACT,
    EXPECTED_REWARD,
    TIME_COST,
    ENERGY_COST,
    FINANCIAL_COST,
    SOCIAL_RISK,
    REPUTATION_RISK,
    UNCERTAINTY,
    ACCESS_DIFFICULTY,
    FUTURE_OPPORTUNITY_VALUE,
    COMMITMENT_CONFLICT,
    RECENT_REPETITION,
    CULTURE_FIT,
}

/** One contribution to an action's score. [value] may be negative (a cost). */
@Serializable
data class ScoreComponent(
    val type: ScoreComponentType,
    val value: Double,
    val explanationKey: String,
)

/**
 * A candidate action a person could take next. Candidates come only from what
 * the person can actually perceive and reach — never the full action space.
 */
@Serializable
data class ActionCandidate(
    val id: ActionId,
    val verb: ActionVerb,
    val targetRoom: RoomId?,
    val targetPerson: PersonId?,
    val plannedMinutes: Int,
    val targetTaskId: HotelTaskId? = null,
) {
    fun signature(): ActionSignature = ActionSignature(verb, targetRoom, targetPerson)
}

/**
 * The full scored evaluation of one candidate: the deterministic total of its
 * components plus a bounded, seeded stochastic nudge. Randomness may break a
 * close call; it must never let an implausible action routinely win.
 */
@Serializable
data class ActionScore(
    val candidate: ActionCandidate,
    val components: List<ScoreComponent>,
    val deterministicTotal: Double,
    val stochasticAdjustment: Double,
    val finalScore: Double,
) {
    fun topPositive(): ScoreComponent? = components.filter { it.value > 0 }.maxByOrNull { it.value }

    fun topNegative(): ScoreComponent? = components.filter { it.value < 0 }.minByOrNull { it.value }
}

@Serializable
@JvmInline
value class DecisionId(val value: String)

/**
 * A record of one decision: what was chosen, everything that was considered
 * (with scores), and the goals/memories that mattered. Retained for the "Why?"
 * interface and, later, the causal graph. Routine pathfinding steps are never
 * recorded — only meaningful decisions.
 */
@Serializable
data class DecisionRecord(
    val id: DecisionId,
    val actorId: PersonId,
    val simTime: SimTime,
    val chosenAction: ActionCandidate,
    val consideredActions: List<ActionScore>,
    val relevantGoalIds: Set<GoalId>,
    val relevantMemoryIds: Set<MemoryId>,
    val resultingCauseIds: Set<CauseId>,
)
