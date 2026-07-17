package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * The kind of meaningful state change a cause node records. Nodes are created
 * ONLY for changes that matter — never for movement steps, need decay, awareness
 * checks, rejected candidates or routine clock ticks. Each type names a real
 * thing that happened in the simulation, so an explanation can always be backed
 * by a record rather than invented after the fact.
 */
@Serializable
enum class CauseType {
    DECISION,
    ACTION_STARTED,
    ACTION_COMPLETED,
    ACTION_FAILED,
    OBSERVATION,
    BELIEF_CREATED,
    BELIEF_CORRECTED,
    INFORMATION_SHARED,
    MEMORY_CREATED,
    MEMORY_RECALLED,
    EMOTIONAL_CHANGE,
    RELATIONSHIP_CHANGE,
    GOAL_CREATED,
    GOAL_COMPLETED,
    GOAL_ABANDONED,
    COMMITMENT_CREATED,
    COMMITMENT_MISSED,
    TASK_CREATED,
    TASK_COMPLETED,
    TASK_OVERDUE,
    SERVICE_INTERACTION,
    SATISFACTION_CHANGE,
    CONVERSATION_ACT,
    CHRONICLE_SIGNIFICANCE,
}

/**
 * How one cause relates to another. Never a single generic "caused" — the
 * relation carries meaning: what enabled, informed, motivated, reinforced,
 * contradicted or was remembered from what.
 */
@Serializable
enum class CauseRelation {
    CAUSED,
    ENABLED,
    INFORMED,
    MOTIVATED,
    REINFORCED,
    CONTRADICTED,
    PREVENTED,
    DELAYED,
    REVEALED,
    REMEMBERED_FROM,
}

/**
 * A single node in the causal graph: one meaningful thing that happened, at a
 * time, involving some people and subjects, somewhere, with a readable summary
 * key and a significance that may later be revised upward as consequences
 * accrue. [subjectIds] holds entity keys (e.g. "person:maya", "room:bar",
 * "task:bar:ethan") so a node can point at whatever it concerns.
 */
@Serializable
data class CauseNode(
    val id: CauseId,
    val simTime: SimTime,
    val type: CauseType,
    val actorIds: Set<PersonId> = emptySet(),
    val subjectIds: Set<String> = emptySet(),
    val locationId: RoomId? = null,
    val summaryKey: String,
    val significance: Double,
    val metadata: Map<String, String> = emptyMap(),
)

/** A typed, directed link from a parent cause to the child it helped bring about. */
@Serializable
data class CauseEdge(
    val parentId: CauseId,
    val childId: CauseId,
    val relation: CauseRelation,
    val strength: Double,
)

/**
 * A typed directed acyclic graph of everything meaningful that has happened.
 * Immutable: adding a node returns a new graph. Edges only ever point from
 * earlier causes to the later effects they helped produce, so the graph stays
 * acyclic by construction. This is the record the "Why?" and chronicle layers
 * read from — they may summarise it, never fabricate it.
 */
