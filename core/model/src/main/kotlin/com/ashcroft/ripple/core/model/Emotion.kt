package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * Transient feelings, distinct from long-running needs. Emotions spike from
 * events (a kind word, an interruption, a moment alone) and fade on their own.
 * They colour decisions while they last — an anxious person hesitates, an
 * excited one reaches out — but they are not goals in themselves.
 */
@Serializable
enum class EmotionKind {
    HAPPINESS,
    ANXIETY,
    FRUSTRATION,
    EMBARRASSMENT,
    LONELINESS,
    CONFIDENCE,
    EXCITEMENT,
    GUILT,
}

/**
 * A person's current emotional weather. Each emotion runs 0.0 (absent) to 1.0
 * (overwhelming). Immutable; both stirring an emotion and letting it fade
 * return new instances so ticks stay pure.
 */
@Serializable
data class EmotionState(private val levels: Map<EmotionKind, Double> = emptyMap()) {
    operator fun get(kind: EmotionKind): Double = levels[kind] ?: 0.0

    val strongest: EmotionKind?
        get() = levels.filterValues { it > FELT_THRESHOLD }.maxByOrNull { it.value }?.key

    fun with(kind: EmotionKind, value: Double): EmotionState =
        EmotionState(levels + (kind to value.coerceIn(0.0, 1.0)))

    /** Stir one or more emotions by a delta, clamped to [0,1]. */
    fun stirred(deltas: Map<EmotionKind, Double>): EmotionState {
        if (deltas.isEmpty()) return this
        val next = levels.toMutableMap()
        for ((kind, delta) in deltas) {
            next[kind] = (get(kind) + delta).coerceIn(0.0, 1.0)
        }
        return EmotionState(next)
    }

    /**
     * Let every emotion decay toward calm over [minutes]. Feelings do not last
     * forever; the rate is gentle so a strong moment still echoes for a while.
     */
    fun decayed(minutes: Int): EmotionState {
        if (levels.isEmpty()) return this
        val retained = Math.pow(RETENTION_PER_MINUTE, minutes.toDouble())
        return EmotionState(
            levels.mapValues { (_, v) -> (v * retained).let { if (it < FLOOR) 0.0 else it } },
        )
    }

    companion object {
        const val FELT_THRESHOLD = 0.2
        private const val RETENTION_PER_MINUTE = 0.995
        private const val FLOOR = 0.02
        val CALM = EmotionState()
    }
}
