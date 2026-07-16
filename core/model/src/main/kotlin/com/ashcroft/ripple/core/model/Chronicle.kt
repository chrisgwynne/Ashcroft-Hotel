package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * A single line in the hotel's chronicle: a readable record of something that
 * has *already happened* and genuinely mattered. The chronicle never schedules
 * or invents events — it only notices, after the fact, outcomes significant
 * enough to be worth remembering. Routine comings and goings never appear.
 */
@Serializable
data class ChronicleEntry(
    val id: String,
    val at: SimTime,
    val headline: String,
    val significance: Double,
    val involved: Set<PersonId>,
    val causeIds: Set<CauseId> = emptySet(),
)
