package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionCandidate
import com.ashcroft.ripple.core.model.ActionScore
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.CommitmentKind
import com.ashcroft.ripple.core.model.DeterministicRandom
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.GoalTarget
import com.ashcroft.ripple.core.model.GoalType
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.ScoreComponent
import com.ashcroft.ripple.core.model.ScoreComponentType
import com.ashcroft.ripple.core.model.TraitKind
import kotlin.math.abs
import kotlin.math.min

/**
 * Scores a candidate action as a breakdown of named components, never as a
 * single opaque number. Needs push utility *continuously* (no `if need < X`
 * thresholds); commitments, goals, personality, memories, costs and repetition
 * all contribute their own components. A bounded, seeded nudge can break a close
 * call but can never let an implausible action routinely beat a clearly better
 * one.
 */
class ActionScorer {
    fun score(candidate: ActionCandidate, context: DecisionContext, noiseSeed: Long): ActionScore {
        val actor = context.actor
        val components = mutableListOf<ScoreComponent>()

        needRelief(candidate, context, components)
        commitmentFulfilment(candidate, context, components)
        taskAttendance(candidate, context, components)
        goalProgress(candidate, context, components)
        personalityFit(candidate, actor, components)
        emotionalFit(candidate, actor, components)
        habitAndRepetition(candidate, actor, components)
        socialFactors(candidate, context, components)
        costs(candidate, context, components)
        commitmentConflict(candidate, context, components)

        val deterministicTotal = components.sumOf { it.value }
        val rng = DeterministicRandom(
            DeterministicRandom.seedOf(noiseSeed, actor.id.value.hashCode().toLong(), candidate.id.value.hashCode().toLong()),
        )
        val stochastic = rng.jitter(NOISE.toFloat()).toDouble()
        return ActionScore(candidate, components, deterministicTotal, stochastic, deterministicTotal + stochastic)
    }

    private fun needRelief(candidate: ActionCandidate, ctx: DecisionContext, out: MutableList<ScoreComponent>) {
        val needs = reliefNeeds(candidate.verb)
        if (needs.isEmpty()) return
        var total = 0.0
        var strongest = needs.first()
        var strongestDeficit = -1.0
        for (need in needs) {
            val deficit = (1f - ctx.actor.needs[need]).toDouble().coerceAtLeast(0.0)
            total += NEED_WEIGHT * deficit * deficit // non-linear: pressure grows as a need empties
            if (deficit > strongestDeficit) {
                strongestDeficit = deficit
                strongest = need
            }
        }
        out += ScoreComponent(ScoreComponentType.NEED_RELIEF, total, needExplanation(strongest))
    }

    private fun commitmentFulfilment(candidate: ActionCandidate, ctx: DecisionContext, out: MutableList<ScoreComponent>) {
        if (candidate.verb != ActionVerb.WORK) return
        val shift = ctx.activeCommitments.firstOrNull { it.kind == CommitmentKind.SHIFT } ?: return
        if (shift.location != candidate.targetRoom) return
        val conscientiousness = ctx.actor.personality[TraitKind.CONSCIENTIOUSNESS]
        val value = shift.strength * (0.5 + conscientiousness) * COMMITMENT_WEIGHT
        out += ScoreComponent(ScoreComponentType.COMMITMENT_FULFILMENT, value, "your shift is on")
    }

    private fun taskAttendance(candidate: ActionCandidate, ctx: DecisionContext, out: MutableList<ScoreComponent>) {
        if (candidate.verb != ActionVerb.ATTEND) return
        val task = ctx.availableTasks.firstOrNull { it.taskId == candidate.targetTaskId } ?: return
        val conscientiousness = ctx.actor.personality[TraitKind.CONSCIENTIOUSNESS]
        val onShift = ctx.activeCommitments.any { it.kind == CommitmentKind.SHIFT }
        var value = task.priority * (0.5 + conscientiousness) * TASK_WEIGHT
        if (onShift) value += TASK_ON_SHIFT
        // A sociable member of staff finds guest-facing work a little more appealing.
        if (task.guestFacing) value += ctx.actor.personality[TraitKind.SOCIABILITY] * TASK_GUEST_FACING
        out += ScoreComponent(ScoreComponentType.COMMITMENT_FULFILMENT, value, "there is a guest or job waiting")
    }

