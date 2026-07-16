package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * The needs a person feels pressure to satisfy. Values run 0f (desperate) to
 * 1f (fully satisfied). Needs create *pressure*, not commands — the activity
 * chooser weighs them alongside personality, commitments and opportunity. This
 * is a Phase 2 subset of the fuller need list described in the design.
 */
@Serializable
enum class NeedKind {
    HUNGER,
    REST,
    SOCIAL,
    HYGIENE,
    PRIVACY,
    PURPOSE,
}

/**
 * A person's current satisfaction across all needs. Immutable; every change
 * produces a new instance so simulation ticks stay pure and replayable.
 */
@Serializable
data class NeedState(private val values: Map<NeedKind, Float>) {
    operator fun get(kind: NeedKind): Float = values[kind] ?: DEFAULT

    /** The need under the most pressure (lowest satisfaction). */
    fun mostPressing(): NeedKind = NeedKind.entries.minBy { get(it) }

    fun with(kind: NeedKind, value: Float): NeedState =
        NeedState(values + (kind to value.coerceIn(0f, 1f)))

    /** Apply a per-need delta, clamped to [0,1]. */
    fun adjusted(deltas: Map<NeedKind, Float>): NeedState {
        if (deltas.isEmpty()) return this
        val next = values.toMutableMap()
        for ((kind, delta) in deltas) {
            next[kind] = (get(kind) + delta).coerceIn(0f, 1f)
        }
        return NeedState(next)
    }

    companion object {
        const val DEFAULT = 0.7f

        fun full(): NeedState = NeedState(NeedKind.entries.associateWith { 1f })

        fun of(vararg pairs: Pair<NeedKind, Float>): NeedState =
            NeedState(NeedKind.entries.associateWith { DEFAULT } + pairs.toMap())
    }
}
