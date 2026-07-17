package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * Why a guest is here. Purpose shapes the goals and commitments a guest carries
 * — a business traveller works and keeps appointments, a holidaymaker seeks the
 * bar and restaurant, someone on a quiet retreat guards their privacy — but it
 * never assigns a storyline. Two guests with the same purpose still behave
 * differently through personality and circumstance.
 */
@Serializable
enum class StayPurpose {
    BUSINESS,
    HOLIDAY,
    INTERVIEW,
    VISITING_FAMILY,
    FUNCTION,
    TEMPORARY,
    RETREAT,
}

/**
 * A guest's stay: why they are here, when they are due to leave, and how they
 * are finding it. [expectations] are the things this particular guest cares
 * about (speed, quiet, friendliness…), against which observed service is judged.
 */
@Serializable
data class GuestStay(
    val purpose: StayPurpose,
    val checkoutDay: Long,
    val expectations: Set<GuestValue> = emptySet(),
    val satisfaction: Double = 0.6,
    /** The multi-dimensional picture the guest is forming of the hotel (Phase 7C). */
    val perception: GuestPerception = GuestPerception(),
    /** How likely they are, and how far along, toward coming back (Phase 7C). */
    val returnIntention: ReturnIntention = ReturnIntention(),
) {
    fun daysLeft(now: SimTime): Long = checkoutDay - now.dayIndex
}

/** The axes a guest judges a hotel along — richer than one satisfaction number. */
@Serializable
enum class PerceptionDimension {
    WELCOME,
    SPEED,
    ROOM_QUALITY,
    CLEANLINESS,
    STAFF_WARMTH,
    PRIVACY,
    QUIET,
    FOOD,
    RELIABILITY,
    VALUE,
    PRESTIGE,
    RECOGNITION,
}

/**
 * A guest's forming opinion of the hotel, one evidence-weighted axis at a time. A
 * single lapse touches only the axis it bears on (speed, reliability) — it does
 * not rewrite the whole opinion. The headline satisfaction is a *weighted read* of
 * these axes against what this particular guest cares about.
 */
@Serializable
data class GuestPerception(
    val dimensions: Map<PerceptionDimension, StandingValue> = emptyMap(),
) {
    fun witness(dimension: PerceptionDimension, direction: Double, weight: Double, decay: Double = 1.0): GuestPerception {
        val current = dimensions[dimension] ?: StandingValue()
        return copy(dimensions = dimensions + (dimension to current.reinforced(direction, weight, decay)))
    }

    /** A 0..1 satisfaction read, weighting each formed axis by how much this guest cares and how sure they are. */
    fun satisfaction(weights: Map<PerceptionDimension, Double>, neutral: Double = 0.55): Double {
        if (dimensions.isEmpty()) return neutral
        var weightSum = 0.0
        var valueSum = 0.0
        for ((dimension, standing) in dimensions) {
            val w = (weights[dimension] ?: BASE_WEIGHT) * standing.confidence
            weightSum += w
            valueSum += standing.value * w
        }
        if (weightSum <= 0.0) return neutral
        // The evidence mean is -1..1; blend it around the neutral starting point.
        val mean = (valueSum / weightSum).coerceIn(-1.0, 1.0)
        return (neutral + (1.0 - neutral).coerceAtLeast(neutral) * mean).coerceIn(0.0, 1.0)
    }

    private companion object {
        const val BASE_WEIGHT = 0.5
    }
}

/** How far a former-guest-to-be has travelled from a warm feeling toward an actual return. */
@Serializable
enum class ReturnStage { NONE, INTENDING, PLANNING, BOOKED, ARRIVED }

/**
 * A guest's developing wish to come back. [value] is the strength of the wish
 * (0..1); [stage] tracks how far it has turned into action. Intention is only ever
 * an *option on the future* — finances, circumstance and alternatives can end it at
 * any stage, so intending is not the same as planning, planning not the same as
 * booking, and booking not the same as arriving.
 */
@Serializable
data class ReturnIntention(
    val value: Double = 0.0,
    val confidence: Double = 0.0,
    val stage: ReturnStage = ReturnStage.NONE,
)

/**
 * How much a particular guest cares about each axis of the hotel — the weights
 * their satisfaction is read against. Derived from why they are here, what they
 * said they value, and their temperament. A business traveller weights speed and
 * quiet; a holidaymaker, food and warmth; a stickler, cleanliness.
 */
object PerceptionWeights {
    private const val BASE = 0.5
    private const val CARES = 1.0

    fun forGuest(purpose: StayPurpose, expectations: Set<GuestValue>, personality: Personality): Map<PerceptionDimension, Double> {
        val weights = PerceptionDimension.entries.associateWith { BASE }.toMutableMap()

        fun bump(dimension: PerceptionDimension, by: Double = CARES) {
            weights[dimension] = (weights[dimension] ?: BASE) + by
        }
        expectations.forEach { value ->
            when (value) {
                GuestValue.SPEED -> bump(PerceptionDimension.SPEED)
                GuestValue.PRIVACY -> bump(PerceptionDimension.PRIVACY)
                GuestValue.CLEANLINESS -> bump(PerceptionDimension.CLEANLINESS)
                GuestValue.FRIENDLINESS -> bump(PerceptionDimension.STAFF_WARMTH)
                GuestValue.LUXURY -> {
                    bump(PerceptionDimension.ROOM_QUALITY)
                    bump(PerceptionDimension.PRESTIGE)
                }
                GuestValue.QUIET -> bump(PerceptionDimension.QUIET)
                GuestValue.RECOGNITION -> bump(PerceptionDimension.RECOGNITION)
                GuestValue.VALUE_FOR_MONEY -> bump(PerceptionDimension.VALUE)
            }
        }
        when (purpose) {
            StayPurpose.BUSINESS -> {
                bump(PerceptionDimension.SPEED)
                bump(PerceptionDimension.QUIET)
                bump(PerceptionDimension.RELIABILITY)
            }
            StayPurpose.HOLIDAY -> {
                bump(PerceptionDimension.FOOD)
                bump(PerceptionDimension.STAFF_WARMTH)
                bump(PerceptionDimension.ROOM_QUALITY)
            }
            StayPurpose.INTERVIEW -> {
                bump(PerceptionDimension.QUIET)
                bump(PerceptionDimension.SPEED)
            }
            StayPurpose.VISITING_FAMILY -> {
                bump(PerceptionDimension.WELCOME)
                bump(PerceptionDimension.STAFF_WARMTH)
            }
            StayPurpose.FUNCTION -> {
                bump(PerceptionDimension.FOOD)
                bump(PerceptionDimension.PRESTIGE)
            }
            StayPurpose.TEMPORARY -> {
                bump(PerceptionDimension.VALUE)
                bump(PerceptionDimension.RELIABILITY)
            }
            StayPurpose.RETREAT -> {
                bump(PerceptionDimension.PRIVACY)
                bump(PerceptionDimension.QUIET)
            }
        }
        if (personality[TraitKind.CONSCIENTIOUSNESS] > 0.6f) {
            bump(PerceptionDimension.CLEANLINESS, 0.5)
            bump(PerceptionDimension.RELIABILITY, 0.5)
        }
        if (personality[TraitKind.SOCIABILITY] > 0.6f) bump(PerceptionDimension.STAFF_WARMTH, 0.5)
        return weights
    }
}

/** What a guest most cares about — the axes their satisfaction is judged along. */
@Serializable
enum class GuestValue {
    SPEED,
    PRIVACY,
    CLEANLINESS,
    FRIENDLINESS,
    LUXURY,
    QUIET,
    RECOGNITION,
    VALUE_FOR_MONEY,
}
