package com.ashcroft.ripple.core.simulation

/**
 * A tiny, allocation-free SplitMix64 generator. The simulation never uses
 * [Math.random]/[java.util.Random] seeded from wall-clock time; all stochastic
 * choices derive from an explicit seed so that a given seed + history replays
 * identically. Instances are cheap — derive one per decision from stable inputs
 * (world seed, person id hash, tick) rather than sharing mutable global state.
 */
class DeterministicRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** A float in [0,1). */
    fun nextFloat(): Float = ((nextLong() ushr 40).toInt() and 0xFFFFFF) / 16_777_216f

    /** A signed jitter in [-magnitude, magnitude]. */
    fun jitter(magnitude: Float): Float = (nextFloat() * 2f - 1f) * magnitude

    companion object {
        /** Combine stable inputs into a well-mixed seed. */
        fun seedOf(vararg parts: Long): Long {
            var h = -0x340d631b7bdddcdbL // 0xCBF29CE484222325
            for (p in parts) {
                h = (h xor p) * 0x100000001b3L
            }
            return h
        }
    }
}