@Serializable
data class CausalGraph(
    val nodes: Map<CauseId, CauseNode> = emptyMap(),
    val edges: List<CauseEdge> = emptyList(),
) {
    val size: Int get() = nodes.size

    fun node(id: CauseId): CauseNode? = nodes[id]

    /** Add a node and its links to already-existing parent causes (missing parents are skipped). */
    fun add(node: CauseNode, parents: List<Pair<CauseId, CauseRelation>> = emptyList(), strength: Double = 1.0): CausalGraph {
        val newEdges = parents.filter { nodes.containsKey(it.first) }
            .map { CauseEdge(it.first, node.id, it.second, strength) }
        return copy(nodes = nodes + (node.id to node), edges = edges + newEdges)
    }

    /**
     * A deterministic size bound: keep the most recent [maxNodes] and drop the
     * edges among the rest. Recency alone is a blunt instrument — it forgets what
     * mattered as readily as what did not — so [retain] supersedes it for long runs.
     */
    fun prunedTo(maxNodes: Int): CausalGraph {
        if (nodes.size <= maxNodes) return this
        val keep = nodes.keys.toList().takeLast(maxNodes).toSet()
        return CausalGraph(
            nodes.filterKeys { it in keep },
            edges.filter { it.parentId in keep && it.childId in keep },
        )
    }

    /**
     * Retain what history has shown to matter within a bounded budget:
     *  - the most recent [recentKeep] nodes (the live present), always;
     *  - every [pinned] node still in the graph (a cause a memory, belief, task or
     *    chronicle entry still points at — pruning must never orphan a reference
     *    someone still holds);
     *  - and then, to fill the remaining [budget], the most *significant* of the
     *    older nodes — those a real consequence reinforced.
     * Routine churn falls away; milestones and their chains persist. Edges among
     * dropped nodes go with them, so the result stays acyclic and reference-clean
     * by construction. [budget] is a soft floor: pinned references are honoured even
     * when they exceed it, so the record can never contradict itself.
     */
    fun retain(budget: Int, recentKeep: Int, pinned: Set<CauseId> = emptySet()): CausalGraph {
        if (nodes.size <= budget) return this
        val order = nodes.keys.toList()
        val keep = LinkedHashSet<CauseId>()
        keep += order.takeLast(recentKeep.coerceAtMost(order.size))
        keep += pinned.filter { nodes.containsKey(it) }
        val remaining = budget - keep.size
        if (remaining > 0) {
            keep += nodes.values.asSequence()
                .filter { it.id !in keep }
                .sortedWith(compareByDescending<CauseNode> { it.significance }.thenByDescending { it.simTime.epochMinutes })
                .take(remaining)
                .map { it.id }
        }
        if (keep.size >= nodes.size) return this
        return CausalGraph(
            nodes.filterKeys { it in keep },
            edges.filter { it.parentId in keep && it.childId in keep },
        )
    }

    fun parentsOf(id: CauseId): List<CauseEdge> = edges.filter { it.childId == id }

    fun childrenOf(id: CauseId): List<CauseEdge> = edges.filter { it.parentId == id }

    /** All ancestor cause ids of [id], nearest first is not guaranteed; bounded by [limit]. */
    fun ancestors(id: CauseId, limit: Int = 256): Set<CauseId> = walk(id, limit) { parentsOf(it).map { e -> e.parentId } }

    fun descendants(id: CauseId, limit: Int = 256): Set<CauseId> = walk(id, limit) { childrenOf(it).map { e -> e.childId } }

    fun roots(): List<CauseNode> {
        val hasParent = edges.map { it.childId }.toSet()
        return nodes.values.filter { it.id !in hasParent }
    }

    /** True if the edge set contains no directed cycle — an invariant the builder must preserve. */
    fun isAcyclic(): Boolean {
        val adjacency = edges.groupBy({ it.parentId }, { it.childId })
        val state = HashMap<CauseId, Int>() // 0 = visiting, 1 = done

        fun visit(n: CauseId): Boolean {
            when (state[n]) {
                0 -> return false
                1 -> return true
            }
            state[n] = 0
            for (next in adjacency[n].orEmpty()) if (!visit(next)) return false
            state[n] = 1
            return true
        }
        return nodes.keys.all { visit(it) }
    }

    /** Every edge references nodes that exist — no dangling parents or children. */
    fun referencesResolve(): Boolean = edges.all { nodes.containsKey(it.parentId) && nodes.containsKey(it.childId) }

    private fun walk(start: CauseId, limit: Int, step: (CauseId) -> List<CauseId>): Set<CauseId> {
        val seen = LinkedHashSet<CauseId>()
        val stack = ArrayDeque(step(start))
        while (stack.isNotEmpty() && seen.size < limit) {
            val next = stack.removeLast()
            if (seen.add(next)) stack.addAll(step(next))
        }
        return seen
    }
}
