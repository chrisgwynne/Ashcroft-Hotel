package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Belief
import com.ashcroft.ripple.core.model.ConversationAct
import com.ashcroft.ripple.core.model.ConversationReception
import com.ashcroft.ripple.core.model.ConversationRecord
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.FactTopic
import com.ashcroft.ripple.core.model.InformationSource
import com.ashcroft.ripple.core.model.MemoryKind
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.Tendencies
import com.ashcroft.ripple.core.model.TraitKind

/**
 * Turns a bid to talk into a concrete exchange. The initiator picks an *act*
 * from their goals, feelings, beliefs and standing with the other person; the
 * recipient then independently decides how to receive it — a chosen apology can
 * still be refused, an offer of help declined, a piece of information
 * misunderstood. Each act carries its own relationship, emotional, knowledge and
 * memory effects. Nothing is scripted; the same act between different people, or
 * the same pair in a different mood, plays out differently.
 */
internal object ConversationSystem {
    fun converse(actor: Person, target: Person, present: Set<PersonId>, roll: Float, now: SimTime): Pair<Person, Person> {
        val act = chooseAct(actor, target, present, now)
        val reception = receive(act, actor, target, roll)
        return applyEffects(act, reception, actor, target, present, now)
    }

    // --- Act selection: what the initiator chooses to say ------------------------------------

    private fun chooseAct(actor: Person, target: Person, present: Set<PersonId>, now: SimTime): ConversationAct {
        val rel = actor.relationships.with(target.id)
        val guilty = actor.emotions[EmotionKind.GUILT] > 0.3 && rel[RelationDimension.RESENTMENT] < 0.3
        val wantsToHelp = believesStressed(actor, target) && actor.personality[TraitKind.AGREEABLENESS] > 0.5
        val delighted = rel[RelationDimension.AFFECTION] > 0.4 && actor.emotions[EmotionKind.HAPPINESS] > 0.3
        val wantsHelp = wantsHelpFrom(actor, target)
        val venting = actor.emotions[EmotionKind.FRUSTRATION] > 0.4 && rel[RelationDimension.AFFECTION] < 0.1

        return when {
            owesThanks(actor, target) -> ConversationAct.THANK
            guilty -> ConversationAct.APOLOGISE
            wantsToHelp -> ConversationAct.OFFER_HELP
            delighted -> ConversationAct.PRAISE
            wantsHelp && rel[RelationDimension.TRUST] > 0.1 -> ConversationAct.REQUEST_HELP
            wantsHelp -> ConversationAct.ASK_QUESTION
            news(actor, present, now).isNotEmpty() -> ConversationAct.GIVE_INFORMATION
            venting -> ConversationAct.COMPLAIN
            rel[RelationDimension.FAMILIARITY] < 0.15 -> ConversationAct.GREET
            else -> ConversationAct.SMALL_TALK
        }
    }

    private fun believesStressed(actor: Person, target: Person): Boolean =
        actor.knowledge.about(FactTopic.PersonMood(target.id))?.claim?.value?.let { it != "content" } == true

    private fun owesThanks(actor: Person, target: Person): Boolean =
        actor.memories.any { it.subjectId == target.id && it.kind == MemoryKind.WAS_HELPED } &&
            actor.lastConversation?.let { it.withPerson == target.id && it.act == ConversationAct.THANK } != true

    private fun wantsHelpFrom(actor: Person, target: Person): Boolean =
        target.role.isStaff &&
            actor.goals.any {
                it.type == com.ashcroft.ripple.core.model.GoalType.SATISFY_NEED ||
                    it.type == com.ashcroft.ripple.core.model.GoalType.FULFIL_WORK
            }

    // --- Reception: the recipient decides, independently --------------------------------------

    private fun receive(act: ConversationAct, actor: Person, target: Person, roll: Float): ConversationReception {
        val rel = target.relationships.with(actor.id)
        val base = target.personality[TraitKind.SOCIABILITY] * 0.4f +
            (1f - target.needs[NeedKind.SOCIAL]) * 0.3f +
            target.relationships.with(actor.id).warmth().coerceIn(-0.5, 0.5).toFloat() * 0.3f
        val actMod = when (act) {
            ConversationAct.GREET, ConversationAct.THANK, ConversationAct.PRAISE, ConversationAct.SMALL_TALK -> 0.35f
            ConversationAct.GIVE_INFORMATION, ConversationAct.REASSURE -> 0.25f
            ConversationAct.ASK_QUESTION -> 0.15f + target.personality[TraitKind.AGREEABLENESS] * 0.2f
            ConversationAct.OFFER_HELP -> 0.2f
            ConversationAct.REQUEST_HELP ->
                target.personality[TraitKind.AGREEABLENESS] * 0.4f + rel[RelationDimension.TRUST].toFloat() * 0.3f
            ConversationAct.APOLOGISE -> 0.3f - rel[RelationDimension.RESENTMENT].toFloat() * 0.6f
            ConversationAct.COMPLAIN, ConversationAct.DISAGREE -> -0.15f
            ConversationAct.END_CONVERSATION, ConversationAct.REBUFF -> 0.5f
        }
        val willingness = (base + actMod).coerceIn(0.05f, 0.95f)
        return when {
            roll < willingness * MISUNDERSTAND_BAND && act.carriesInformation() -> ConversationReception.MISUNDERSTOOD
            roll < willingness -> ConversationReception.ACCEPTED
            else -> ConversationReception.REFUSED
        }
    }

