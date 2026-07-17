package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * A short rolling window of what a person has recently done. Used for
 * *principled* anti-repetition: habits can reinforce routine, boredom can dull
 * repeated leisure, and repeated failures erode confidence — but necessities
 * (eating, sleeping, working) are never suppressed by a blanket novelty
 * penalty. Bounded so it never grows without limit.
 */
@Serializable
data class BehaviourHistory(
    val recentActions: List<ActionSignature> = emptyList(),
    val recentRooms: List<RoomId> = emptyList(),
    val recentInteractionTargets: List<PersonId> = emptyList(),
    val repeatedFailures: Map<String, Int> = emptyMap(),
) {
    fun timesRecently(signature: ActionSignature): Int = recentActions.count { it == signature }

    fun failuresFor(signature: ActionSignature): Int = repeatedFailures[signature.key()] ?: 0

    fun recordCompletion(signature: ActionSignature, room: RoomId?, target: PersonId?): BehaviourHistory = copy(
        recentActions = (recentActions + signature).takeLast(WINDOW),
        recentRooms = (recentRooms + listOfNotNull(room)).takeLast(WINDOW),
        recentInteractionTargets = (recentInteractionTargets + listOfNotNull(target)).takeLast(WINDOW),
    )

    fun recordFailure(signature: ActionSignature): BehaviourHistory =
        copy(repeatedFailures = repeatedFailures + (signature.key() to (failuresFor(signature) + 1)))

    private companion object {
        const val WINDOW = 8
    }
}

private fun ActionSignature.key(): String = "${verb.name}|${room?.value ?: ""}|${person?.value ?: ""}"
