package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class MemoryId(val value: String)

/**
 * The kind of remembered event. Broadened in Phase 4 to cover the ordinary
 * social moments — being helped, ignored, praised or embarrassed, sharing news
 * — that accumulate into how people feel about one another. Still no dramatic
 * life events.
 */
@Serializable
enum class MemoryKind {
    WAS_HELPED,
    WAS_IGNORED,
    WAS_INTERRUPTED,
    HAD_PLEASANT_CHAT,
    WORKED_WELL,
    GRANTED_FAVOUR,
    WAS_PRAISED,
    WAS_EMBARRASSED,
    HELPED_SOMEONE,
    LEARNED_SOMETHING,
    SHARED_NEWS,
    WAS_THANKED,
}

/**
 * A structured long-term memory of a meaningful moment. Only significant events
 * become memories (routine movement never does). [valence] is negative
 * (unpleasant) to positive (pleasant); [importance] gates whether it is retained
 * and how strongly it later sways behaviour.
 *
 * Phase 4 additions let memories resurface rather than merely accumulate:
 * [placeId] anchors a memory to where it happened, [confidence] captures how
 * clearly it is remembered (it can blur with time), and [lastRecalledAt] /
 * [recallCount] track how a memory is revisited — a memory brought to mind by
 * a place or a face weighs more heavily in the moment than one long dormant.
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
    val placeId: RoomId? = null,
    val confidence: Double = 1.0,
    val lastRecalledAt: SimTime? = null,
    val recallCount: Int = 0,
) {
    /** How this memory currently colours the owner's feeling toward [subjectId]. */
    fun sentiment(): Double = valence * importance * confidence

    /** Bring this memory to mind: note when, and that it was revisited. */
    fun recalled(now: SimTime): Memory = copy(lastRecalledAt = now, recallCount = recallCount + 1)

    /** How readily this memory surfaces: important, vivid and recently-recalled memories come easily. */
    fun salience(now: SimTime): Double {
        val recency = lastRecalledAt?.let { 1.0 / (1.0 + (now.epochMinutes - it.epochMinutes) / MINUTES_PER_DAY) } ?: 0.0
        return importance * confidence * (1.0 + recency)
    }

    private companion object {
        const val MINUTES_PER_DAY = 60.0 * 24.0
    }
}
