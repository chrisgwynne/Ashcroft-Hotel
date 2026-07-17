package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * Where a practice or tradition sits in its life. Nothing here is scheduled: the
 * stage is *read* from how widely a routine is currently adopted among a place's
 * people and whether that adoption is rising or falling away from its own past
 * peak — so a practice emerges, settles, and can quietly lapse, all from the
 * aggregate of individual habits, never from a script.
 */
@Serializable
enum class PracticeStage { EMERGING, ESTABLISHED, FADING, DORMANT }

/**
 * A shared way of doing things among the people of a department or the hotel: a
 * routine that enough of them have independently taken up that it has become
 * customary. [adoption] is a slow moving fraction of members who hold the
 * underlying habit; [stage] is derived from it and from its own history, never
 * set. A practice is emergent — it is what a group has come to do, not a rule
 * imposed on them.
 */
@Serializable
data class Practice(
    val descriptor: String,
    val adoption: Double = 0.0,
    val peakAdoption: Double = 0.0,
    val stage: PracticeStage = PracticeStage.EMERGING,
    val observations: Int = 0,
    val sinceDay: Long = 0,
    val lastSeenAt: SimTime = SimTime(0),
) {
    val isCustomary: Boolean get() = stage == PracticeStage.ESTABLISHED

    /** Fold the latest observed adoption fraction in, ageing toward it, and re-read the stage. */
    fun observed(fraction: Double, now: SimTime): Practice {
        val blended = (adoption + (fraction - adoption) * SMOOTH).coerceIn(0.0, 1.0)
        val peak = max(peakAdoption, blended)
        val stageNow = when {
            blended >= ESTABLISH -> PracticeStage.ESTABLISHED
            blended <= DORMANT_BELOW -> PracticeStage.DORMANT
            peak >= ESTABLISH && blended < peak * FADE_FRACTION -> PracticeStage.FADING
            else -> PracticeStage.EMERGING
        }
        return copy(
            adoption = blended,
            peakAdoption = peak,
            stage = stageNow,
            observations = observations + 1,
            lastSeenAt = now,
        )
    }

    private companion object {
        const val SMOOTH = 0.08
        const val ESTABLISH = 0.55
        const val FADE_FRACTION = 0.6
        const val DORMANT_BELOW = 0.08
    }
}

/**
 * Every place's practices, keyed by entity then by routine. Derived, never
 * declared: a practice only exists because the aggregate of individual habits put
 * it there, and it lapses when they stop.
 */
@Serializable
data class PracticeRegistry(
    val byEntity: Map<EntityId, Map<String, Practice>> = emptyMap(),
) {
    fun of(entity: EntityId): Map<String, Practice> = byEntity[entity] ?: emptyMap()

    /** The routines this place currently treats as customary. */
    fun customary(entity: EntityId): Set<String> = of(entity).filterValues { it.isCustomary }.keys

    /**
     * Record the latest adoption of one routine among a place's people. A routine
     * seen for the first time enters as [PracticeStage.EMERGING]; thereafter its
     * stage is re-derived from the running adoption.
     */
    fun observe(entity: EntityId, descriptor: String, fraction: Double, now: SimTime): PracticeRegistry {
        val current = of(entity)
        val existing = current[descriptor] ?: Practice(descriptor, sinceDay = now.dayIndex)
        val updated = existing.observed(fraction, now)
        return copy(byEntity = byEntity + (entity to (current + (descriptor to updated))))
    }

    companion object {
        val EMPTY = PracticeRegistry()
    }
}
