package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CauseId
import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.HotelTaskStatus
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.ReturnStage
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.department
import kotlinx.serialization.Serializable

/** The kinds of meaningful development the hotel's "pulse" surfaces — never routine work. */
@Serializable
enum class PulseKind {
    PRACTICE_ESTABLISHED,
    DEPARTMENT_CULTURE_SHIFT,
    RELATIONSHIP_WARMED,
    RELATIONSHIP_COOLED,
    RETURN_LIKELY,
    TRUST_WIDENED,
    SERVICE_RESOLVED,
    CHRONICLE_MILESTONE,
}

/**
 * One meaningful development in the life of the hotel, ready to surface to a
 * watching player. It is emitted only when some derived quantity crosses a
 * threshold that *matters* — a custom taking hold, a bond turning, a guest
 * deciding they would come back — never for a routine task or a passing chat.
 * [id] is a stable, deterministic key so the same crossing is never surfaced
 * twice; [subjects] lets a follower filter to what concerns them; [causeIds]
 * keeps the trail back to the record.
 */
@Serializable
data class PulseEvent(
    val id: String,
    val kind: PulseKind,
    val at: SimTime,
    val headline: String,
    val significance: Double,
    val subjects: Set<EntityId> = emptySet(),
    val causeIds: Set<CauseId> = emptySet(),
)

/**
 * Reads two snapshots of the hotel — an earlier one and a later one — and reports
 * the meaningful developments that happened between them, as threshold crossings
 * rather than raw deltas. Comparing adjacent ticks yields almost nothing; comparing
 * a snapshot with one taken minutes or days later surfaces exactly the turns worth
 * a player's attention. It is a pure function of the two states, so the pulse is
 * fully deterministic and never invents anything the state does not support.
 *
 * "Meaningful" is enforced structurally: every detector fires on a band change
 * (neutral → warm → close, none → intending → planning, emerging → established),
 * so ordinary churn below a band boundary is silent by construction.
 */
object PulseDetector {
    fun detect(before: WorldState, after: WorldState): List<PulseEvent> {
        val events = ArrayList<PulseEvent>()
        practicesEstablished(before, after, events)
        departmentCultureShifts(before, after, events)
        relationshipTurns(before, after, events)
        returnsBecomingLikely(before, after, events)
        trustWidening(before, after, events)
        servicesResolved(before, after, events)
        chronicleMilestones(before, after, events)
        return events.sortedWith(compareByDescending<PulseEvent> { it.significance }.thenBy { it.id })
    }

    private fun practicesEstablished(before: WorldState, after: WorldState, out: MutableList<PulseEvent>) {
        for ((entity, practices) in after.practices.byEntity) {
            val was = before.practices.of(entity)
            for ((descriptor, practice) in practices) {
                if (practice.isCustomary && was[descriptor]?.isCustomary != true) {
                    out += PulseEvent(
                        id = "pulse:practice:${entity.value}:$descriptor",
                        kind = PulseKind.PRACTICE_ESTABLISHED,
                        at = after.clock,
                        headline = "${placeName(entity)} has made a custom of ${readableRoutine(descriptor)}.",
                        significance = (0.5 + practice.adoption * 0.4).coerceIn(0.0, 1.0),
                        subjects = setOf(entity),
                    )
                }
            }
        }
    }

    private fun departmentCultureShifts(before: WorldState, after: WorldState, out: MutableList<PulseEvent>) {
        for (dept in Department.entries) {
            val entity = EntityId.department(dept)
            val wasTraits = before.culture.of(entity)?.pronounced()?.keys ?: emptySet()
            val nowProfile = after.culture.of(entity) ?: continue
            for ((trait, standing) in nowProfile.pronounced()) {
                if (trait !in wasTraits) {
                    out += PulseEvent(
                        id = "pulse:culture:${entity.value}:${trait.name}",
                        kind = PulseKind.DEPARTMENT_CULTURE_SHIFT,
                        at = after.clock,
                        headline = "The ${deptName(dept)} team is becoming known for its ${trait.name.lowercase()}.",
                        significance = (0.45 + kotlin.math.abs(standing.value) * 0.4).coerceIn(0.0, 1.0),
                        subjects = setOf(entity),
                    )
                }
            }
        }
    }

    private fun relationshipTurns(before: WorldState, after: WorldState, out: MutableList<PulseEvent>) {
        val wasById = before.people.associateBy { it.id }
        for (person in after.people) {
            val was = wasById[person.id] ?: continue
            for (rel in person.relationships.all) {
                val other = rel.other
                val beforeEdge = was.relationships.with(other)
                val warmedNow = rel.warmth() >= CLOSE && beforeEdge.warmth() < CLOSE
                val cooledNow = rel[RelationDimension.RESENTMENT] >= COLD && beforeEdge[RelationDimension.RESENTMENT] < COLD
                if (warmedNow) {
                    out += relationshipEvent(person, other, after, PulseKind.RELATIONSHIP_WARMED, "have grown close", rel.warmth())
                } else if (cooledNow) {
                    val resentment = rel[RelationDimension.RESENTMENT]
                    out += relationshipEvent(person, other, after, PulseKind.RELATIONSHIP_COOLED, "have fallen out", resentment)
                }
            }
        }
    }

