package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CausalGraph
import com.ashcroft.ripple.core.model.CauseId
import com.ashcroft.ripple.core.model.CauseNode
import com.ashcroft.ripple.core.model.CauseRelation
import com.ashcroft.ripple.core.model.CauseType
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime

/**
 * A per-tick accumulator of causal records. The engine emits a node for each
 * *meaningful* change it makes during a tick (never for movement, decay,
 * awareness or rejected candidates), links it to the causes that produced it,
 * and at the end folds everything into the world's [CausalGraph]. Ids are
 * assigned in deterministic emission order, so the graph a run produces is fully
 * reproducible.
 */
internal class CauseLog(private val now: SimTime) {
    private val pending = ArrayList<Pair<CauseNode, List<Pair<CauseId, CauseRelation>>>>()
    private val reinforcements = ArrayList<Pair<CauseId, Double>>()
    private var seq = 0

    val isEmpty: Boolean get() = pending.isEmpty() && reinforcements.isEmpty()

    fun emit(
        type: CauseType,
        summaryKey: String,
        significance: Double,
        actors: Set<PersonId> = emptySet(),
        subjects: Set<String> = emptySet(),
        location: RoomId? = null,
        metadata: Map<String, String> = emptyMap(),
        parents: List<Pair<CauseId, CauseRelation>> = emptyList(),
    ): CauseId {
        val id = CauseId("c:${now.epochMinutes}:${seq++}")
        pending.add(
            CauseNode(id, now, type, actors, subjects, location, summaryKey, significance, metadata) to parents,
        )
        return id
    }

    /**
     * Record that a real consequence has reflected back on [cause]: raise its
     * significance and echo a decaying fraction up its causal ancestry. This is how
     * a cause becomes more significant *because of what it led to* — an unattended
     * task that soured a guest, a slight that later broke a friendship — so the
     * pruner can keep what history proved to matter.
     */
    fun reinforce(cause: CauseId, amount: Double) {
        if (amount > 0.0) reinforcements.add(cause to amount)
    }

    /** Merge every pending node, link and reinforcement into [graph] in a single pass (one copy, not one per change). */
    fun foldInto(graph: CausalGraph): CausalGraph {
        if (pending.isEmpty() && reinforcements.isEmpty()) return graph
        val nodes = LinkedHashMap(graph.nodes)
        val edges = ArrayList(graph.edges)
        for ((node, parents) in pending) {
            nodes[node.id] = node
            for ((parentId, relation) in parents) {
                if (nodes.containsKey(parentId)) edges.add(com.ashcroft.ripple.core.model.CauseEdge(parentId, node.id, relation, 1.0))
            }
        }
        if (reinforcements.isNotEmpty()) {
            val parentsOf = HashMap<CauseId, MutableList<CauseId>>()
            for (e in edges) parentsOf.getOrPut(e.childId) { ArrayList() }.add(e.parentId)
            for ((id, amount) in reinforcements) applyReinforce(nodes, parentsOf, id, amount, REINFORCE_DEPTH)
        }
        return CausalGraph(nodes, edges)
    }

    private fun applyReinforce(
        nodes: MutableMap<CauseId, CauseNode>,
        parentsOf: Map<CauseId, List<CauseId>>,
        id: CauseId,
        amount: Double,
        depth: Int,
    ) {
        if (depth <= 0 || amount < REINFORCE_EPSILON) return
        val node = nodes[id] ?: return
        nodes[id] = node.copy(significance = (node.significance + amount).coerceAtMost(1.0))
        for (parent in parentsOf[id].orEmpty()) applyReinforce(nodes, parentsOf, parent, amount * REINFORCE_DECAY, depth - 1)
    }

    private companion object {
        const val REINFORCE_DEPTH = 4
        const val REINFORCE_DECAY = 0.5
        const val REINFORCE_EPSILON = 0.01
    }
}
