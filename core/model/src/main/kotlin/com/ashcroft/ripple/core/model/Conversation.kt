package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * An explicit thing one person says to another. Conversations are no longer a
 * single opaque "social success": a person chooses an *act* out of their goals,
 * feelings, beliefs and standing with the other, and the other independently
 * decides how to receive it. Nothing here is scripted — the act emerges from
 * circumstance, and its outcome is the recipient's to determine.
 */
@Serializable
enum class ConversationAct(val opener: Boolean = false) {
    GREET(opener = true),
    SMALL_TALK(opener = true),
    ASK_QUESTION,
    GIVE_INFORMATION,
    REQUEST_HELP,
    OFFER_HELP,
    THANK,
    APOLOGISE,
    REASSURE,
    COMPLAIN,
    PRAISE,
    DISAGREE,
    END_CONVERSATION,
    REBUFF,
}

/**
 * A compact fingerprint of the *context* an exchange happened in: who, what act,
 * about what, where, and toward which goal or task. Two exchanges with the same
 * signature are "the same conversation again" — used to damp repeated chains
 * when nothing has changed, while leaving task-driven repetition alone.
 */
@Serializable
data class ConversationContextSignature(
    val recipientId: PersonId,
    val act: ConversationAct,
    val subjectKey: String? = null,
    val locationId: RoomId? = null,
    val taskDriven: Boolean = false,
)

/** How the recipient received the act. Choosing to speak never guarantees a welcome. */
@Serializable
enum class ConversationReception {
    ACCEPTED,
    REFUSED,
    MISUNDERSTOOD,
}

/**
 * A record of a single conversational exchange, retained on both parties so the
 * UI can show "what was last said" and so misunderstandings and refusals leave a
 * trace. [understoodValue] captures what the *listener* took away, which can
 * differ from what was meant when the act is misunderstood.
 */
@Serializable
data class ConversationRecord(
    val withPerson: PersonId,
    val initiatedByMe: Boolean,
    val act: ConversationAct,
    val reception: ConversationReception,
    val at: SimTime,
    val topicKey: String? = null,
    val understoodValue: String? = null,
    val summary: String,
)
