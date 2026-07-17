package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * The independent axes along which one person relates to another. Ripple never
 * collapses a relationship to a single "friendship" number: you can respect
 * someone you do not like, depend on someone you resent, or trust a colleague
 * without warmth. Each axis moves on its own from what actually happens between
 * two people.
 */
@Serializable
enum class RelationDimension {
    FAMILIARITY, // how well they know each other at all
    TRUST, // will they believe and rely on them
    RESPECT, // regard for their competence and standing
    AFFECTION, // plain warmth and liking
    ATTRACTION, // pull toward their company (kept non-romantic in this phase)
    RESENTMENT, // stored grievance
    GRATITUDE, // felt debt for kindness
    FEAR, // wariness or intimidation
    DEPENDENCE, // how much they lean on them
    PROFESSIONAL_CONFIDENCE, // faith in them at work specifically
}

/**
 * How one person stands toward another across every dimension. Values run
 * -1.0 to 1.0 (for most axes 0.0 is neutral; FAMILIARITY runs 0.0 upward).
 * Immutable — adjustments return a new instance.
 */
@Serializable
data class Relationship(
    val other: PersonId,
    private val axes: Map<RelationDimension, Double> = emptyMap(),
) {
    operator fun get(dimension: RelationDimension): Double = axes[dimension] ?: 0.0

    fun with(dimension: RelationDimension, value: Double): Relationship =
        Relationship(other, axes + (dimension to value.coerceIn(-1.0, 1.0)))

    fun adjusted(deltas: Map<RelationDimension, Double>): Relationship {
        if (deltas.isEmpty()) return this
        val next = axes.toMutableMap()
        for ((dimension, delta) in deltas) {
            next[dimension] = (get(dimension) + delta).coerceIn(-1.0, 1.0)
        }
        return Relationship(other, next)
    }

    /**
     * A single readable summary for UI and coarse decisions, derived from the
     * warm axes minus the cold ones. This is a *view*, never the stored truth —
     * the dimensions remain separate.
     */
    fun warmth(): Double =
        (get(RelationDimension.AFFECTION) + get(RelationDimension.TRUST) + get(RelationDimension.GRATITUDE)) / 3.0 -
            (get(RelationDimension.RESENTMENT) + get(RelationDimension.FEAR)) / 2.0

    val isStranger: Boolean get() = get(RelationDimension.FAMILIARITY) < STRANGER_CEILING

    companion object {
        const val STRANGER_CEILING = 0.15
    }
}

/**
 * The set of relationships a person holds, keyed by the other person. Starts
 * empty — everyone begins as a stranger and becomes known only through shared
 * experience.
 */
@Serializable
data class Relationships(private val toward: Map<PersonId, Relationship> = emptyMap()) {
    val all: Collection<Relationship> get() = toward.values

    fun with(other: PersonId): Relationship = toward[other] ?: Relationship(other)

    fun updated(relationship: Relationship): Relationships =
        Relationships(toward + (relationship.other to relationship))

    fun adjust(other: PersonId, deltas: Map<RelationDimension, Double>): Relationships =
        updated(with(other).adjusted(deltas))

    companion object {
        val EMPTY = Relationships()
    }
}
