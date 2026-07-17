package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.WorldPos

/**
 * Breadth-first shortest path over a [NavGraph]. Uniform step cost keeps it
 * simple and fully deterministic (neighbours are visited in a fixed order), so
 * the same start/goal always yields the same route — a requirement for
 * reproducible simulation.
 */
object Pathfinder {
    /**
     * @return the ordered positions to walk from [start] to [goal], excluding
     * [start] and including [goal]. Empty if already there or unreachable.
     */
    fun findPath(graph: NavGraph, start: WorldPos, goal: WorldPos): List<WorldPos> {
        if (start == goal) return emptyList()
        if (!graph.isWalkable(goal)) return emptyList()

        val cameFrom = HashMap<WorldPos, WorldPos>()
        val visited = HashSet<WorldPos>()
        val queue = ArrayDeque<WorldPos>()
        queue.add(start)
        visited.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == goal) return reconstruct(cameFrom, goal)
            for (next in graph.neighbours(current)) {
                if (visited.add(next)) {
                    cameFrom[next] = current
                    queue.add(next)
                }
            }
        }
        return emptyList()
    }

    private fun reconstruct(cameFrom: Map<WorldPos, WorldPos>, goal: WorldPos): List<WorldPos> {
        val path = ArrayList<WorldPos>()
        var node: WorldPos? = goal
        while (node != null && cameFrom.containsKey(node)) {
            path.add(node)
            node = cameFrom[node]
        }
        path.reverse()
        return path
    }
}
