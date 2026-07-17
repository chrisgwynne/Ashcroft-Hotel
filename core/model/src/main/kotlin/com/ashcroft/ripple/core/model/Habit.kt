package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * The canonical name of a *routine* — a kind of thing done in a kind of place —
 * shared by a person's habits and a place's practices, so "working the front
 * desk" is the same routine whoever does it and however it is scored. Task-work
 * and shift-work collapse to the one working routine; everything else keeps its
 * verb. The room is part of the routine (a habit is anchored to where it happens).
 */
fun routineKeyOf(verb: ActionVerb, room: RoomId?): String {
    val v = if (verb == ActionVerb.ATTEND) ActionVerb.WORK else verb
    return "${v.name}|${room?.value ?: ""}"
}

/** Whether a verb is the kind of thing that settles into a routine at all. */
fun ActionVerb.isRoutineForming(): Boolean = when (this) {
    ActionVerb.WORK, ActionVerb.ATTEND, ActionVerb.EAT, ActionVerb.SLEEP, ActionVerb.WASH,
    ActionVerb.RELAX, ActionVerb.TAKE_BREAK, ActionVerb.SOCIALISE,
    -> true

    else -> false
}

/**
 * A learned personal routine: how strongly someone tends, of their own accord, to
 * do a particular thing. Derived from their *own* repeated behaviour over a long
 * horizon — and, a little, from watching colleagues they respect — never a copied
 * trait. [strength] is a 0..1 disposition that climbs gently with repetition and
 * fades gently without it, so a habit is a settled leaning, not a memory of the
 * last thing done.
 */
@Serializable
data class Habit(
    val key: String,
    val strength: Double = 0.0,
    val observations: Int = 0,
    val lastReinforcedAt: SimTime = SimTime(0),
)

/**
 * Everything a person has come to habitually do. The only way it changes is by
 * reinforcement from real behaviour; between reinforcements every habit fades a
 * little, so disuse quietly unlearns. Bounded, so a lifetime of routines never
 * grows without limit — the weakest are forgotten first.
 */
@Serializable
data class HabitProfile(
    val habits: Map<String, Habit> = emptyMap(),
) {
    fun strengthOf(key: String): Double = habits[key]?.strength ?: 0.0

    /**
     * Fold one completed routine in as a saturating step toward 1, ageing every
     * habit (this one included, before its step) so recent practice counts for more
     * and abandoned routines decay away.
     */
    fun reinforce(key: String, amount: Double, now: SimTime, decay: Double = FADE): HabitProfile {
        val faded = habits.mapValues { (_, h) -> h.copy(strength = h.strength * decay) }
        val current = faded[key] ?: Habit(key)
        val next = current.copy(
            strength = (current.strength + amount * (1.0 - current.strength)).coerceIn(0.0, 1.0),
            observations = current.observations + 1,
            lastReinforcedAt = now,
        )
        val merged = faded + (key to next)
        val pruned = if (merged.size <= CAP) {
            merged
        } else {
            merged.entries.sortedByDescending { it.value.strength }.take(CAP).associate { it.toPair() }
        }
        return copy(habits = pruned)
    }

    /** The routines that have become genuinely characteristic of this person. */
    fun settled(threshold: Double = SETTLED): Map<String, Habit> = habits.filterValues { it.strength >= threshold }

    companion object {
        val EMPTY = HabitProfile()
        const val FADE = 0.9992
        const val SETTLED = 0.35
        const val CAP = 48
    }
}
