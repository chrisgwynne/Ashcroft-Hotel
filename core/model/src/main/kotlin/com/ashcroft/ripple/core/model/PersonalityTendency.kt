package com.ashcroft.ripple.core.model

/**
 * A readable behavioural tendency, shown to the player *instead of* raw trait
 * numbers. A tendency is only claimed once there is enough accumulated evidence
 * for it — one helpful act does not make someone "always helpful". Tendencies
 * describe what a person has repeatedly been seen to do, not a hidden label.
 */
enum class Tendency(val label: String) {
    OFTEN_HELPS("Often helps others"),
    AVOIDS_CONFRONTATION("Avoids confrontation"),
    SPEAKS_UP("Speaks their mind"),
    QUICK_TO_THANK("Quick to show thanks"),
    GENEROUS_WITH_PRAISE("Generous with praise"),
    PREFERS_ROUTINE("Prefers familiar routines"),
    SEEKS_RECOGNITION("Seeks recognition"),
    KEEPS_TO_THEMSELVES("Keeps to themselves"),
    STEADY_UNDER_PRESSURE("Steady under pressure"),
}

/**
 * Reads a person's accumulated behaviour into the tendencies that are
 * well-enough evidenced to state. This is a projection for display and coarse
 * reasoning; it never invents a trait the record does not support.
 */
object Tendencies {
    const val EVIDENCE_THRESHOLD = 3
    const val HELP = "help"
    const val CONFRONT = "confront"
    const val AVOID = "avoid"
    const val THANK = "thank"
    const val PRAISE = "praise"
    const val RECOGNITION = "recognition"

    fun of(person: Person): List<Tendency> {
        val e = person.tendencyEvidence
        val result = mutableListOf<Tendency>()
        if (e.count(HELP) >= EVIDENCE_THRESHOLD) result += Tendency.OFTEN_HELPS
        if (e.count(AVOID) >= EVIDENCE_THRESHOLD && e.count(CONFRONT) < EVIDENCE_THRESHOLD) result += Tendency.AVOIDS_CONFRONTATION
        if (e.count(CONFRONT) >= EVIDENCE_THRESHOLD) result += Tendency.SPEAKS_UP
        if (e.count(THANK) >= EVIDENCE_THRESHOLD) result += Tendency.QUICK_TO_THANK
        if (e.count(PRAISE) >= EVIDENCE_THRESHOLD) result += Tendency.GENEROUS_WITH_PRAISE
        if (e.count(RECOGNITION) >= EVIDENCE_THRESHOLD) result += Tendency.SEEKS_RECOGNITION
        if (dominatesRoutine(person)) result += Tendency.PREFERS_ROUTINE
        if (person.acquaintances.size <= 1 && person.behaviour.recentActions.size >= WINDOW_FILLED) {
            result += Tendency.KEEPS_TO_THEMSELVES
        }
        if (person.memories.count { it.valence < 0 } >= EVIDENCE_THRESHOLD &&
            person.emotions[EmotionKind.ANXIETY] < CALM_CEILING
        ) {
            result += Tendency.STEADY_UNDER_PRESSURE
        }
        return result
    }

    private fun Map<String, Int>.count(key: String): Int = this[key] ?: 0

    private fun dominatesRoutine(person: Person): Boolean {
        val actions = person.behaviour.recentActions
        if (actions.size < WINDOW_FILLED) return false
        val commonest = actions.groupingBy { it }.eachCount().maxByOrNull { it.value }?.value ?: 0
        return commonest >= actions.size * 3 / 4
    }

    private const val WINDOW_FILLED = 6
    private const val CALM_CEILING = 0.3
}