    private fun goalProgress(candidate: ActionCandidate, ctx: DecisionContext, out: MutableList<ScoreComponent>) {
        var value = 0.0
        for (goal in ctx.activeGoals) {
            value += when {
                goal.type == GoalType.SATISFY_NEED &&
                    goal.target is GoalTarget.Need &&
                    reliefNeeds(candidate.verb).contains((goal.target as GoalTarget.Need).kind) -> goal.priority * 0.4
                goal.type == GoalType.FULFIL_WORK && candidate.verb == ActionVerb.WORK -> goal.priority * 0.5
                goal.type == GoalType.GAIN_APPROVAL && (candidate.verb == ActionVerb.WORK || candidate.verb.social) -> goal.priority * 0.2
                else -> 0.0
            }
        }
        if (value != 0.0) out += ScoreComponent(ScoreComponentType.GOAL_PROGRESS, value, "it moves a current goal forward")
    }

    private fun personalityFit(candidate: ActionCandidate, actor: com.ashcroft.ripple.core.model.Person, out: MutableList<ScoreComponent>) {
        val value = when (candidate.verb) {
            ActionVerb.GREET, ActionVerb.CONVERSE, ActionVerb.SOCIALISE ->
                (actor.personality[TraitKind.SOCIABILITY] - 0.5) * PERSONALITY_WEIGHT
            ActionVerb.WORK ->
                ((actor.personality[TraitKind.CONSCIENTIOUSNESS] + actor.personality[TraitKind.AMBITION]) / 2f - 0.5) * PERSONALITY_WEIGHT
            ActionVerb.RELAX, ActionVerb.RETURN_HOME, ActionVerb.TAKE_BREAK ->
                (0.5 - actor.personality[TraitKind.CONSCIENTIOUSNESS]) * PERSONALITY_WEIGHT
            else -> 0.0
        }
        if (abs(value) > 1e-6) out += ScoreComponent(ScoreComponentType.PERSONALITY_FIT, value, "it suits their temperament")
    }

    private fun habitAndRepetition(
        candidate: ActionCandidate,
        actor: com.ashcroft.ripple.core.model.Person,
        out: MutableList<ScoreComponent>,
    ) {
        val recent = actor.behaviour.timesRecently(candidate.signature())
        if (isRoutine(candidate.verb) && recent > 0) {
            out += ScoreComponent(ScoreComponentType.HABIT_STRENGTH, min(recent, 3) * HABIT_STEP, "it is part of their routine")
        }
        if (isLeisure(candidate.verb) && recent > 1) {
            out += ScoreComponent(ScoreComponentType.RECENT_REPETITION, -min(recent, 4) * BOREDOM_STEP, "they have done this a lot lately")
        }
        val failures = actor.behaviour.failuresFor(candidate.signature())
        if (failures > 0) {
            out += ScoreComponent(ScoreComponentType.RECENT_REPETITION, -min(failures, 4) * FAILURE_STEP, "it has not worked out recently")
        }
    }

    private fun socialFactors(candidate: ActionCandidate, ctx: DecisionContext, out: MutableList<ScoreComponent>) {
        val target = candidate.targetPerson ?: return
        val perceived = ctx.perceivedPeople.firstOrNull { it.id == target }
        // Combine the remembered feeling (Phase 3) with the multidimensional bond (Phase 4):
        // warmth and trust draw people together; resentment and fear hold them back.
        val rel = ctx.actor.relationships.with(target)
        val bond = rel[RelationDimension.AFFECTION] + rel[RelationDimension.TRUST] + rel[RelationDimension.GRATITUDE] -
            rel[RelationDimension.RESENTMENT] - rel[RelationDimension.FEAR]
        val impact = (perceived?.sentiment ?: 0.0) + bond
        if (abs(impact) > 1e-6) {
            out += ScoreComponent(ScoreComponentType.RELATIONSHIP_IMPACT, impact * RELATIONSHIP_WEIGHT, sentimentExplanation(impact))
        }
        // Approaching anyone carries a little uncertainty; a stranger, a little social risk.
        out += ScoreComponent(ScoreComponentType.UNCERTAINTY, -UNCERTAINTY_COST, "they might not be receptive")
        if (perceived?.alreadyKnown == false) {
            out += ScoreComponent(ScoreComponentType.SOCIAL_RISK, -SOCIAL_RISK_COST, "they do not know this person yet")
        }
        // Diminishing relevance: having just talked with someone repeatedly, there is
        // less pull to do it again unless something has changed. Task-driven contact is
        // tracked separately and is not damped here.
        val repeats = ctx.actor.recentExchangesWith(target)
        if (repeats > 0) {
            out += ScoreComponent(ScoreComponentType.RECENT_REPETITION, -min(repeats, 5) * REPEAT_STEP, "they have talked recently")
        }
    }

