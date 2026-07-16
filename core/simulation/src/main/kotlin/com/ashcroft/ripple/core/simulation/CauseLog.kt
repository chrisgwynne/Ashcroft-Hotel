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
    private var seq = 0

    val isEmpty: Boolean get() = pending.isEmpty()

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

    /** Merge every pending node and its links into [graph] in a single pass (one copy, not one per node). */
    fun foldInto(graph: CausalGraph): CausalGraph {
        if (pending.isEmpty()) return graph
        val nodes = LinkedHashMap(graph.nodes)
        val edges = ArrayList(graph.edges)
        for ((node, parents) in pending) {
            nodes[node.id] = node
            for ((parentId, relation) in parents) {
                if (nodes.containsKey(parentId)) edges.add(com.ashcroft.ripple.core.model.CauseEdge(parentId, node.id, relation, 1.0))
            }
        }
        return CausalGraph(nodes, edges)
    }
}
