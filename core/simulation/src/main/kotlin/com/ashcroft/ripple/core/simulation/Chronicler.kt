package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ChronicleEntry
import com.ashcroft.ripple.core.model.FactTopic
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.SimTime

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
    private const val MIN_SIGNIFICANCE = 0.5

    fun update(previous: List<Person>, current: List<Person>, chronicle: List<ChronicleEntry>, now: SimTime): List<ChronicleEntry> {
        val known = chronicle.map { it.id }.toSet()
        val additions = mutableListOf<ChronicleEntry>()
        additions += relationshipMilestones(current, known, now)
        additions += rumourReach(current, known, now)
        return if (additions.isEmpty()) chronicle else chronicle + additions.filter { it.significance >= MIN_SIGNIFICANCE }
    }

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

    private fun significance(intensity: Double, involved: Int): Double =
        (intensity.coerceIn(0.0, 1.0) * 0.6 + (involved.coerceAtMost(6) / 6.0) * 0.4).coerceIn(0.0, 1.0)
}