    private fun ConversationAct.carriesInformation() = this == ConversationAct.GIVE_INFORMATION || this == ConversationAct.ASK_QUESTION

    // --- Effects -----------------------------------------------------------------------------

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun applyEffects(
        act: ConversationAct,
        reception: ConversationReception,
        actor: Person,
        target: Person,
        present: Set<PersonId>,
        now: SimTime,
    ): Pair<Person, Person> {
        val accepted = reception == ConversationReception.ACCEPTED
        var a = actor
        var t = target

        // Everyone who actually engages feels a little less alone and grows familiar.
        if (reception != ConversationReception.REFUSED) {
            a = a.relieveSocial(0.10f).warmTo(target.id, RelationDimension.FAMILIARITY, 0.05)
            t = t.relieveSocial(0.07f).warmTo(actor.id, RelationDimension.FAMILIARITY, 0.05)
        }

        when (act) {
            ConversationAct.GREET, ConversationAct.SMALL_TALK -> if (accepted) {
                a = a.warmTo(target.id, RelationDimension.AFFECTION, 0.03).feel(EmotionKind.HAPPINESS, 0.08)
                t = t.warmTo(actor.id, RelationDimension.AFFECTION, 0.02)
            }
            ConversationAct.GIVE_INFORMATION -> {
                val shared = news(actor, present, now)
                if (reception == ConversationReception.ACCEPTED) {
                    t = t.learnAll(shared).warmTo(actor.id, RelationDimension.TRUST, 0.03)
                } else if (reception == ConversationReception.MISUNDERSTOOD) {
                    t = t.learnAll(shared.map { it.garbled() })
                }
            }
            ConversationAct.ASK_QUESTION -> if (reception != ConversationReception.REFUSED) {
                val answer = news(target, present, now)
                val heard = if (reception == ConversationReception.MISUNDERSTOOD) answer.map { it.garbled() } else answer
                a = a.learnAll(heard).warmTo(target.id, RelationDimension.TRUST, 0.02)
            }
            ConversationAct.REQUEST_HELP -> if (accepted) {
                a = a.remember(MemoryKind.WAS_HELPED, target.id, 0.7, 0.6, now)
                    .warmTo(target.id, RelationDimension.GRATITUDE, 0.12).warmTo(target.id, RelationDimension.DEPENDENCE, 0.05)
                t = t.remember(MemoryKind.HELPED_SOMEONE, actor.id, 0.5, 0.4, now)
                    .warmTo(actor.id, RelationDimension.AFFECTION, 0.04).feel(EmotionKind.CONFIDENCE, 0.06).tally(Tendencies.HELP)
            } else {
                a = a.feel(EmotionKind.FRUSTRATION, 0.12).warmTo(target.id, RelationDimension.TRUST, -0.05)
            }
            ConversationAct.OFFER_HELP -> if (accepted) {
                t = t.remember(MemoryKind.WAS_HELPED, actor.id, 0.7, 0.6, now).warmTo(actor.id, RelationDimension.GRATITUDE, 0.12)
                a = a.remember(MemoryKind.HELPED_SOMEONE, target.id, 0.5, 0.4, now).feel(EmotionKind.HAPPINESS, 0.08)
            }
            ConversationAct.THANK -> {
                a = a.feel(EmotionKind.HAPPINESS, 0.05)
                t = t.remember(MemoryKind.WAS_THANKED, actor.id, 0.5, 0.4, now)
                    .warmTo(actor.id, RelationDimension.AFFECTION, 0.05).feel(EmotionKind.HAPPINESS, 0.08)
            }
            ConversationAct.APOLOGISE -> if (accepted) {
                a = a.calm(EmotionKind.GUILT, 0.5)
                t = t.warmTo(actor.id, RelationDimension.RESENTMENT, -0.15).warmTo(actor.id, RelationDimension.TRUST, 0.04)
            } else {
                a = a.feel(EmotionKind.EMBARRASSMENT, 0.15)
            }
            ConversationAct.PRAISE -> if (accepted) {
                t = t.remember(MemoryKind.WAS_PRAISED, actor.id, 0.7, 0.5, now).feel(EmotionKind.CONFIDENCE, 0.10)
                // The same praise lands differently: warmth in most, a flicker of
                // suspicion in the guarded — so relationship axes diverge, never move as one.
                t = if (t.personality[TraitKind.AGREEABLENESS] < 0.35f) {
                    t.warmTo(actor.id, RelationDimension.RESPECT, 0.02).warmTo(actor.id, RelationDimension.RESENTMENT, 0.03)
                } else {
                    t.warmTo(actor.id, RelationDimension.AFFECTION, 0.05)
                }
            }
            ConversationAct.REASSURE -> if (accepted) {
                t = t.calm(EmotionKind.ANXIETY, 0.4).warmTo(actor.id, RelationDimension.TRUST, 0.05)
            }
            ConversationAct.COMPLAIN -> {
                a = a.calm(EmotionKind.FRUSTRATION, 0.3)
                if (!accepted) t = t.warmTo(actor.id, RelationDimension.AFFECTION, -0.04).feel(EmotionKind.FRUSTRATION, 0.05)
            }
            ConversationAct.DISAGREE -> {
                a = a.warmTo(target.id, RelationDimension.RESPECT, 0.02)
                t = t.warmTo(actor.id, RelationDimension.AFFECTION, -0.03)
            }
            ConversationAct.END_CONVERSATION, ConversationAct.REBUFF -> Unit
        }

        if (reception == ConversationReception.REFUSED && act !in NEUTRAL_ON_REFUSAL) {
            a = a.remember(MemoryKind.WAS_IGNORED, target.id, -0.4, 0.4, now)
                .feel(EmotionKind.EMBARRASSMENT, 0.12).warmTo(target.id, RelationDimension.RESENTMENT, 0.04)
        }

        a = a.tallyForActor(act, reception)
        t = t.tallyForRecipient(reception)

        val summary = summarise(act, reception, target.name)
        val topic = if (act.carriesInformation()) news(actor, present, now).firstOrNull()?.topicKey else null
        val signature = com.ashcroft.ripple.core.model.ConversationContextSignature(
            recipientId = target.id,
            act = act,
            subjectKey = topic,
            locationId = actor.location.roomId,
            taskDriven = false,
        )
        a = a.copy(
            lastConversation = ConversationRecord(target.id, true, act, reception, now, topic, null, summary),
            acquaintances = a.acquaintances + target.id,
            conversationLog = (a.conversationLog + signature).takeLast(CONVERSATION_LOG),
        )
        t = t.copy(
            lastConversation = ConversationRecord(actor.id, false, act, reception, now, topic, null, mirror(act, reception, actor.name)),
            acquaintances = t.acquaintances + actor.id,
        )
        return a to t
    }