    private fun relationshipEvent(
        person: Person,
        other: PersonId,
        after: WorldState,
        kind: PulseKind,
        phrase: String,
        intensity: Double,
    ): PulseEvent {
        val otherName = after.person(other)?.name ?: other.value
        // Order the pair for a stable, direction-independent id so the turn is reported once.
        val pair = listOf(person.id.value, other.value).sorted()
        return PulseEvent(
            id = "pulse:rel:${kind.name}:${pair[0]}:${pair[1]}",
            kind = kind,
            at = after.clock,
            headline = "${person.name} and $otherName $phrase.",
            significance = (0.4 + intensity.coerceIn(0.0, 2.0) / 2.0 * 0.4).coerceIn(0.0, 1.0),
            subjects = setOf(EntityId.person(person.id), EntityId.person(other)),
        )
    }

    private fun returnsBecomingLikely(before: WorldState, after: WorldState, out: MutableList<PulseEvent>) {
        val wasById = before.people.associateBy { it.id }
        for (person in after.people) {
            val stay = person.stay ?: continue
            val wasStage = wasById[person.id]?.stay?.returnIntention?.stage ?: ReturnStage.NONE
            val nowStage = stay.returnIntention.stage
            if (nowStage.ordinal >= ReturnStage.INTENDING.ordinal && nowStage.ordinal > wasStage.ordinal) {
                out += PulseEvent(
                    id = "pulse:return:${person.id.value}:${nowStage.name}",
                    kind = PulseKind.RETURN_LIKELY,
                    at = after.clock,
                    headline = "${person.name} is warming to the idea of coming back.",
                    significance = (0.4 + stay.returnIntention.value * 0.4).coerceIn(0.0, 1.0),
                    subjects = setOf(EntityId.person(person.id)),
                )
            }
        }
    }

    private fun trustWidening(before: WorldState, after: WorldState, out: MutableList<PulseEvent>) {
        val subjects = after.people.map { it.id }
        for (subject in subjects) {
            val nowHolders = positiveObservers(after.people, subject)
            val wasHolders = positiveObservers(before.people, subject)
            if (nowHolders >= TRUST_QUORUM && wasHolders < TRUST_QUORUM) {
                val name = after.person(subject)?.name ?: subject.value
                out += PulseEvent(
                    id = "pulse:trust:${subject.value}",
                    kind = PulseKind.TRUST_WIDENED,
                    at = after.clock,
                    headline = "$name is coming to be trusted across the team.",
                    significance = 0.55,
                    subjects = setOf(EntityId.person(subject)),
                )
            }
        }
    }

    private fun positiveObservers(people: List<Person>, subject: PersonId): Int =
        people.count { observer ->
            observer.id != subject && (observer.standings.professionalOf(subject)?.let { standingIsPositive(it) } ?: false)
        }

    private fun standingIsPositive(standing: com.ashcroft.ripple.core.model.ProfessionalStanding): Boolean {
        val confidence = standing.dimensions.values.sumOf { it.confidence }
        if (confidence <= 0.0) return false
        return standing.dimensions.values.sumOf { it.value * it.confidence } / confidence >= TRUST_POSITIVE
    }

    private fun servicesResolved(before: WorldState, after: WorldState, out: MutableList<PulseEvent>) {
        val wasOpen = before.tasks.filter { it.isOpen }.associateBy { it.id }
        for (task in after.tasks) {
            val prior = wasOpen[task.id] ?: continue
            val nowDone = task.status == HotelTaskStatus.COMPLETED
            val wasLingering = task.type.guestFacing && (after.clock.epochMinutes - prior.createdAt.epochMinutes) >= LINGER_MINUTES
            if (nowDone && wasLingering) {
                out += PulseEvent(
                    id = "pulse:service:${task.id.value}",
                    kind = PulseKind.SERVICE_RESOLVED,
                    at = after.clock,
                    headline = "A long-waiting ${readableTask(task.type.name)} was finally seen to.",
                    significance = (0.4 + task.priority * 0.3).coerceIn(0.0, 1.0),
                    subjects = buildSet {
                        add(EntityId.room(task.locationId))
                        task.requestedBy?.let { add(EntityId.person(it)) }
                    },
                    causeIds = task.causeIds,
                )
            }
        }
    }

    private fun chronicleMilestones(before: WorldState, after: WorldState, out: MutableList<PulseEvent>) {
        val known = before.chronicle.map { it.id }.toSet()
        for (entry in after.chronicle) {
            if (entry.id in known) continue
            out += PulseEvent(
                id = "pulse:chronicle:${entry.id}",
                kind = PulseKind.CHRONICLE_MILESTONE,
                at = entry.at,
                headline = entry.headline,
                significance = entry.significance,
                subjects = entry.involved.map { EntityId.person(it) }.toSet(),
                causeIds = entry.causeIds,
            )
        }
    }

    private fun placeName(entity: EntityId): String = when {
        entity == EntityId.HOTEL -> "The Ashcroft"
        entity.value.startsWith("dept:") -> "The ${entity.value.removePrefix("dept:").replace('_', ' ')} team"
        else -> entity.value
    }

    private fun deptName(dept: Department): String = dept.name.lowercase().replace('_', ' ')

    private fun readableRoutine(descriptor: String): String {
        val verb = descriptor.substringBefore('|').lowercase()
        return when (verb) {
            "work" -> "steady work"
            "socialise" -> "gathering together"
            "eat" -> "eating together"
            "relax", "take_break" -> "taking their breaks together"
            else -> verb
        }
    }

    private fun readableTask(name: String): String = name.lowercase().replace('_', ' ')

    private const val CLOSE = 1.2
    private const val COLD = 0.5
    private const val TRUST_QUORUM = 3
    private const val TRUST_POSITIVE = 0.25
    private const val LINGER_MINUTES = 30L
}
