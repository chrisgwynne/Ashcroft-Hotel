package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.EntityId

/** The result of fast-forwarding to the next meaningful development. */
data class PulseJump(
    val state: WorldState,
    val events: List<PulseEvent>,
    val ticks: Int,
    val arrived: Boolean,
)

/**
 * Observer-side helpers that drive the deterministic engine forward *until
 * something worth seeing happens* — "jump to the next meaningful change". The stop
 * condition is the emergent [PulseDetector], gated by significance: it is the
 * simulation's own turns of event that decide when to stop, never a scripted
 * schedule. Ordinary life is never skipped over silently — the very first
 * qualifying development halts the jump.
 */
object Observation {
    /**
     * Step [engine] from [state] until the pulse surfaces an event of at least
     * [minSignificance], or [maxTicks] pass. Returns where it stopped, the events
     * that stopped it, how many ticks it took, and whether it actually arrived at a
     * development (vs hitting the tick cap).
     */
    fun jumpToNextChange(
        engine: SimulationEngine,
        state: WorldState,
        maxTicks: Int = DEFAULT_CAP,
        minSignificance: Double = DEFAULT_MIN,
    ): PulseJump = jump(engine, state, maxTicks, minSignificance) { it }

    /**
     * As [jumpToNextChange], but stops only at a development concerning [entity] —
     * "follow this person/room/department to their next meaningful moment".
     */
    fun jumpToNextChangeFor(
        engine: SimulationEngine,
        state: WorldState,
        entity: EntityId,
        maxTicks: Int = DEFAULT_CAP,
        minSignificance: Double = DEFAULT_MIN,
    ): PulseJump = jump(engine, state, maxTicks, minSignificance) { events -> events.filter { entity in it.subjects } }

    private fun jump(
        engine: SimulationEngine,
        state: WorldState,
        maxTicks: Int,
        minSignificance: Double,
        filter: (List<PulseEvent>) -> List<PulseEvent>,
    ): PulseJump {
        var previous = state
        var ticks = 0
        while (ticks < maxTicks) {
            val next = engine.step(previous)
            ticks++
            val qualifying = filter(PulseDetector.detect(previous, next)).filter { it.significance >= minSignificance }
            if (qualifying.isNotEmpty()) return PulseJump(next, qualifying, ticks, arrived = true)
            previous = next
        }
        return PulseJump(previous, emptyList(), ticks, arrived = false)
    }

    private const val DEFAULT_CAP = 7 * 24 * 60
    private const val DEFAULT_MIN = 0.5
}
