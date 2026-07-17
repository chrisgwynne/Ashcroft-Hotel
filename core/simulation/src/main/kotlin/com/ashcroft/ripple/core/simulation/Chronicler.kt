package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ChronicleEntry
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.FactTopic
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.PracticeRegistry
import com.ashcroft.ripple.core.model.PracticeStage
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.department

/**
 * Notices, after the fact, the handful of outcomes worth writing down. The
 * chronicler never creates events or nudges the world — it only reads the state
 * the simulation has already produced and records the rare moments that clear a
 * significance bar: two people growing notably close or cold, or a piece of word
 * that has genuinely spread. Everything routine is passed over in silence, and
 * every entry is written once (keyed) so nothing repeats.
 */
internal object Chronicler {
    private const val CLOSE = 0.6
    private const val COLD = 0.5
    private const val RUMOUR_REACH = 3
    private const val FAMILIAR_STAFF = 4
    private const val MIN_SIGNIFICANCE = 0.5

    fun update(
        previous: List<Person>,
        current: List<Person>,
        chronicle: List<ChronicleEntry>,
        now: SimTime,
        practicesBefore: PracticeRegistry = PracticeRegistry.EMPTY,
        practicesAfter: PracticeRegistry = PracticeRegistry.EMPTY,
    ): List<ChronicleEntry> {
        val known = chronicle.map { it.id }.toSet()
        val additions = mutableListOf<ChronicleEntry>()
        additions += relationshipMilestones(current, known, now)
        additions += rumourReach(current, known, now)
        additions += familiarGuests(current, known, now)
        additions += careerMilestones(previous, current, known, now)
        additions += customMilestones(current, practicesBefore, practicesAfter, known, now)
        return if (additions.isEmpty()) chronicle else chronicle + additions.filter { it.significance >= MIN_SIGNIFICANCE }
    }

    /**
     * Someone growing into their work — crossing, for the first time, into both a
     * real record and an active drive to advance — is a long-arc milestone. Read
     * from the aspiration the career layer derived, never a scripted promotion.
     */
    private fun careerMilestones(previous: List<Person>, current: List<Person>, known: Set<String>, now: SimTime): List<ChronicleEntry> {
        val was = previous.associateBy { it.id }
        return current.mapNotNull { person ->
            val now2 = person.aspiration ?: return@mapNotNull null
            val before = was[person.id]?.aspiration
            val arrived = now2.isPursuing && now2.isProven
            val alreadyThere = before != null && before.isPursuing && before.isProven
            val id = "career:${person.id.value}"
            if (arrived && !alreadyThere && id !in known) {
                ChronicleEntry(
                    id, now, "${person.name} has grown into their work and set their sights higher.",
                    significance = significance(now2.drive, involved = 1), involved = setOf(person.id),
                )
            } else {
                null
            }
        }
    }

    /**
     * A routine that a department's people have, between them, made customary — a
     * practice crossing into ESTABLISHED for the first time — is how a place's way of
     * doing things gets written down. Derived from the aggregate of habits, not decreed.
     */
    private fun customMilestones(
        current: List<Person>,
        before: PracticeRegistry,
        after: PracticeRegistry,
        known: Set<String>,
        now: SimTime,
    ): List<ChronicleEntry> {
        val entries = mutableListOf<ChronicleEntry>()
        for ((entity, practices) in after.byEntity) {
            val prior = before.of(entity)
            for ((descriptor, practice) in practices) {
                val justEstablished = practice.stage == PracticeStage.ESTABLISHED &&
                    prior[descriptor]?.stage != PracticeStage.ESTABLISHED
                val id = "custom:${entity.value}:$descriptor"
                if (justEstablished && id !in known) {
                    entries += ChronicleEntry(
                        id, now, "${placeName(entity)} has settled into a way of doing things of its own.",
                        significance = significance(practice.adoption, involved = membersOf(current, entity)),
                        involved = involvedIn(current, entity),
                    )
                }
            }
        }
        return entries
    }

