package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * A stable reference to anything a judgement can be about — a person, a room, a
 * department or the hotel itself. Evidence and reputation are keyed by this so the
 * same machinery serves people, places and institutions.
 */
@Serializable
@JvmInline
value class EntityId(val value: String) {
    companion object {
        fun person(id: PersonId) = EntityId("person:${id.value}")

        fun room(id: RoomId) = EntityId("room:${id.value}")

        fun department(d: Department) = EntityId("dept:${d.name.lowercase()}")

        val HOTEL = EntityId("hotel")
    }
}

@Serializable
@JvmInline
value class EvidenceId(val value: String)

/**
 * The axes along which repeated behaviour is judged. Evidence records lean one of
 * these one way or another; reputations are the accumulation. Deliberately broad
 * enough to describe people, rooms, departments and the hotel.
 */
@Serializable
enum class EvidenceDimension {
    RELIABILITY,
    COMPETENCE,
    WARMTH,
    DISCRETION,
    HONESTY,
    GENEROSITY,
    FAIRNESS,
    CALMNESS,
    RESPONSIVENESS,
    CLEANLINESS,
    PRESTIGE,
    SAFETY,
    VALUE,
    CONSISTENCY,
    INNOVATION,
    TRADITION,
}

/**
 * One recorded scrap of evidence about a subject on one dimension, derived from
 * real causal history (never invented). [direction] is -1..1 (bad..good),
 * [strength] how much this instance weighs, [confidence] how sure the observation
 * is. [observedBy] is the person who saw it firsthand, or null for a visible/public
 * outcome. [sourceCauseIds] ties it back to the causal graph. This is the *basis*
 * from which different people form different reputations — not a reputation itself.
 */
@Serializable
data class HistoricalEvidence(
    val id: EvidenceId,
    val subjectId: EntityId,
    val dimension: EvidenceDimension,
    val direction: Double,
    val strength: Double,
    val confidence: Double,
    val sourceCauseIds: Set<CauseId>,
    val observedBy: PersonId?,
    val createdAt: SimTime,
    val expiresAt: SimTime? = null,
)

/** A bounded, append-only record of evidence, kept for provenance and inspection. */
@Serializable
data class EvidenceLedger(
    val entries: List<HistoricalEvidence> = emptyList(),
) {
    val size: Int get() = entries.size

    fun add(evidence: HistoricalEvidence): EvidenceLedger = EvidenceLedger(entries + evidence)

    fun about(subject: EntityId): List<HistoricalEvidence> = entries.filter { it.subjectId == subject }

    /** Keep the most recent [max] entries; older evidence has already folded into standings. */
    fun prunedTo(max: Int): EvidenceLedger = if (entries.size <= max) this else EvidenceLedger(entries.takeLast(max))

    companion object {
        val EMPTY = EvidenceLedger()
    }
}

/** The social/personal axes one person forms about another, each derived from evidence. */
@Serializable
enum class StandingDimension {
    RELIABILITY,
    COMPETENCE,
    WARMTH,
    DISCRETION,
    HONESTY,
    GENEROSITY,
    FAIRNESS,
    CALMNESS,
    RESPONSIVENESS,
}

/** The role-specific axes colleagues and managers form about someone at work. */
@Serializable
enum class ProfessionalDimension {
    RELIABILITY,
    COMPETENCE,
    INITIATIVE,
    JUDGEMENT,
    TEAMWORK,
    LEADERSHIP,
    GUEST_HANDLING,
    DISCRETION,
    PUNCTUALITY,
    RESILIENCE,
}

/**
 * A held judgement on one axis: a weighted running mean [value] of the evidence
 * seen (-1..1), the accumulated [weight] behind it (decayed as it ages), and a
 * [confidence] that grows with weight and saturates. New evidence nudges it; it is
 * never set directly.
 */
@Serializable
data class StandingValue(
    val value: Double = 0.0,
    val weight: Double = 0.0,
    val confidence: Double = 0.0,
) {
    /**
     * Fold one piece of evidence in as an online weighted mean, ageing the prior
     * weight by [decay] first so recent evidence counts for more than stale.
     */
    fun reinforced(direction: Double, addedWeight: Double, decay: Double = 1.0): StandingValue {
        val prior = weight * decay
        val total = prior + addedWeight
        if (total <= 0.0) return this
        val merged = (value * prior + direction.coerceIn(-1.0, 1.0) * addedWeight) / total
        return StandingValue(
            value = merged.coerceIn(-1.0, 1.0),
            weight = total,
            confidence = (total / (total + CONFIDENCE_HALF)).coerceIn(0.0, 1.0),
        )
    }

    companion object {
        const val CONFIDENCE_HALF = 4.0
    }
}