    // --- Knowledge helpers -------------------------------------------------------------------

    /** News the speaker can pass on: things about people or places the listener cannot see for themselves. */
    private fun news(speaker: Person, present: Set<PersonId>, now: SimTime): List<Belief> {
        val elsewhere = speaker.knowledge.all.filter { belief ->
            when (val topic = belief.claim.topic) {
                is FactTopic.NotableGuest -> true
                is FactTopic.Whereabouts -> topic.person !in present
                is FactTopic.PersonMood -> topic.person !in present
                is FactTopic.RoomOccupancy -> topic.room != speaker.location.roomId
            }
        }
        return (
            elsewhere.filter { it.claim.topic is FactTopic.NotableGuest } +
                elsewhere.sortedByDescending { it.confidence }
        ).distinctBy { it.topicKey }.take(SHARE_LIMIT).map {
            Belief(it.claim, (it.confidence * HEARSAY_DECAY).coerceIn(0.0, 1.0), InformationSource.CONVERSATION, now, speaker.id)
        }
    }

    private fun Belief.garbled(): Belief = copy(confidence = (confidence * 0.5).coerceIn(0.0, 1.0), source = InformationSource.OVERHEARD)

    // --- Small Person transforms -------------------------------------------------------------

    /**
     * Warm (or cool) a relationship axis, with the change *saturating*: the closer
     * an axis already is to its extreme, the less each further exchange moves it.
     * This is what stops ordinary repeated pleasantries from drifting every bond
     * to maximum warmth — affection has to be earned, and plateaus.
     */
    private fun Person.warmTo(other: PersonId, dim: RelationDimension, delta: Double): Person {
        val current = relationships.with(other)[dim]
        val room = if (delta >= 0) 1.0 - current else 1.0 + current
        return copy(relationships = relationships.adjust(other, mapOf(dim to delta * room.coerceIn(0.0, 1.0))))
    }

