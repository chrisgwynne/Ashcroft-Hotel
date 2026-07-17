package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * A staff member's long-run professional aspiration — emergent, never a scripted
 * promotion. [drive] is how strongly they seek to grow and take on more;
 * [demonstrated] is the track record they can actually point to (their own
 * settled, competent work); [recognition] is the standing leaders have shown they
 * hold them in, decayed as it ages; [focus] is the kind of excellence they are
 * building toward.
 *
 * An aspiration changes what a person reaches for, and how leaders come to see
 * them — but it never, by crossing a threshold, changes their role. Careers here
 * advance only through the ordinary accumulation of decisions, competence and the
 * recognition of others; there is no promotion event and no ladder to climb by
 * number. Every part of it is derived from real history, with a trail back to the
 * evidence behind the recognition.
 */
@Serializable
data class Aspiration(
    val focus: ProfessionalDimension = ProfessionalDimension.COMPETENCE,
    val drive: Double = 0.0,
    val demonstrated: Double = 0.0,
    val recognition: Double = 0.0,
    val sourceEvidenceIds: Set<EvidenceId> = emptySet(),
    val updatedAt: SimTime = SimTime(0),
) {
    /** Actively pursuing advancement — enough to shape their choices. */
    val isPursuing: Boolean get() = drive >= PURSUING

    /** Has a real track record to point to — enough for leaders and opportunities to notice. */
    val isProven: Boolean get() = demonstrated >= PROVEN

    companion object {
        const val PURSUING = 0.45
        const val PROVEN = 0.4
    }
}
