package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * The developing character of a place or institution — a room, a department, the
 * hotel itself — held as a *bundle* of evidence-weighted traits, never a single
 * label. A department is not "the efficient one"; it is reliable to this degree,
 * warm to that, brisk to another, each strength derived from the repeated,
 * causally-recorded evidence of how work there has actually gone. One good day
 * barely moves it; a season of them makes it characteristic.
 *
 * [traits] are online weighted means (-1..1) over the [EvidenceDimension]s, each
 * carrying its own accumulated weight and confidence. [sourceEvidenceIds] keeps a
 * bounded trail back to the evidence behind the profile, so any claimed trait can
 * be traced to what produced it. Culture moves *slowly*: evidence ages gently, so
 * character is the residue of a long history, not a reaction to the last event.
 */
@Serializable
data class CultureProfile(
    val subjectId: EntityId,
    val traits: Map<EvidenceDimension, StandingValue> = emptyMap(),
    val sourceEvidenceIds: Set<EvidenceId> = emptySet(),
    val observations: Int = 0,
    val lastUpdatedAt: SimTime = SimTime(0),
) {
    fun observe(
        dimension: EvidenceDimension,
        direction: Double,
        weight: Double,
        evidenceId: EvidenceId,
        now: SimTime,
        decay: Double = 1.0,
    ): CultureProfile {
        val current = traits[dimension] ?: StandingValue()
        val trail = (sourceEvidenceIds + evidenceId).let {
            if (it.size <= TRAIL) it else it.toList().takeLast(TRAIL).toSet()
        }
        return copy(
            traits = traits + (dimension to current.reinforced(direction, weight, decay)),
            sourceEvidenceIds = trail,
            observations = observations + 1,
            lastUpdatedAt = now,
        )
    }

    fun trait(dimension: EvidenceDimension): StandingValue = traits[dimension] ?: StandingValue()

    /**
     * The traits that have become genuinely *characteristic* — held with enough
     * accumulated weight and far enough from neutral to describe the place. There is
     * deliberately no single winner: a place can be several things at once, or, until
     * a history builds, nothing in particular.
     */
    fun pronounced(minWeight: Double = MIN_WEIGHT, minMagnitude: Double = MIN_MAGNITUDE): Map<EvidenceDimension, StandingValue> =
        traits.filter { (_, v) -> v.weight >= minWeight && kotlin.math.abs(v.value) >= minMagnitude }

    companion object {
        const val TRAIL = 32
        const val MIN_WEIGHT = 2.0
        const val MIN_MAGNITUDE = 0.15
    }
}

/**
 * Every place's culture in one keyed registry. Culture is derived, never assigned:
 * the only way a profile changes is by folding fresh evidence about that entity.
 * Keyed by [EntityId] so rooms, departments and the hotel share the machinery.
 */
@Serializable
data class CultureRegistry(
    val profiles: Map<EntityId, CultureProfile> = emptyMap(),
) {
    fun of(subject: EntityId): CultureProfile? = profiles[subject]

    fun with(subject: EntityId, block: (CultureProfile) -> CultureProfile): CultureRegistry {
        val base = profiles[subject] ?: CultureProfile(subject)
        return copy(profiles = profiles + (subject to block(base)))
    }

    companion object {
        val EMPTY = CultureRegistry()
    }
}
