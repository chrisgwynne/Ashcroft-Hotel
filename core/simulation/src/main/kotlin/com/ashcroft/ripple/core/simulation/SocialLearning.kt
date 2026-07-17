package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.HabitProfile
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.PracticeRegistry
import com.ashcroft.ripple.core.model.ProfessionalStanding
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.department
import com.ashcroft.ripple.core.model.isRoutineForming
import com.ashcroft.ripple.core.model.routineKeyOf

/**
 * How doing a thing, and watching a respected colleague do it, quietly changes
 * what people tend to do — and how the aggregate of those individual habits
 * becomes a department's or the hotel's customs.
 *
 * Nothing here is scripted or cloned. A person's own completed routines reinforce
 * their own habits; observers of a routine pick up a *smaller* inclination toward
 * it, scaled by how well they professionally regard the person they watched — you
 * learn more from those you rate. Traits are never copied: only the disposition to
 * *do a particular routine in a particular place* moves, and only from real,
 * observed behaviour. Practices are then simply read off the habits: a routine
 * enough of a group has independently taken up has, by that fact, become a custom;
 * when they stop, it lapses. Fully deterministic.
 */
internal object SocialLearning {
    private data class Completion(val actor: PersonId, val key: String, val room: RoomId?)

    fun apply(
        before: List<Person>,
        after: List<Person>,
        practices: PracticeRegistry,
        now: SimTime,
    ): Pair<List<Person>, PracticeRegistry> {
        val beforePhase = before.associate { it.id to it.action.phase }
        val completions = after.mapNotNull { p ->
            val a = p.action
            if (a.phase != ActionPhase.COMPLETED || beforePhase[p.id] == ActionPhase.COMPLETED) return@mapNotNull null
            if (!a.verb.isRoutineForming()) return@mapNotNull null
            Completion(p.id, routineKeyOf(a.verb, p.location.roomId), p.location.roomId)
        }
        if (completions.isEmpty() && now.epochMinutes % PRACTICE_PERIOD != 0L) return after to practices

        val byId = after.associateBy { it.id }.toMutableMap()
        // A person's own completed routine reinforces their own habit.
        for (c in completions) {
            val person = byId.getValue(c.actor)
            byId[c.actor] = person.copy(habits = person.habits.reinforce(c.key, SELF_LEARN, now))
        }
        // Co-located staff pick up a fainter inclination — more from those they rate.
        for (c in completions) {
            val room = c.room ?: continue
            val observers = byId.values.filter { it.id != c.actor && it.role.isStaff && it.location.roomId == room }
            for (observer in observers) {
                val regard = observer.standings.professionalOf(c.actor)?.let { regardOf(it) } ?: 0.0
                val amount = SOCIAL_LEARN_BASE * (BASELINE_OPENNESS + regard)
                byId[observer.id] = observer.copy(habits = observer.habits.reinforce(c.key, amount, now))
            }
        }

        val people = after.map { byId.getValue(it.id) }
        // Read the customs off the habits, once an hour rather than every minute.
        val updated = if (now.epochMinutes % PRACTICE_PERIOD == 0L) aggregate(people, practices, now) else practices
        return people to updated
    }

    /** A professional regard in 0..1 — the confidence-weighted mean of the good one sees, floored at nothing. */
    private fun regardOf(standing: ProfessionalStanding): Double {
        val confidence = standing.dimensions.values.sumOf { it.confidence }
        if (confidence <= 0.0) return 0.0
        val mean = standing.dimensions.values.sumOf { it.value * it.confidence } / confidence
        return mean.coerceIn(0.0, 1.0)
    }

    /** Re-derive each department's and the hotel's practices from the members' settled habits. */
    private fun aggregate(people: List<Person>, practices: PracticeRegistry, now: SimTime): PracticeRegistry {
        val staff = people.filter { it.role.isStaff }
        if (staff.isEmpty()) return practices
        var registry = practices
        val byDept = staff.groupBy { it.role.department() }
        for ((dept, members) in byDept) {
            if (dept == null) continue
            registry = observeGroup(registry, EntityId.department(dept), members, now)
        }
        return observeGroup(registry, EntityId.HOTEL, staff, now)
    }

    /**
     * For every routine any member has settled into — plus any this place already
     * treats as a practice, so lapsing ones can be seen to fade — record what
     * fraction of the group now holds it. The stage follows from that fraction.
     */
    private fun observeGroup(registry: PracticeRegistry, entity: EntityId, members: List<Person>, now: SimTime): PracticeRegistry {
        if (members.isEmpty()) return registry
        val settledKeys = members.flatMap { it.habits.settled().keys }.toSet()
        val keys = settledKeys + registry.of(entity).keys
        var result = registry
        for (key in keys) {
            val holders = members.count { it.habits.strengthOf(key) >= HabitProfile.SETTLED }
            result = result.observe(entity, key, holders.toDouble() / members.size, now)
        }
        return result
    }

    private const val SELF_LEARN = 0.06
    private const val SOCIAL_LEARN_BASE = 0.015
    private const val BASELINE_OPENNESS = 0.5
    private const val PRACTICE_PERIOD = 60L
}
