package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.EmotionState
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.TraitKind

/**
 * How feeling settles between events. Emotions fade toward calm, but not toward
 * the *same* calm for everyone: each person has a faint temperamental baseline —
 * the outgoing run a little happy, the impatient a little frustrated, the
 * ambitious a little driven, the sociable a little lonely when by themselves.
 * These gentle pulls are what keep the cast's feelings varied rather than all
 * collapsing onto whatever happened most recently. Events still dominate; this
 * only colours the quiet moments, and never assigns an emotion at random.
 */
internal object EmotionDynamics {
    private const val BASE = 0.35
    private const val LONELY_NEED = 0.4

    fun tick(person: Person): EmotionState {
        var e = person.emotions.decayed(decayMinutes(person))
        // A person's characteristic feeling never fades below a gentle floor, so
        // temperament keeps colouring the quiet moments; events push above it.
        for ((emotion, floor) in baselines(person)) {
            if (e[emotion] < floor) e = e.with(emotion, floor)
        }
        return e
    }

    private fun baselines(person: Person): Map<EmotionKind, Double> {
        val p = person.personality
        val out = HashMap<EmotionKind, Double>()
        if (p[TraitKind.EXTRAVERSION] > 0.6f) out[EmotionKind.HAPPINESS] = BASE
        if (p[TraitKind.PATIENCE] < 0.4f) out[EmotionKind.FRUSTRATION] = BASE
        if (p[TraitKind.AMBITION] > 0.6f) out[EmotionKind.CONFIDENCE] = BASE
        if (p[TraitKind.OPENNESS] > 0.6f) out[EmotionKind.EXCITEMENT] = BASE * 0.85
        if (p[TraitKind.SOCIABILITY] > 0.6f && person.needs[NeedKind.SOCIAL] < LONELY_NEED) {
            out[EmotionKind.LONELINESS] = BASE
        }
        if (p[TraitKind.AGREEABLENESS] < 0.35f) out[EmotionKind.ANXIETY] = BASE * 0.8
        return out
    }

    /** Patient people let feelings settle a touch faster; the impatient hold onto them. */
    private fun decayMinutes(person: Person): Int = if (person.personality[TraitKind.PATIENCE] > 0.6f) 2 else 1
}