    private fun placeName(entity: EntityId): String = when {
        entity == EntityId.HOTEL -> "The Ashcroft"
        entity.value.startsWith("dept:") -> "The ${entity.value.removePrefix("dept:").replace('_', ' ')} team"
        else -> entity.value
    }

    private fun involvedIn(people: List<Person>, entity: EntityId): Set<PersonId> = when {
        entity == EntityId.HOTEL -> people.filter { it.role.isStaff }.map { it.id }.toSet()
        entity.value.startsWith("dept:") -> {
            val dept = entity.value.removePrefix("dept:")
            people.filter { it.role.isStaff && it.role.department()?.name?.lowercase() == dept }.map { it.id }.toSet()
        }
        else -> emptySet()
    }

    private fun membersOf(people: List<Person>, entity: EntityId): Int = involvedIn(people, entity).size

    /** A bond crossing into genuine closeness, or into real coldness, is worth a line — once. */
    private fun relationshipMilestones(people: List<Person>, known: Set<String>, now: SimTime): List<ChronicleEntry> {
        val byId = people.associateBy { it.id }
        val entries = mutableListOf<ChronicleEntry>()
        for (person in people) {
            for (rel in person.relationships.all) {
                val other = byId[rel.other] ?: continue
                if (person.id.value >= other.id.value) continue // record each pair once, in a stable order
                val warmth = rel[RelationDimension.AFFECTION] + rel[RelationDimension.TRUST]
                val cold = rel[RelationDimension.RESENTMENT]
                if (warmth >= CLOSE * 2) {
                    val id = "close:${person.id.value}:${other.id.value}"
                    if (id !in known) {
                        entries += ChronicleEntry(
                            id, now, "${person.name} and ${other.name} have grown close.",
                            significance = significance(warmth, involved = 2), involved = setOf(person.id, other.id),
                        )
                    }
                } else if (cold >= COLD) {
                    val id = "cold:${person.id.value}:${other.id.value}"
                    if (id !in known) {
                        entries += ChronicleEntry(
                            id, now, "A coolness has settled between ${person.name} and ${other.name}.",
                            significance = significance(cold, involved = 2), involved = setOf(person.id, other.id),
                        )
                    }
                }
            }
        }
        return entries
    }

    /** When the same piece of hearsay is held by several people, word has spread. */
    private fun rumourReach(people: List<Person>, known: Set<String>, now: SimTime): List<ChronicleEntry> {
        val holdersByTopic = HashMap<String, MutableSet<PersonId>>()
        for (person in people) {
            for (belief in person.knowledge.all) {
                if (belief.isRumour && belief.claim.topic is FactTopic.NotableGuest) {
                    holdersByTopic.getOrPut(belief.topicKey) { mutableSetOf() }.add(person.id)
                }
            }
        }
        return holdersByTopic.mapNotNull { (topic, holders) ->
            val id = "rumour:$topic"
            if (holders.size >= RUMOUR_REACH && id !in known) {
                ChronicleEntry(
                    id, now, "Word has got around the staff about a notable guest.",
                    significance = significance(holders.size / 5.0, involved = holders.size), involved = holders,
                )
            } else {
                null
            }
        }
    }

    /** A guest served and greeted enough to be known by much of the staff has become a regular face. */
    private fun familiarGuests(people: List<Person>, known: Set<String>, now: SimTime): List<ChronicleEntry> {
        val staff = people.filter { it.role.isStaff }
        return people.filter { it.role == com.ashcroft.ripple.core.model.RoleKind.GUEST }.mapNotNull { guest ->
            val knownBy = staff.count { it.acquaintances.contains(guest.id) }
            val id = "familiar:${guest.id.value}"
            if (knownBy >= FAMILIAR_STAFF && id !in known) {
                ChronicleEntry(
                    id, now, "${guest.name} has become a familiar face to the staff.",
                    significance = significance(knownBy / 6.0, involved = knownBy + 1), involved = setOf(guest.id),
                )
            } else {
                null
            }
        }
    }

    private fun significance(intensity: Double, involved: Int): Double =
        (intensity.coerceIn(0.0, 1.0) * 0.6 + (involved.coerceAtMost(6) / 6.0) * 0.4).coerceIn(0.0, 1.0)
}
