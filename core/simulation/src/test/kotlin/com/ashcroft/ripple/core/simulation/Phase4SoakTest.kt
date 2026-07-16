package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.ConversationReception
import com.ashcroft.ripple.core.model.InformationSource
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.ScoreComponentType
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Long-run validation for Phase 4. Runs the whole social simulation headless for
 * 30 days (several seeds) and a year (one seed), gathering the behavioural
 * metrics that show whether ordinary hotel life alone produces distinct lives —
 * conversation, knowledge spread, false beliefs, relationships and feeling — and
 * asserts the properties the phase must hold.
 */
class Phase4SoakTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    private data class Report(
        val seed: Long,
        val days: Int,
        val conversations: Int,
        val accepted: Int,
        val refused: Int,
        val misunderstood: Int,
        val rumoursCreated: Int,
        val maxFalseBeliefs: Int,
        val falseBeliefsCorrected: Int,
        val avgMemories: Double,
        val recallRate: Double,
        val relationshipAxesUsed: Int,
        val distinctStrongestEmotions: Int,
        val staffGuestInteractions: Int,
        val guestGuestInteractions: Int,
        val behaviouralSimilarity: Double,
        val chronicleEntries: Int,
        val chronicleWithCauses: Int,
        val repeatedActChains: Int,
    ) {
        fun perDay(v: Int) = "%.2f".format(v.toDouble() / days)

        fun print() {
            println("=== Ripple Phase 4 soak — seed $seed, $days days ===")
            println("conversations: $conversations (${perDay(conversations)}/day) — acc $accepted, ref $refused, misund $misunderstood")
            println("rumours created: $rumoursCreated; max false beliefs: $maxFalseBeliefs; corrected: $falseBeliefsCorrected")
            println("avg memories/person: %.1f; recall rate: %.3f".format(avgMemories, recallRate))
            val axes = RelationDimension.entries.size
            println("relationship axes used: $relationshipAxesUsed/$axes; strongest emotions: $distinctStrongestEmotions")
            println("staff↔guest interactions: $staffGuestInteractions; guest↔guest: $guestGuestInteractions")
            println("behavioural similarity (0=distinct,1=identical): %.3f".format(behaviouralSimilarity))
            println("chronicle entries: $chronicleEntries (${perDay(chronicleEntries)}/day); repeated act-chains: $repeatedActChains")
        }
    }

    private fun soak(seed: Long, days: Int): Report {
        var state = AshcroftScenario.initial(seed)
        val roleOf = state.people.associate { it.id to it.role }
        var conversations = 0
        var accepted = 0
        var refused = 0
        var misunderstood = 0
        var recallTicks = 0
        var staffGuest = 0
        var guestGuest = 0
        var maxFalse = 0
        var corrected = 0
        val lastConvAt = HashMap<String, Long>()
        val rumourTopics = HashSet<String>()
        val falseNow = HashSet<String>()
        val everFalse = HashSet<String>()
        val actChains = HashMap<String, Int>()
        val verbCounts = HashMap<String, HashMap<ActionVerb, Int>>()

        repeat(days * 24 * 60) {
            state = engine.step(state)
            for (p in state.people) {
                if (p.recalledMemoryId != null) recallTicks++
                verbCounts.getOrPut(p.id.value) { HashMap() }.merge(p.action.verb, 1, Int::plus)
                p.knowledge.all.filter { it.source == InformationSource.CONVERSATION }
                    .forEach { rumourTopics.add("${p.id.value}:${it.topicKey}") }
                val convo = p.lastConversation ?: continue
                if (convo.initiatedByMe && lastConvAt[p.id.value] != convo.at.epochMinutes) {
                    lastConvAt[p.id.value] = convo.at.epochMinutes
                    conversations++
                    when (convo.reception) {
                        ConversationReception.ACCEPTED -> accepted++
                        ConversationReception.REFUSED -> refused++
                        ConversationReception.MISUNDERSTOOD -> misunderstood++
                    }
                    val otherRole = roleOf[convo.withPerson]
                    val bothStaff = p.role.isStaff && otherRole?.isStaff == true
                    val bothGuest = !p.role.isStaff && otherRole?.isStaff == false
                    if (bothGuest) {
                        guestGuest++
                    } else if (!bothStaff) {
                        staffGuest++
                    }
                    actChains.merge("${p.id.value}->${convo.withPerson.value}:${convo.act}", 1, Int::plus)
                }
            }
            val false0 = WorldTruth.falseBeliefs(state).map { "${it.first.value}:${it.second.topicKey}" }.toSet()
            maxFalse = maxOf(maxFalse, false0.size)
            falseNow.filter { it !in false0 }.forEach { corrected++ }
            falseNow.clear()
            falseNow.addAll(false0)
            everFalse.addAll(false0)
        }

        return Report(
            seed = seed,
            days = days,
            conversations = conversations,
            accepted = accepted,
            refused = refused,
            misunderstood = misunderstood,
            rumoursCreated = rumourTopics.size,
            maxFalseBeliefs = maxFalse,
            falseBeliefsCorrected = corrected,
            avgMemories = state.people.map { it.memories.size }.average(),
            recallRate = recallTicks.toDouble() / (days * 24 * 60 * state.people.size),
            relationshipAxesUsed = axesUsed(state.people),
            distinctStrongestEmotions = state.people.mapNotNull { it.emotions.strongest }.toSet().size,
            staffGuestInteractions = staffGuest,
            guestGuestInteractions = guestGuest,
            behaviouralSimilarity = similarity(verbCounts),
            chronicleEntries = state.chronicle.size,
            chronicleWithCauses = state.chronicle.count { it.causeIds.isNotEmpty() },
            repeatedActChains = actChains.values.count { it >= REPEAT_FLAG },
        )
    }

    private fun axesUsed(people: List<Person>): Int =
        RelationDimension.entries.count { dim -> people.any { p -> p.relationships.all.any { kotlin.math.abs(it[dim]) > 0.02 } } }

    /** Average pairwise cosine similarity of action-verb profiles: 1.0 means everyone behaves identically. */
    private fun similarity(verbCounts: Map<String, Map<ActionVerb, Int>>): Double {
        val ids = verbCounts.keys.toList()
        if (ids.size < 2) return 0.0
        var sum = 0.0
        var pairs = 0
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                sum += cosine(verbCounts.getValue(ids[i]), verbCounts.getValue(ids[j]))
                pairs++
            }
        }
        return if (pairs == 0) 0.0 else sum / pairs
    }

    private fun cosine(a: Map<ActionVerb, Int>, b: Map<ActionVerb, Int>): Double {
        val keys = a.keys + b.keys
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (k in keys) {
            val x = (a[k] ?: 0).toDouble()
            val y = (b[k] ?: 0).toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))
    }

    @Test
    fun thirtyDaySoakAcrossSeedsProducesDistinctSocialLives() {
        val reports = listOf(1924L, 7L, 42L).map { soak(it, 30) }
        reports.forEach { it.print() }

        reports.forEach { r ->
            assertTrue("[seed ${r.seed}] people should actually talk", r.conversations > 0)
            assertTrue("[seed ${r.seed}] some overtures are accepted and some are not", r.accepted > 0 && r.refused > 0)
            assertTrue("[seed ${r.seed}] information should spread by conversation", r.rumoursCreated > 0)
            assertTrue("[seed ${r.seed}] false beliefs should arise", r.maxFalseBeliefs > 0)
            assertTrue("[seed ${r.seed}] and be corrected naturally", r.falseBeliefsCorrected > 0)
            assertTrue("[seed ${r.seed}] relationships span more than one axis", r.relationshipAxesUsed >= 3)
            assertTrue("[seed ${r.seed}] characters should not behave identically", r.behaviouralSimilarity < 0.995)
            assertTrue("[seed ${r.seed}] the chronicle should stay sparse", r.chronicleEntries <= r.days)
        }
        // Relationships must genuinely span many dimensions somewhere, never collapse to one score.
        assertTrue("relationships should exercise many axes", reports.maxOf { it.relationshipAxesUsed } >= 6)
        // Different seeds should yield noticeably different social histories.
        val convoCounts = reports.map { it.conversations }.toSet()
        assertTrue("different seeds should diverge", convoCounts.size > 1)
    }

    @Test
    fun oneYearSoakHoldsInvariantsAndStaysAlive() {
        val r = soak(1924L, 365)
        r.print()
        assertTrue("a year of life should hold conversations", r.conversations > 20)
        assertTrue("memories stay bounded over a year", r.avgMemories <= 40.0)
        assertTrue("relationships do not all collapse to one axis", r.relationshipAxesUsed >= 3)
        assertTrue("feeling stays varied across the cast", r.distinctStrongestEmotions >= 1)
        assertTrue("the chronicle remains sparse over a year", r.chronicleEntries <= 365)
        assertTrue("memory-driven recall keeps happening", r.recallRate > 0.0)
    }

    @Test
    fun memoriesAndRelationshipsActuallyInfluenceDecisions() {
        val state = engine.run(AshcroftScenario.initial(), 2_000)
        val influenced = state.people.any { p ->
            p.lastDecision?.consideredActions?.any { score ->
                score.components.any {
                    it.type == ScoreComponentType.RELATIONSHIP_IMPACT || it.type == ScoreComponentType.EMOTIONAL_FIT
                }
            } == true
        }
        assertTrue("relationships and feelings should show up in the reasons for choices", influenced)
    }

    private companion object {
        const val REPEAT_FLAG = 8
    }
}
