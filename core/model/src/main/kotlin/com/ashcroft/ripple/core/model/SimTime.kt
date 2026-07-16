package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * Deterministic simulation time measured in whole minutes since an arbitrary
 * epoch (the moment simulation Year 1 begins). The simulation clock is seeded
 * and monotonic: time never moves backwards. Real wall-clock time is never
 * used by the engine.
 */
@Serializable
@JvmInline
value class SimTime(
    val epochMinutes: Long,
) : Comparable<SimTime> {
    override fun compareTo(other: SimTime): Int = epochMinutes.compareTo(other.epochMinutes)

    operator fun plus(minutes: Long): SimTime = SimTime(epochMinutes + minutes)

    val minuteOfDay: Int get() = (Math.floorMod(epochMinutes, MINUTES_PER_DAY)).toInt()

    val hourOfDay: Int get() = minuteOfDay / MINUTES_PER_HOUR

    val dayIndex: Long get() = Math.floorDiv(epochMinutes, MINUTES_PER_DAY)

    companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 60 * 24
        val START = SimTime(0)
    }
}