/**
 * One observer's opinion of one subject across the social axes. Different observers
 * hold different [PersonalStanding]s about the same person — there is no global
 * score. [sourceEvidenceIds] keeps a bounded trail back to the evidence behind it.
 */
@Serializable
data class PersonalStanding(
    val observerId: PersonId,
    val subjectId: PersonId,
    val dimensions: Map<StandingDimension, StandingValue> = emptyMap(),
    val sourceEvidenceIds: Set<EvidenceId> = emptySet(),
    val lastUpdatedAt: SimTime = SimTime(0),
) {
    fun observe(
        dimension: StandingDimension,
        direction: Double,
        weight: Double,
        evidenceId: EvidenceId,
        now: SimTime,
        decay: Double = 1.0,
    ): PersonalStanding {
        val current = dimensions[dimension] ?: StandingValue()
        return copy(
            dimensions = dimensions + (dimension to current.reinforced(direction, weight, decay)),
            sourceEvidenceIds = (sourceEvidenceIds + evidenceId).let { if (it.size <= TRAIL) it else it.toList().takeLast(TRAIL).toSet() },
            lastUpdatedAt = now,
        )
    }

    /** A single confidence-weighted read across the axes, for summaries. */
    val overall: Double
        get() {
            val totalConfidence = dimensions.values.sumOf { it.confidence }
            return if (totalConfidence <= 0.0) 0.0 else dimensions.values.sumOf { it.value * it.confidence } / totalConfidence
        }

    private companion object {
        const val TRAIL = 24
    }
}

/** One observer's professional opinion of one subject across the role axes. */
@Serializable
data class ProfessionalStanding(
    val observerId: PersonId,
    val subjectId: PersonId,
    val dimensions: Map<ProfessionalDimension, StandingValue> = emptyMap(),
    val sourceEvidenceIds: Set<EvidenceId> = emptySet(),
    val lastUpdatedAt: SimTime = SimTime(0),
) {
    fun observe(
        dimension: ProfessionalDimension,
        direction: Double,
        weight: Double,
        evidenceId: EvidenceId,
        now: SimTime,
        decay: Double = 1.0,
    ): ProfessionalStanding {
        val current = dimensions[dimension] ?: StandingValue()
        return copy(
            dimensions = dimensions + (dimension to current.reinforced(direction, weight, decay)),
            sourceEvidenceIds = (sourceEvidenceIds + evidenceId).let { if (it.size <= TRAIL) it else it.toList().takeLast(TRAIL).toSet() },
            lastUpdatedAt = now,
        )
    }

    fun rating(dimension: ProfessionalDimension): StandingValue = dimensions[dimension] ?: StandingValue()

    private companion object {
        const val TRAIL = 24
    }
}

/**
 * Everything one person believes about everyone else — their private, observer-
 * specific reputations. Two people can (and should) hold different views of the
 * same third party; this is where that divergence lives.
 */
@Serializable
data class Standings(
    val personal: Map<PersonId, PersonalStanding> = emptyMap(),
    val professional: Map<PersonId, ProfessionalStanding> = emptyMap(),
) {
    fun personalOf(subject: PersonId): PersonalStanding? = personal[subject]

    fun professionalOf(subject: PersonId): ProfessionalStanding? = professional[subject]

    fun withPersonal(observer: PersonId, subject: PersonId, block: (PersonalStanding) -> PersonalStanding): Standings {
        val base = personal[subject] ?: PersonalStanding(observer, subject)
        return copy(personal = personal + (subject to block(base)))
    }

    fun withProfessional(observer: PersonId, subject: PersonId, block: (ProfessionalStanding) -> ProfessionalStanding): Standings {
        val base = professional[subject] ?: ProfessionalStanding(observer, subject)
        return copy(professional = professional + (subject to block(base)))
    }

    companion object {
        val EMPTY = Standings()
    }
}
