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
) {
    fun daysLeft(now: SimTime): Long = checkoutDay - now.dayIndex
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
