package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CausalGraph
import com.ashcroft.ripple.core.model.CauseId
import com.ashcroft.ripple.core.model.CauseNode
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime

/** One readable line rendered from a real cause node — never invented, always backed by a record. */
data class CausalLine(val at: SimTime, val significance: Double, val text: String)

/**
 * A three-level account of why something happened, read straight from the causal
 * graph:
 *  - [immediate]: the thing itself;
 *  - [becauseOf]: what directly brought it about (its parents);
 *  - [rootedIn]: the deeper history those in turn grew from (grandparents and beyond).
 * Every line is a rendering of a stored [CauseNode]; the narrator summarises the
 * graph, it never fabricates a cause the simulation did not record.
 */
data class CausalStory(
    val immediate: List<CausalLine>,
    val becauseOf: List<CausalLine>,
    val rootedIn: List<CausalLine>,
)

/**
 * Turns the causal graph into plain language for the deep "Why?" and for
 * entity-history views. It walks real edges and renders real nodes; when it meets
 * a [summaryKey] it has no specific phrase for, it degrades to a readable form of
 * the key rather than guessing at meaning.
 */
class CausalNarrator(
    private val graph: CausalGraph,
    private val personName: (PersonId) -> String,
    private val roomName: (RoomId) -> String,
) {
    /** The layered story behind [start] (typically a decision's resulting cause ids). */
    fun tell(start: Collection<CauseId>, perLevel: Int = PER_LEVEL): CausalStory {
        val seen = HashSet<CauseId>()
        val immediate = start.mapNotNull { graph.node(it) }.also { seen += it.map(CauseNode::id) }
        val becauseOf = parentsOf(immediate, seen, perLevel)
        val rootedIn = parentsOf(becauseOf, seen, perLevel)
        return CausalStory(lines(immediate, perLevel), lines(becauseOf, perLevel), lines(rootedIn, perLevel))
    }

    /** Everything the graph records about a person, newest first — their causal history. */
    fun historyOf(person: PersonId, limit: Int = HISTORY_LIMIT): List<CausalLine> {
        val key = "person:${person.value}"
        return graph.nodes.values
            .filter { person in it.actorIds || key in it.subjectIds }
            .sortedByDescending { it.simTime.epochMinutes }
            .take(limit)
            .map(::line)
    }

    /** Everything the graph records as happening in a room, newest first. */
    fun historyOf(room: RoomId, limit: Int = HISTORY_LIMIT): List<CausalLine> {
        val key = "room:${room.value}"
        return graph.nodes.values
            .filter { it.locationId == room || key in it.subjectIds }
            .sortedByDescending { it.simTime.epochMinutes }
            .take(limit)
            .map(::line)
    }

    private fun parentsOf(nodes: List<CauseNode>, seen: MutableSet<CauseId>, perLevel: Int): List<CauseNode> =
        nodes.flatMap { graph.parentsOf(it.id) }
            .map { it.parentId }
            .mapNotNull { graph.node(it) }
            .filter { seen.add(it.id) }
            .sortedByDescending { it.significance }
            .take(perLevel)

    private fun lines(nodes: List<CauseNode>, perLevel: Int): List<CausalLine> =
        nodes.sortedByDescending { it.significance }.take(perLevel).map(::line)

    private fun line(node: CauseNode): CausalLine = CausalLine(node.simTime, node.significance, render(node))

    /** Render one node to a sentence from its recorded facts. */
    private fun render(node: CauseNode): String {
        val who = node.actorIds.joinToString(" and ") { personName(it) }.ifEmpty { "someone" }
        val where = node.locationId?.let { " in ${roomName(it)}" }.orEmpty()
        val head = node.summaryKey.substringBefore('.')
        return when (head) {
            "decision" -> "$who chose to ${humanVerb(node.summaryKey)}$where"
            "conversation" -> "$who ${conversationPhrase(node.summaryKey)}$where"
            "memory" -> "$who formed a memory$where"
            "recall" -> "$who was reminded of something that stirred them$where"
            "belief" -> beliefPhrase(who, node)
            "relationship" -> "$who felt a relationship shift after it"
            "service" -> "$who were brought together over service$where"
            "satisfaction" -> satisfactionPhrase(who, node)
            "task" -> taskPhrase(node, where)
            "chronicle" -> node.metadata["headline"] ?: "something worth remembering happened"
            else -> readable(node.summaryKey)
        }
    }

    private fun humanVerb(key: String): String = key.substringAfter('.').replace('_', ' ')

    private fun conversationPhrase(key: String): String = when (val act = key.substringAfter('.')) {
        "give_information" -> "shared news"
        "ask_question" -> "asked after something"
        "request_help" -> "asked for help"
        "offer_help" -> "offered help"
        else -> act.replace('_', ' ')
    }

    private fun beliefPhrase(who: String, node: CauseNode): String {
        val was = node.metadata["was"]
        val now = node.metadata["now"]
        return if (was != null && now != null) {
            "$who saw for themselves that it was '$now', not '$was'"
        } else {
            "$who corrected something they believed"
        }
    }

    private fun satisfactionPhrase(who: String, node: CauseNode): String = when {
        node.summaryKey.endsWith("neglected") -> "$who was left waiting, and minded it"
        else -> "$who was looked after"
    }

    private fun taskPhrase(node: CauseNode, where: String): String {
        val kind = node.metadata["type"]?.lowercase()?.replace('_', ' ')
            ?: node.summaryKey.substringAfterLast('.').replace('_', ' ')
        return when {
            node.summaryKey.startsWith("task.overdue") -> "a request for $kind went unanswered past its time$where"
            node.summaryKey.startsWith("task.completed") -> "$kind was seen to$where"
            else -> "$kind was called for$where"
        }
    }

    private fun readable(key: String): String = key.replace('.', ' ').replace('_', ' ')

    private companion object {
        const val PER_LEVEL = 5
        const val HISTORY_LIMIT = 40
    }
}
