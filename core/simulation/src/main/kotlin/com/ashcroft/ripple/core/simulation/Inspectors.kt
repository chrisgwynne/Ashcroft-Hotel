package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CausalGraph
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.SimTime
import kotlinx.serialization.json.Json

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

    // --- Causal graph windows (Phase 6) -----------------------------------------------------

    /** A quick read on the causal graph's shape and health, for dev panels. */
    fun causalStats(state: WorldState): List<String> {
        val g = state.causes
        val header = listOf(
            "nodes ${g.size}, edges ${g.edges.size}, roots ${g.roots().size}",
            "acyclic ${g.isAcyclic()}, references resolve ${g.referencesResolve()}",
        )
        val byType = g.nodes.values.groupingBy { it.type }.eachCount().entries
            .sortedByDescending { it.value }.take(TYPE_VIEW)
            .map { "${it.key.name.lowercase()} ${it.value}" }
        return header + byType
    }

    /** A person's causal history, newest first — everything the graph records about them. */
    fun causalHistory(state: WorldState, id: PersonId): List<String> =
        narrator(state).historyOf(id).map { "${stamp(it.at)} — ${it.text}" }

    /**
     * The layered "Why?" behind a person's most recent decision: what it was, what
     * directly brought it about, and the deeper history those grew from. Empty if
     * they have not made a recorded decision.
     */
    fun why(state: WorldState, id: PersonId): List<String> {
        val record = state.person(id)?.lastDecision ?: return emptyList()
        val story = narrator(state).tell(record.resultingCauseIds)
        return buildList {
            if (story.immediate.isNotEmpty()) add("Now: " + story.immediate.joinToString("; ") { it.text })
            if (story.becauseOf.isNotEmpty()) add("Because: " + story.becauseOf.joinToString("; ") { it.text })
            if (story.rootedIn.isNotEmpty()) add("Rooted in: " + story.rootedIn.joinToString("; ") { it.text })
        }
    }

    /** The chronicle with each milestone's contributing causes spelled out beneath it. */
    fun chronicleWithCauses(state: WorldState): List<String> {
        val narrator = narrator(state)
        return state.chronicle.flatMap { entry ->
            listOf("• ${entry.headline} (sig ${round(entry.significance)})") +
                entry.causeIds.mapNotNull { state.causes.node(it) }
                    .sortedByDescending { it.simTime.epochMinutes }
                    .map { "    ↳ ${stamp(it.simTime)} ${narratorLine(narrator, it.id)}" }
        }
    }

    /** A portable snapshot of the causal record — the graph serialised as JSON. */
    fun exportCauses(state: WorldState): String = EXPORT_JSON.encodeToString(CausalGraph.serializer(), state.causes)

    private fun narrator(state: WorldState): CausalNarrator {
        val names = state.people.associate { it.id to it.name }
        return CausalNarrator(state.causes, { names[it] ?: it.value }, { it.value })
    }

    private fun narratorLine(narrator: CausalNarrator, id: com.ashcroft.ripple.core.model.CauseId): String =
        narrator.tell(listOf(id), perLevel = 1).immediate.firstOrNull()?.text.orEmpty()

    private fun stamp(at: SimTime): String = "d${at.dayIndex} %02d:%02d".format(at.hourOfDay, at.minuteOfDay % 60)

    private fun round(v: Double): Double = kotlin.math.round(v * 100) / 100.0

    private val EXPORT_JSON = Json { prettyPrint = false }

    private const val MEMORY_VIEW = 8
    private const val TYPE_VIEW = 8
}