    /**
     * Current feeling nudges choices: buoyant or lonely people reach out, anxious
     * or embarrassed ones hold back and retreat, frustration sours work, confidence
     * sweetens it. Emotions bias, never dictate — the weights are deliberately small.
     */
    private fun emotionalFit(candidate: ActionCandidate, actor: com.ashcroft.ripple.core.model.Person, out: MutableList<ScoreComponent>) {
        val e = actor.emotions
        var value = 0.0
        if (candidate.verb.social) {
            value += (
                e[EmotionKind.HAPPINESS] + e[EmotionKind.EXCITEMENT] + e[EmotionKind.LONELINESS] -
                    e[EmotionKind.ANXIETY] - e[EmotionKind.EMBARRASSMENT]
            ) * EMOTION_SOCIAL_WEIGHT
        }
        if (candidate.verb == ActionVerb.WORK) {
            value += (e[EmotionKind.CONFIDENCE] - e[EmotionKind.FRUSTRATION]) * EMOTION_WORK_WEIGHT
        }
        if (candidate.verb == ActionVerb.RETURN_HOME || candidate.verb == ActionVerb.RELAX) {
            value += (e[EmotionKind.ANXIETY] + e[EmotionKind.EMBARRASSMENT]) * EMOTION_RETREAT_WEIGHT
        }
        if (abs(value) > 1e-6) out += ScoreComponent(ScoreComponentType.EMOTIONAL_FIT, value, emotionExplanation(e.strongest))
    }

    private fun emotionExplanation(strongest: EmotionKind?): String = when (strongest) {
        EmotionKind.HAPPINESS, EmotionKind.EXCITEMENT -> "they are in good spirits"
        EmotionKind.LONELINESS -> "they are feeling alone"
        EmotionKind.ANXIETY -> "they feel on edge"
        EmotionKind.EMBARRASSMENT -> "they are still smarting from earlier"
        EmotionKind.FRUSTRATION -> "they are out of patience"
        EmotionKind.CONFIDENCE -> "they feel sure of themselves"
        EmotionKind.GUILT -> "something is weighing on them"
        null -> "their mood colours it"
    }

    private fun costs(candidate: ActionCandidate, ctx: DecisionContext, out: MutableList<ScoreComponent>) {
        val room = candidate.targetRoom
        if (room != null && room != ctx.currentRoom) {
            val minutes = travelMinutesHint(candidate)
            if (minutes > 0) out += ScoreComponent(ScoreComponentType.TIME_COST, -minutes * TIME_COST_STEP, "it is a walk away")
        }
        if (candidate.verb == ActionVerb.WORK) {
            val tired = (1f - ctx.actor.needs[NeedKind.REST]).toDouble()
            if (tired > 0.4) out += ScoreComponent(ScoreComponentType.ENERGY_COST, -tired * ENERGY_COST_WEIGHT, "they are tired")
        }
        if (isPaid(candidate.verb) && !ctx.actor.role.isStaff) {
            val scarcity = (1.0 - min(ctx.actor.money, MONEY_COMFORTABLE) / MONEY_COMFORTABLE.toDouble())
            out += ScoreComponent(ScoreComponentType.FINANCIAL_COST, -FINANCIAL_BASE - scarcity * FINANCIAL_SCARCITY, "it costs money")
        }
    }

    private fun commitmentConflict(candidate: ActionCandidate, ctx: DecisionContext, out: MutableList<ScoreComponent>) {
        val shift = ctx.activeCommitments.firstOrNull { it.kind == CommitmentKind.SHIFT } ?: return
        if (candidate.verb == ActionVerb.WORK || candidate.verb == ActionVerb.ATTEND || isNecessity(candidate.verb)) return
        out += ScoreComponent(ScoreComponentType.COMMITMENT_CONFLICT, -shift.strength * CONFLICT_WEIGHT, "they are supposed to be working")
    }