    private fun Person.tally(key: String): Person =
        copy(tendencyEvidence = tendencyEvidence + (key to (tendencyEvidence[key] ?: 0) + 1))

    /** Record the initiator's deed, so repeated patterns surface as readable tendencies. */
    private fun Person.tallyForActor(act: ConversationAct, reception: ConversationReception): Person = when {
        reception != ConversationReception.ACCEPTED -> this
        act == ConversationAct.OFFER_HELP -> tally(Tendencies.HELP)
        act == ConversationAct.THANK -> tally(Tendencies.THANK)
        act == ConversationAct.PRAISE -> tally(Tendencies.PRAISE)
        act == ConversationAct.COMPLAIN || act == ConversationAct.DISAGREE -> tally(Tendencies.CONFRONT)
        else -> this
    }

    /** A recipient who turns someone away is, over time, seen to avoid engaging. */
    private fun Person.tallyForRecipient(reception: ConversationReception): Person =
        if (reception == ConversationReception.REFUSED) tally(Tendencies.AVOID) else this

    private fun Person.feel(kind: EmotionKind, delta: Double): Person = copy(emotions = emotions.stirred(mapOf(kind to delta)))

    private fun Person.calm(kind: EmotionKind, fraction: Double): Person =
        copy(emotions = emotions.with(kind, emotions[kind] * (1 - fraction)))

    private fun Person.relieveSocial(delta: Float): Person = copy(needs = needs.with(NeedKind.SOCIAL, needs[NeedKind.SOCIAL] + delta))

    private fun Person.learnAll(beliefs: List<Belief>): Person = copy(knowledge = beliefs.fold(knowledge) { kb, b -> kb.learn(b) })

    private fun Person.remember(kind: MemoryKind, subject: PersonId, valence: Double, importance: Double, now: SimTime): Person {
        val memory = com.ashcroft.ripple.core.model.Memory(
            id = com.ashcroft.ripple.core.model.MemoryId("m:${id.value}:${now.epochMinutes}:$kind"),
            ownerId = id,
            occurredAt = now,
            kind = kind,
            subjectId = subject,
            valence = valence,
            importance = importance,
            placeId = location.roomId,
        )
        return copy(memories = (memories + memory).takeLast(MAX_MEMORIES))
    }

    private fun summarise(act: ConversationAct, reception: ConversationReception, name: String): String = when (reception) {
        ConversationReception.ACCEPTED -> "${phrase(act)} $name"
        ConversationReception.REFUSED -> "tried to ${phraseBare(act)} $name, but was rebuffed"
        ConversationReception.MISUNDERSTOOD -> "${phrase(act)} $name, but was misunderstood"
    }

    private fun mirror(act: ConversationAct, reception: ConversationReception, name: String): String = when (reception) {
        ConversationReception.ACCEPTED -> "$name ${phraseBare(act)} them"
        ConversationReception.REFUSED -> "brushed off $name"
        ConversationReception.MISUNDERSTOOD -> "half-followed $name"
    }

    private fun phrase(act: ConversationAct): String = phraseBare(act).replaceFirstChar { it.uppercase() }

    private fun phraseBare(act: ConversationAct): String = when (act) {
        ConversationAct.GREET -> "greeted"
        ConversationAct.SMALL_TALK -> "made small talk with"
        ConversationAct.ASK_QUESTION -> "asked something of"
        ConversationAct.GIVE_INFORMATION -> "shared news with"
        ConversationAct.REQUEST_HELP -> "asked for help from"
        ConversationAct.OFFER_HELP -> "offered to help"
        ConversationAct.THANK -> "thanked"
        ConversationAct.APOLOGISE -> "apologised to"
        ConversationAct.REASSURE -> "reassured"
        ConversationAct.COMPLAIN -> "complained to"
        ConversationAct.PRAISE -> "praised"
        ConversationAct.DISAGREE -> "disagreed with"
        ConversationAct.END_CONVERSATION -> "wound things up with"
        ConversationAct.REBUFF -> "brushed off"
    }

    private const val HEARSAY_DECAY = 0.6
    private const val SHARE_LIMIT = 2
    private const val MAX_MEMORIES = 40
    private const val CONVERSATION_LOG = 10
    private const val MISUNDERSTAND_BAND = 0.18f
    private val NEUTRAL_ON_REFUSAL = setOf(
        ConversationAct.COMPLAIN,
        ConversationAct.DISAGREE,
        ConversationAct.END_CONVERSATION,
        ConversationAct.REBUFF,
    )
}
