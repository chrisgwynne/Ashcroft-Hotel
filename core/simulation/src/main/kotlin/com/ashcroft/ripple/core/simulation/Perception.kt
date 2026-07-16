package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Belief
import com.ashcroft.ripple.core.model.Claim
import com.ashcroft.ripple.core.model.FactTopic
import com.ashcroft.ripple.core.model.InformationSource
import com.ashcroft.ripple.core.model.KnowledgeBase
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.SimTime

/**
 * How people learn. Nobody in Ripple is omniscient: each tick a person only
 * takes in what is in front of them — who shares their room, how those people
 * seem, whether the room is busy, and whether anyone notable is about. Beliefs
 * formed this way are held with high (but not perfect) confidence.
 *
 * Crucially, leaving a room does *not* erase what was learned there: those
 * beliefs simply stop being refreshed and slowly [KnowledgeBase.weathered] with
 * time, so a person can go on believing a room is occupied long after its guest
 * has left. World truth moved; their picture did not. That gap is the point.
 */
internal object Perception {
    private const val OBSERVED_CONFIDENCE = 0.95
    private const val MOOD_CONFIDENCE = 0.8
    private const val NOTABLE_CONFIDENCE = 0.9
    private const val WEATHER_PER_TICK = 0.9995
    private const val NOTABLE_MONEY = 250

    /** Update [person]'s knowledge from what they can see and hear right now. */
    fun observe(person: Person, everyone: List<Person>, now: SimTime): KnowledgeBase {
        val room = person.location.roomId ?: return person.knowledge.weathered(WEATHER_PER_TICK)
        if (person.location.isMoving) return person.knowledge.weathered(WEATHER_PER_TICK)

        val here = everyone.filter { it.location.roomId == room }
        var kb = person.knowledge.weathered(WEATHER_PER_TICK)

        val occupancy = if (here.size > 1) "occupied" else "empty"
        kb = kb.learn(observed(FactTopic.RoomOccupancy(room), occupancy, OBSERVED_CONFIDENCE, now))

        for (other in here) {
            if (other.id == person.id) continue
            kb = kb.learn(observed(FactTopic.Whereabouts(other.id), room.value, OBSERVED_CONFIDENCE, now))
            kb = kb.learn(observed(FactTopic.PersonMood(other.id), moodWord(other), MOOD_CONFIDENCE, now))
            if (isNotable(other)) {
                kb = kb.learn(observed(FactTopic.NotableGuest(other.id), "notable", NOTABLE_CONFIDENCE, now))
            }
        }
        return kb
    }

    private fun observed(topic: FactTopic, value: String, confidence: Double, now: SimTime): Belief =
        Belief(Claim(topic, value), confidence, InformationSource.OBSERVED, now)

    /** A guest of means is the sort of arrival other people notice and talk about. */
    fun isNotable(person: Person): Boolean = person.role == RoleKind.GUEST && person.money >= NOTABLE_MONEY

    /** A one-word read on how someone seems, from their most pressing need. */
    fun moodWord(person: Person): String {
        val need = person.needs.mostPressing()
        if (person.needs[need] > 0.5f) return "content"
        return when (need) {
            NeedKind.HUNGER -> "hungry"
            NeedKind.REST -> "tired"
            NeedKind.SOCIAL -> "lonely"
            NeedKind.HYGIENE -> "dishevelled"
            NeedKind.PRIVACY -> "crowded"
            NeedKind.COMFORT -> "unsettled"
            NeedKind.SAFETY -> "uneasy"
            NeedKind.PURPOSE -> "restless"
            NeedKind.RECOGNITION -> "overlooked"
            NeedKind.AUTONOMY -> "hemmed-in"
        }
    }
}
