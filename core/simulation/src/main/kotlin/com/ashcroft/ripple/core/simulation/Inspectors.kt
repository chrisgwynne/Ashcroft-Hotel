package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CausalGraph
import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.department
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

    // --- Long-arc identity windows (Phase 7G) ------------------------------------------------

    /**
     * How [observer] regards [subject], socially and professionally — each axis a
     * running mean with the confidence behind it, and each standing carrying the
     * count of evidence it was built from. This is a *belief*, not a truth; another
     * observer may hold a different one.
     */
    fun standingHeld(state: WorldState, observer: PersonId, subject: PersonId): List<String> {
        val holder = state.person(observer) ?: return emptyList()
        val out = mutableListOf<String>()
        holder.standings.personalOf(subject)?.let { s ->
            out += "socially (${s.sourceEvidenceIds.size} pieces of evidence):"
            out += s.dimensions.entries.sortedByDescending { kotlin.math.abs(it.value.value) }
                .map { "  ${it.key.name.lowercase()} ${round(it.value.value)} (conf ${round(it.value.confidence)})" }
        }
        holder.standings.professionalOf(subject)?.let { s ->
            out += "professionally (${s.sourceEvidenceIds.size} pieces of evidence):"
            out += s.dimensions.entries.sortedByDescending { kotlin.math.abs(it.value.value) }
                .map { "  ${it.key.name.lowercase()} ${round(it.value.value)} (conf ${round(it.value.confidence)})" }
        }
        return out
    }

    /** The pronounced traits of a place's culture, each with the weight and evidence behind it. */
    fun cultureOf(state: WorldState, entity: EntityId): List<String> {
        val profile = state.culture.of(entity) ?: return emptyList()
        val pronounced = profile.pronounced()
        val header = "from ${profile.observations} observations, ${profile.sourceEvidenceIds.size} on record"
        if (pronounced.isEmpty()) return listOf("$header — no settled character yet")
        return listOf(header) + pronounced.entries.sortedByDescending { kotlin.math.abs(it.value.value) }
            .map { "${it.key.name.lowercase()} ${round(it.value.value)} (weight ${round(it.value.weight)})" }
    }

    /** The customs a place has grown into, with each one's stage and how widely it is held. */
    fun practicesOf(state: WorldState, entity: EntityId): List<String> =
        state.practices.of(entity).values
            .sortedByDescending { it.adoption }
            .map { "${it.descriptor} — ${it.stage.name.lowercase()} (adoption ${round(it.adoption)})" }

    /** A staff member's emergent career aspiration, with the evidence behind the recognition it rests on. */
    fun aspirationOf(state: WorldState, id: PersonId): List<String> {
        val aspiration = state.person(id)?.aspiration ?: return emptyList()
        return listOf(
            "focus: ${aspiration.focus.name.lowercase()}",
            "drive ${round(aspiration.drive)}${if (aspiration.isPursuing) " (pursuing)" else ""}",
            "demonstrated ${round(aspiration.demonstrated)}${if (aspiration.isProven) " (proven)" else ""}",
            "recognition ${round(aspiration.recognition)} (${aspiration.sourceEvidenceIds.size} pieces of evidence)",
        )
    }

    /** A read-only identity card for a person — who they are becoming, in the round. */
    fun personIdentity(state: WorldState, id: PersonId): List<String> {
        val person = state.person(id) ?: return emptyList()
        val out = mutableListOf("${person.name} — ${person.role.name.lowercase().replace('_', ' ')}")
        val settled = person.habits.settled()
        if (settled.isNotEmpty()) {
            out += "routines: " + settled.values.sortedByDescending { it.strength }
                .take(IDENTITY_VIEW).joinToString(", ") { "${it.key} ${round(it.strength)}" }
        }
        person.aspiration?.takeIf { it.drive > 0.0 }?.let {
            out += "aspiration: ${it.focus.name.lowercase()} drive ${round(it.drive)}"
        }
        return out
    }

    /** A read-only identity card for a department — its character and its customs. */
    fun departmentIdentity(state: WorldState, department: Department): List<String> {
        val entity = EntityId.department(department)
        val members = state.people.count { it.role.isStaff && it.role.department() == department }
        return listOf("The ${department.name.lowercase().replace('_', ' ')} — $members on the team") +
            prefixed("character", cultureOf(state, entity)) +
            prefixed("customs", practicesOf(state, entity))
    }

    /** A read-only identity card for the hotel as a whole. */
    fun hotelIdentity(state: WorldState): List<String> =
        listOf("The Ashcroft") +
            prefixed("character", cultureOf(state, EntityId.HOTEL)) +
            prefixed("customs", practicesOf(state, EntityId.HOTEL))

    /**
     * The "Why?" behind a person's last decision, told three ways: what *objectively*
     * happened (the causal record), what the actor *believed* (the standings and
     * perceptions they were acting on, which may be wrong), and what was *decisive*
     * (the score components that actually tipped the balance). The three can, and
     * often should, diverge — a decision can be objectively suboptimal yet perfectly
     * reasonable given what the person believed.
     */
    fun whyThreeWays(state: WorldState, id: PersonId): Map<String, List<String>> {
        val person = state.person(id) ?: return emptyMap()
        val record = person.lastDecision ?: return emptyMap()
        val objective = why(state, id)
        val decisive = record.consideredActions.firstOrNull { it.candidate.id == record.chosenAction.id }
            ?.components?.sortedByDescending { kotlin.math.abs(it.value) }
            ?.take(DECISIVE_VIEW)
            ?.map { "${it.type.name.lowercase()} ${round(it.value)} — ${it.explanationKey}" }
            .orEmpty()
        val believed = buildList {
            val target = record.chosenAction.targetPerson
            if (target != null) {
                addAll(prefixed("of ${state.person(target)?.name ?: target.value}", standingHeld(state, id, target)))
            }
            person.aspiration?.takeIf { it.isPursuing }?.let { add("driven by their own aspiration to advance") }
            if (isEmpty()) add("nothing about anyone else bore on it")
        }
        return mapOf("objective" to objective, "believed" to believed, "decisive" to decisive)
    }

    private fun prefixed(label: String, lines: List<String>): List<String> =
        if (lines.isEmpty()) emptyList() else listOf("$label:") + lines.map { "  $it" }

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
    private const val IDENTITY_VIEW = 4
    private const val DECISIVE_VIEW = 4
}
