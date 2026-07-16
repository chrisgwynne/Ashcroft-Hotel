package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.NeedState

/**
 * How needs drift over time and how activities relieve them. Needs decay
 * gently every simulated minute; performing the right activity at the right
 * place restores the matching need. These are pressures the chooser reads —
 * nothing here forces an action.
 */
object NeedDynamics {
    private val decayPerMinute: Map<NeedKind, Float> = mapOf(
        NeedKind.HUNGER to 0.0016f,
        NeedKind.REST to 0.0011f,
        NeedKind.SOCIAL to 0.0009f,
        NeedKind.HYGIENE to 0.0007f,
        NeedKind.PRIVACY to 0.0006f,
        NeedKind.PURPOSE to 0.0005f,
    )

    /** Net per-minute restoration while performing an activity (before decay). */
    private fun restorationPerMinute(kind: ActivityKind): Map<NeedKind, Float> = when (kind) {
        ActivityKind.EAT -> mapOf(NeedKind.HUNGER to 0.030f)
        ActivityKind.SLEEP -> mapOf(NeedKind.REST to 0.022f, NeedKind.HYGIENE to -0.0003f)
        ActivityKind.SOCIALISE -> mapOf(NeedKind.SOCIAL to 0.020f)
        ActivityKind.WASH -> mapOf(NeedKind.HYGIENE to 0.040f)
        ActivityKind.RELAX -> mapOf(NeedKind.PRIVACY to 0.018f, NeedKind.REST to 0.004f)
        ActivityKind.WORK -> mapOf(NeedKind.PURPOSE to 0.015f, NeedKind.REST to -0.0009f, NeedKind.SOCIAL to 0.003f)
        ActivityKind.TRAVEL, ActivityKind.IDLE -> emptyMap()
    }

    /** Advance needs by one minute given the current activity. */
    fun tick(needs: NeedState, activity: ActivityKind): NeedState {
        val deltas = HashMap<NeedKind, Float>(NeedKind.entries.size)
        for (need in NeedKind.entries) {
            deltas[need] = -(decayPerMinute[need] ?: 0f)
        }
        for ((need, gain) in restorationPerMinute(activity)) {
            deltas[need] = (deltas[need] ?: 0f) + gain
        }
        return needs.adjusted(deltas)
    }
}
