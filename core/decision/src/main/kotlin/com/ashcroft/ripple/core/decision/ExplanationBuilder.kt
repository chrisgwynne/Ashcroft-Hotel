package com.ashcroft.ripple.core.decision

import com.ashcroft.ripple.core.model.ActionCandidate
import com.ashcroft.ripple.core.model.ActionScore
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.DecisionRecord
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId

/** A plausible alternative and why it scored lower. */
data class AlternativeExplanation(val label: String, val whyLower: String)

/** A fully human-readable account of one decision, for the "Why?" panel. */
data class ActionExplanation(
    val headline: String,
    val summary: String,
    val positives: List<String>,
    val negatives: List<String>,
    val alternatives: List<AlternativeExplanation>,
)

/**
 * Turns recorded, structured scoring factors into plain language. It never
 * invents reasons and never emits hidden chain-of-thought — every line is a
 * rendering of a [com.ashcroft.ripple.core.model.ScoreComponent] that actually
 * contributed to the decision.
 */
class ExplanationBuilder(
    private val roomName: (RoomId) -> String,
    private val personName: (PersonId) -> String,
) {
    fun summaryLine(score: ActionScore): String {
        val top = score.topPositive()?.explanationKey
        return if (top != null) "${headline(score.candidate)} — $top" else headline(score.candidate)
    }

    fun explain(record: DecisionRecord): ActionExplanation {
        val chosenScore = record.consideredActions.firstOrNull { it.candidate == record.chosenAction }
            ?: record.consideredActions.maxByOrNull { it.finalScore }
            ?: return ActionExplanation(headline(record.chosenAction), "", emptyList(), emptyList(), emptyList())

        val positives = chosenScore.components.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.explanationKey }
        val negatives = chosenScore.components.filter { it.value < 0 }.sortedBy { it.value }.map { it.explanationKey }

        val alternatives = record.consideredActions
            .filter { it.candidate != record.chosenAction }
            .sortedByDescending { it.finalScore }
            .take(3)
            .map { alt ->
                val reason = alt.topNegative()?.explanationKey
                    ?: "it simply mattered less than ${chosenScore.topPositive()?.explanationKey ?: "the chosen action"}"
                AlternativeExplanation(headline(alt.candidate), "scored lower — $reason")
            }

        return ActionExplanation(
            headline = headline(record.chosenAction),
            summary = positives.firstOrNull() ?: "no single factor stood out",
            positives = positives,
            negatives = negatives,
            alternatives = alternatives,
        )
    }

    fun headline(candidate: ActionCandidate): String {
        val where = candidate.targetRoom?.let { roomName(it) }
        val who = candidate.targetPerson?.let { personName(it) } ?: "someone"
        val at = where?.let { " at $it" }.orEmpty()
        val inRoom = where?.let { " in $it" }.orEmpty()
        return when (candidate.verb) {
            ActionVerb.WORK -> "Working$at"
            ActionVerb.EAT -> "Eating$at"
            ActionVerb.SLEEP -> "Resting$inRoom"
            ActionVerb.WASH -> "Freshening up"
            ActionVerb.RELAX, ActionVerb.RETURN_HOME -> "Relaxing$inRoom"
            ActionVerb.TAKE_BREAK -> "Taking a break"
            ActionVerb.SOCIALISE -> if (where != null) "Sitting in $where" else "Socialising"
            ActionVerb.GREET -> "Greeting $who"
            ActionVerb.CONVERSE -> "Talking with $who"
            ActionVerb.WANDER -> "Wandering"
            ActionVerb.WAIT -> "Waiting"
        }
    }
}
