package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension

/**
 * Developer-only windows into the hidden machinery — belief vs truth, rumours,
 * false beliefs, memories, emotions and relationship axes. These read the state
 * the player is *not* normally shown, for debugging and for the dev-mode panels.
 * Nothing here changes the world; it only reports it.
 */
object Inspectors {
    fun beliefs(state: WorldState, id: PersonId): List<String> =
        state.person(id)?.knowledge?.all?.map { b ->
            val truth = WorldTruth.valueFor(state, b.claim.topic)
            val flag = when {
                truth == null -> "?"
                WorldTruth.isFalse(state, b) -> "✗ (truth: $truth)"
                else -> "✓"
            }
            "${b.claim.topic.key} = ${b.claim.value} [${b.source}, conf ${round(b.confidence)}] $flag"
        }.orEmpty()

    fun rumours(state: WorldState, id: PersonId): List<String> =
        state.person(id)?.knowledge?.all?.filter { it.isRumour }
            ?.map { "${it.claim.topic.key} = ${it.claim.value} (conf ${round(it.confidence)})" }.orEmpty()

    fun falseBeliefs(state: WorldState, id: PersonId): List<String> =
        state.person(id)?.knowledge?.all?.filter { WorldTruth.isFalse(state, it) }
            ?.map { "believes ${it.claim.topic.key} = ${it.claim.value}" }.orEmpty()

    fun memories(state: WorldState, id: PersonId): List<String> =
        state.person(id)?.memories?.takeLast(MEMORY_VIEW)?.map { m ->
            val who = m.subjectId?.value ?: "—"
            "${m.kind} re:$who (val ${round(m.valence)}, imp ${round(m.importance)}, recalls ${m.recallCount})"
        }.orEmpty()

    fun emotions(state: WorldState, id: PersonId): List<String> =
        state.person(id)?.emotions?.let { e ->
            EmotionKind.entries.filter { e[it] > 0.05 }.map { "${it.name.lowercase()} ${round(e[it])}" }
        }.orEmpty()

    fun relationships(state: WorldState, id: PersonId): List<String> =
        state.person(id)?.relationships?.all?.map { rel ->
            val axes = RelationDimension.entries.filter { kotlin.math.abs(rel[it]) > 0.02 }
                .joinToString(", ") { "${it.name.lowercase()} ${round(rel[it])}" }
            "${rel.other.value}: $axes"
        }.orEmpty()

    fun conversation(state: WorldState, id: PersonId): String? =
        state.person(id)?.lastConversation?.let { "${it.act} → ${it.reception}: ${it.summary}" }

    /** Everyone whose picture of the hotel is currently out of step with the truth. */
    fun falseBeliefTally(state: WorldState): Int = WorldTruth.falseBeliefs(state).size

    fun chronicle(state: WorldState): List<String> = state.chronicle.map { "${it.headline} (sig ${round(it.significance)})" }

    /** Open hotel work right now — the operational backdrop. */
    fun openTasks(state: WorldState): List<String> = state.tasks.filter { it.isOpen }.map { t ->
        val where = state.person(t.requestedBy ?: PersonId(""))?.name?.let { " for $it" } ?: ""
        "${t.type.name.lowercase().replace('_', ' ')} at ${t.locationId.value}$where"
    }

    fun openTaskCount(state: WorldState): Int = state.tasks.count { it.isOpen }

    /** The task a person is presently attending to, if any. */
    fun dutyOf(state: WorldState, id: PersonId): String? {
        val person = state.person(id) ?: return null
        val taskId = person.action.targetTaskId ?: return null
        val task = state.tasks.firstOrNull { it.id == taskId } ?: return null
        return task.type.name.lowercase().replace('_', ' ')
    }

    private fun round(v: Double): Double = kotlin.math.round(v * 100) / 100.0

    private const val MEMORY_VIEW = 8
}
