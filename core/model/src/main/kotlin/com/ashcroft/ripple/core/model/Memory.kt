package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class MemoryId(val value: String)

/** The kind of remembered event. Kept small in Phase 3. */
@Serializable
enum class MemoryKind {
    WAS_HELPED,
    WAS_IGNORED,
    WAS_INTERRUPTED,
    HAD_PLEASANT_CHAT,
    WORKED_WELL,
    GRANTED_FAVOUR,
}

/**
 * A structured memory of a meaningful moment. Only significant events become
 * memories (routine movement never does). Valence is negative (unpleasant) to
 * positive (pleasant); importance gates whether it is retained and how much it
 * later sways decisions.
 */
@Serializable
data class Memory(
    val id: MemoryId,
    val ownerId: PersonId,
    val occurredAt: SimTime,
    val kind: MemoryKind,
    val subjectId: PersonId?,
    val valence: Double,
    val importance: Double,
) {
    /** How this memory currently colours the owner's feeling toward [subjectId]. */
    fun sentiment(): Double = valence * importance
}
