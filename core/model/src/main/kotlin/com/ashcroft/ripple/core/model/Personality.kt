package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * Stable personality dimensions. Each runs 0f..1f. Traits *bias* decisions;
 * they never make behaviour rigid. A Phase 2 subset of the design's full set;
 * traits may slowly evolve through strong repeated experience in later phases.
 */
@Serializable
enum class TraitKind {
    OPENNESS,
    CONSCIENTIOUSNESS,
    EXTRAVERSION,
    AGREEABLENESS,
    AMBITION,
    PATIENCE,
    SOCIABILITY,
}

@Serializable
data class Personality(private val traits: Map<TraitKind, Float>) {
    operator fun get(kind: TraitKind): Float = traits[kind] ?: 0.5f

    companion object {
        fun of(vararg pairs: Pair<TraitKind, Float>): Personality =
            Personality(TraitKind.entries.associateWith { 0.5f } + pairs.toMap())
    }
}