    private fun travelMinutesHint(candidate: ActionCandidate): Int = when (candidate.verb) {
        ActionVerb.SLEEP, ActionVerb.WASH, ActionVerb.RETURN_HOME -> 6
        else -> 4
    }

    private fun reliefNeeds(verb: ActionVerb): List<NeedKind> = when (verb) {
        ActionVerb.EAT -> listOf(NeedKind.HUNGER)
        ActionVerb.SLEEP -> listOf(NeedKind.REST)
        ActionVerb.WASH -> listOf(NeedKind.HYGIENE)
        ActionVerb.RELAX, ActionVerb.RETURN_HOME -> listOf(NeedKind.PRIVACY, NeedKind.COMFORT)
        ActionVerb.TAKE_BREAK -> listOf(NeedKind.AUTONOMY, NeedKind.COMFORT)
        ActionVerb.SOCIALISE, ActionVerb.GREET, ActionVerb.CONVERSE -> listOf(NeedKind.SOCIAL)
        ActionVerb.WORK, ActionVerb.ATTEND -> listOf(NeedKind.PURPOSE, NeedKind.RECOGNITION)
        ActionVerb.WANDER, ActionVerb.WAIT -> emptyList()
    }

    private fun needExplanation(need: NeedKind): String = when (need) {
        NeedKind.HUNGER -> "they are hungry"
        NeedKind.REST -> "they are tired"
        NeedKind.SOCIAL -> "they want company"
        NeedKind.HYGIENE -> "they want to freshen up"
        NeedKind.PRIVACY -> "they want some quiet"
        NeedKind.COMFORT -> "they want to be comfortable"
        NeedKind.SAFETY -> "they want to feel settled"
        NeedKind.PURPOSE -> "they want to feel useful"
        NeedKind.RECOGNITION -> "they want their work seen"
        NeedKind.AUTONOMY -> "they want time of their own"
    }

    private fun sentimentExplanation(s: Double): String =
        if (s >= 0) "they think well of this person" else "they have a poor memory of this person"

    private fun isRoutine(verb: ActionVerb) = verb == ActionVerb.WORK || verb == ActionVerb.EAT || verb == ActionVerb.SLEEP

    private fun isLeisure(verb: ActionVerb) =
        verb == ActionVerb.RELAX || verb == ActionVerb.SOCIALISE || verb == ActionVerb.TAKE_BREAK || verb == ActionVerb.WANDER

    private fun isNecessity(verb: ActionVerb) =
        verb == ActionVerb.EAT || verb == ActionVerb.SLEEP || verb == ActionVerb.WASH

    private fun isPaid(verb: ActionVerb) = verb == ActionVerb.EAT || verb == ActionVerb.SOCIALISE

    private companion object {
        const val NEED_WEIGHT = 1.4
        const val COMMITMENT_WEIGHT = 0.5
        const val PERSONALITY_WEIGHT = 0.4
        const val RELATIONSHIP_WEIGHT = 0.6
        const val EMOTION_SOCIAL_WEIGHT = 0.25
        const val EMOTION_WORK_WEIGHT = 0.2
        const val EMOTION_RETREAT_WEIGHT = 0.2
        const val HABIT_STEP = 0.03
        const val BOREDOM_STEP = 0.06
        const val FAILURE_STEP = 0.06
        const val REPEAT_STEP = 0.09
        const val UNCERTAINTY_COST = 0.05
        const val SOCIAL_RISK_COST = 0.10
        const val TIME_COST_STEP = 0.01
        const val ENERGY_COST_WEIGHT = 0.35
        const val FINANCIAL_BASE = 0.04
        const val FINANCIAL_SCARCITY = 0.20
        const val MONEY_COMFORTABLE = 200
        const val CONFLICT_WEIGHT = 0.45
        const val TASK_WEIGHT = 0.7
        const val TASK_ON_SHIFT = 0.2
        const val TASK_GUEST_FACING = 0.1
        const val NOISE = 0.15
    }
}
