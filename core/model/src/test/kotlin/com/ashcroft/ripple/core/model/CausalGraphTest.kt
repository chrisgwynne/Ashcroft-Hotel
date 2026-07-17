package com.ashcroft.ripple.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CausalGraphTest {
    private fun node(id: String) = CauseNode(
        id = CauseId(id),
        simTime = SimTime(0),
        type = CauseType.DECISION,
        summaryKey = "x",
        significance = 0.5,
    )

    @Test
    fun addingNodesAndLinksBuildsAnAcyclicGraph() {
        val g = CausalGraph()
            .add(node("a"))
            .add(node("b"), parents = listOf(CauseId("a") to CauseRelation.CAUSED))
            .add(node("c"), parents = listOf(CauseId("b") to CauseRelation.MOTIVATED))
        assertEquals(3, g.size)
        assertTrue(g.isAcyclic())
        assertTrue(g.referencesResolve())
        assertEquals(setOf(CauseId("a"), CauseId("b")), g.ancestors(CauseId("c")))
        assertEquals(setOf(CauseId("b"), CauseId("c")), g.descendants(CauseId("a")))
    }

    @Test
    fun linksToMissingParentsAreDroppedNotDangling() {
        // A parent that does not exist is simply not linked — no dangling edge.
        val g = CausalGraph().add(node("b"), parents = listOf(CauseId("ghost") to CauseRelation.CAUSED))
        assertTrue(g.referencesResolve())
        assertTrue(g.parentsOf(CauseId("b")).isEmpty())
    }

    @Test
    fun rootsAreNodesWithNoParents() {
        val g = CausalGraph()
            .add(node("a"))
            .add(node("b"), parents = listOf(CauseId("a") to CauseRelation.CAUSED))
        assertEquals(listOf(CauseId("a")), g.roots().map { it.id })
    }

    @Test
    fun retainKeepsSignificanceRecencyAndReferencesAndDropsChurn() {
        // Ten nodes of rising significance; a very old but very significant one, and
        // an old pinned one, must survive a tight budget while middling churn is dropped.
        var g = CausalGraph()
        for (i in 0 until 10) {
            g = g.add(
                CauseNode(CauseId("n$i"), SimTime(i.toLong()), CauseType.DECISION, summaryKey = "x", significance = i * 0.1),
            )
        }
        val kept = g.retain(budget = 5, recentKeep = 2, pinned = setOf(CauseId("n0")))
        assertTrue("stays within a sensible bound", kept.size <= 6)
        assertTrue("the two most recent are kept", kept.node(CauseId("n9")) != null && kept.node(CauseId("n8")) != null)
        assertTrue("the pinned reference survives however old", kept.node(CauseId("n0")) != null)
        assertTrue("a low-significance middling node is dropped", kept.node(CauseId("n3")) == null)
        assertTrue("the result stays acyclic and reference-clean", kept.isAcyclic() && kept.referencesResolve())
    }

    @Test
    fun retainNeverOrphansAPinnedReferenceEvenBeyondBudget() {
        var g = CausalGraph()
        for (i in 0 until 10) {
            g = g.add(CauseNode(CauseId("n$i"), SimTime(i.toLong()), CauseType.DECISION, summaryKey = "x", significance = 0.1))
        }
        val pinned = (0 until 8).map { CauseId("n$it") }.toSet()
        val kept = g.retain(budget = 3, recentKeep = 1, pinned = pinned)
        assertTrue("every pinned reference is honoured even past budget", pinned.all { kept.node(it) != null })
        assertTrue("references still resolve", kept.referencesResolve())
    }

    @Test
    fun aCycleWouldBeDetected() {
        // Hand-craft a cyclic edge set (the builder never produces one) and confirm detection.
        val cyclic = CausalGraph(
            nodes = mapOf(CauseId("a") to node("a"), CauseId("b") to node("b")),
            edges = listOf(
                CauseEdge(CauseId("a"), CauseId("b"), CauseRelation.CAUSED, 1.0),
                CauseEdge(CauseId("b"), CauseId("a"), CauseRelation.CAUSED, 1.0),
            ),
        )
        assertFalse(cyclic.isAcyclic())
    }
}
