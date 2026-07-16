package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Activity
import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.TraitKind

/**
 * Chooses what a person does next when they become free. This is the Phase 2
 * precursor to the full decision engine (Phase 3): it scores a handful of
 * candidate activities from competing pressures — active commitments and the
 * needs under most pressure — biased by personality and nudged by bounded,
 * *seeded* randomness. It deliberately avoids rigid `if need < X then …`
 * scripts: every candidate competes, and noise keeps outcomes from being
 * mechanically identical across otherwise-similar people.
 */
class ActivityChooser(private val locator: RoomLocator) {
    private data class Candidate(val kind: ActivityKind, val room: RoomId?, val score: Float)

    fun choose(person: Person, now: SimTime, seed: Long): Activity {
        val rng = DeterministicRandom(DeterministicRandom.seedOf(seed, person.id.value.hashCode().toLong(), now.epochMinutes))
        val candidates = buildList {
            addCommitmentCandidates(person, now.minuteOfDay, this)
            addNeedCandidates(person, this)
            add(Candidate(ActivityKind.RELAX, person.homeRoom ?: locator.firstOfKind(RoomKind.LOBBY), BASELINE))
        }

        val best = candidates.maxByOrNull { it.score + rng.jitter(NOISE) } ?: return Activity.IDLE
        return Activity(
            kind = best.kind,
            targetRoom = best.room,
            startedAt = now,
            plannedMinutes = durationFor(best.kind, rng),
        )
    }

    private fun addCommitmentCandidates(person: Person, minuteOfDay: Int, out: MutableList<Candidate>) {
        for (commitment in person.schedule) {
            if (!commitment.isActiveAt(minuteOfDay)) continue
            val kind = when (commitment.kind) {
                com.ashcroft.ripple.core.model.CommitmentKind.SHIFT -> ActivityKind.WORK
                com.ashcroft.ripple.core.model.CommitmentKind.MEAL -> ActivityKind.EAT
                com.ashcroft.ripple.core.model.CommitmentKind.APPOINTMENT -> ActivityKind.RELAX
                com.ashcroft.ripple.core.model.CommitmentKind.CHECKOUT -> ActivityKind.RELAX
            }
            val conscientiousness = person.personality[TraitKind.CONSCIENTIOUSNESS]
            out.add(Candidate(kind, commitment.location, commitment.strength * (0.6f + conscientiousness * 0.6f)))
        }
    }

    private fun addNeedCandidates(person: Person, out: MutableList<Candidate>) {
        for (need in NeedKind.entries) {
            val deficit = 1f - person.needs[need]
            if (deficit <= 0.05f) continue
            val urgency = deficit * deficit // pressure grows non-linearly as a need empties
            when (need) {
                NeedKind.HUNGER ->
                    out.add(Candidate(ActivityKind.EAT, locator.firstOfKind(RoomKind.RESTAURANT), urgency * 1.3f))
                NeedKind.REST ->
                    out.add(Candidate(ActivityKind.SLEEP, person.homeRoom, urgency * 1.2f))
                NeedKind.SOCIAL -> {
                    val social = person.personality[TraitKind.SOCIABILITY]
                    out.add(Candidate(ActivityKind.SOCIALISE, locator.firstOfKind(RoomKind.BAR), urgency * (0.7f + social * 0.8f)))
                }
                NeedKind.HYGIENE ->
                    out.add(Candidate(ActivityKind.WASH, person.homeRoom, urgency * 1.0f))
                NeedKind.PRIVACY ->
                    out.add(Candidate(ActivityKind.RELAX, person.homeRoom, urgency * 0.8f))
                NeedKind.PURPOSE -> {
                    val ambition = person.personality[TraitKind.AMBITION]
                    val room = if (person.role.isStaff) person.homeRoom else locator.firstOfKind(RoomKind.LOBBY)
                    out.add(Candidate(ActivityKind.RELAX, room, urgency * (0.4f + ambition * 0.5f)))
                }
            }
        }
    }

    private fun durationFor(kind: ActivityKind, rng: DeterministicRandom): Int {
        val base = when (kind) {
            ActivityKind.SLEEP -> 300
            ActivityKind.WORK -> 120
            ActivityKind.SOCIALISE -> 60
            ActivityKind.RELAX -> 45
            ActivityKind.EAT -> 40
            ActivityKind.WASH -> 20
            ActivityKind.TRAVEL, ActivityKind.IDLE -> 10
        }
        return (base + (rng.jitter(0.2f) * base).toInt()).coerceAtLeast(5)
    }

    private companion object {
        const val BASELINE = 0.12f
        const val NOISE = 0.10f
    }
}
