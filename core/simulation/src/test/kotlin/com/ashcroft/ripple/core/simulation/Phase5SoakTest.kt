package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.ConversationReception
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.HotelTaskStatus
import com.ashcroft.ripple.core.model.HotelTaskType
import com.ashcroft.ripple.core.model.InformationSource
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 long-run validation. Runs the whole hotel headless across several
 * seeds for 30 days (and a year for three of them), reporting the operational
 * and social metrics that show ordinary hotel life producing dense, varied,
 * believable behaviour — and asserting the phase's completion properties.
 */
class Phase5SoakTest {
    private val engine = SimulationEngine(AshcroftLayout.build())

    private data class Report(
        val seed: Long,
        val days: Int,
        val convos: Int,
        val accepted: Int,
        val refused: Int,
        val misunderstood: Int,
        val staffStaff: Int,
        val staffGuest: Int,
        val guestGuest: Int,
        val tasksGenerated: Int,
        val tasksCompleted: Int,
        val handovers: Int,
        val rumours: Int,
        val avgMemories: Double,
        val recallRate: Double,
        val emotionsOverTime: Int,
        val relAxes: Int,
        val behaviouralSimilarity: Double,
        val repeatedChains: Int,
        val chronicle: Int,
        val avgSatisfaction: Double,
    ) {
        fun perDay(v: Int) = v.toDouble() / days

        fun print() {
            println("=== Phase 5 soak — seed $seed, $days days ===")
            println("convos/day %.1f (acc %d ref %d misund %d)".format(perDay(convos), accepted, refused, misunderstood))
            println("contact: staff-staff $staffStaff, staff-guest $staffGuest, guest-guest $guestGuest")
            println("tasks generated $tasksGenerated, completed $tasksCompleted, handovers $handovers; rumours $rumours")
            println("avg memories %.1f, recall rate %.3f, guest satisfaction %.2f".format(avgMemories, recallRate, avgSatisfaction))
            println("emotions over time $emotionsOverTime, relationship axes $relAxes/${RelationDimension.entries.size}")
            println("behavioural similarity %.3f, repeated act-chains $repeatedChains, chronicle $chronicle".format(behaviouralSimilarity))
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun soak(seed: Long, days: Int): Report {
        var state = AshcroftScenario.initial(seed)
        val roleOf = state.people.associate { it.id to it.role }
        var acc = 0
        var ref = 0
        var mis = 0
        var ss = 0
        var sg = 0
        var gg = 0
        var recallTicks = 0
        val lastConvAt = HashMap<String, Long>()
        val rumourTopics = HashSet<String>()
        val emotions = HashSet<EmotionKind>()
        val tasksSeen = HashSet<String>()
        val tasksDone = HashSet<String>()
        val handovers = HashSet<String>()
        val chains = HashMap<String, Int>()
        val verbCounts = HashMap<String, HashMap<ActionVerb, Int>>()
        var convos = 0

        repeat(days * 24 * 60) {
            state = engine.step(state)
            for (t in state.tasks) {
                tasksSeen += t.id.value
                if (t.status == HotelTaskStatus.COMPLETED) tasksDone += t.id.value
                if (t.type == HotelTaskType.SHIFT_HANDOVER) handovers += t.id.value
            }
            for (p in state.people) {
                if (p.recalledMemoryId != null) recallTicks++
                p.emotions.strongest?.let { emotions += it }
                verbCounts.getOrPut(p.id.value) { HashMap() }.merge(p.action.verb, 1, Int::plus)
                p.knowledge.all.filter { it.source == InformationSource.CONVERSATION }
                    .forEach { rumourTopics += "${p.id.value}:${it.topicKey}" }
                val c = p.lastConversation ?: continue
                if (!c.initiatedByMe || lastConvAt[p.id.value] == c.at.epochMinutes) continue
                lastConvAt[p.id.value] = c.at.epochMinutes
                convos++
                when (c.reception) {
                    ConversationReception.ACCEPTED -> acc++
                    ConversationReception.REFUSED -> ref++
                    ConversationReception.MISUNDERSTOOD -> mis++
                }
                val otherStaff = roleOf[c.withPerson]?.isStaff == true
                when {
                    p.role.isStaff && otherStaff -> ss++
                    !p.role.isStaff && !otherStaff -> gg++
                    else -> sg++
                }
                chains.merge("${p.id.value}->${c.withPerson.value}:${c.act}", 1, Int::plus)
            }
        }
        return Report(
            seed, days, convos, acc, ref, mis, ss, sg, gg,
            tasksSeen.size, tasksDone.size, handovers.size, rumourTopics.size,
            state.people.map { it.memories.size }.average(),
            recallTicks.toDouble() / (days * 24 * 60 * state.people.size),
            emotions.size, axesUsed(state.people), similarity(verbCounts),
            chains.values.count { it >= REPEAT_FLAG }, state.chronicle.size,
            state.people.mapNotNull { it.stay?.satisfaction }.average(),
        )
    }

    private fun axesUsed(people: List<Person>): Int =
        RelationDimension.entries.count { dim -> people.any { p -> p.relationships.all.any { kotlin.math.abs(it[dim]) > 0.02 } } }

    private fun similarity(verbCounts: Map<String, Map<ActionVerb, Int>>): Double {
        val ids = verbCounts.keys.toList()
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
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (k in a.keys + b.keys) {
            val x = (a[k] ?: 0).toDouble()
            val y = (b[k] ?: 0).toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        return if (na == 0.0 || nb == 0.0) 0.0 else dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))
    }

    @Test
    fun thirtyDayAcrossFiveSeeds() {
        val reports = listOf(1924L, 7L, 42L, 100L, 2718L).map { soak(it, 30) }
        reports.forEach { it.print() }
        reports.forEach { r ->
            assertTrue("[${r.seed}] not socially dead", r.perDay(r.convos) >= 3.0)
            assertTrue("[${r.seed}] staff and guests cross paths", r.staffGuest > 0)
            assertTrue("[${r.seed}] operations generate and complete work", r.tasksCompleted > 0)
            assertTrue("[${r.seed}] several relationship axes", r.relAxes >= 5)
            assertTrue("[${r.seed}] varied feeling over time", r.emotionsOverTime >= 4)
            assertTrue("[${r.seed}] characters differ (below Phase 4 worst case)", r.behaviouralSimilarity < 0.85)
            assertTrue("[${r.seed}] chronicle stays sparse", r.chronicle <= r.days)
        }
        assertTrue("seeds diverge", reports.map { it.convos }.toSet().size > 1)
    }

    @Test
    fun oneYearAcrossThreeSeeds() {
        val reports = listOf(1924L, 7L, 42L).map { soak(it, 365) }
        reports.forEach { it.print() }
        reports.forEach { r ->
            assertTrue("[${r.seed}] a year stays alive", r.convos > 100)
            assertTrue("[${r.seed}] memories bounded", r.avgMemories <= 40.0)
            assertTrue("[${r.seed}] relationships keep many axes", r.relAxes >= 5)
            assertTrue("[${r.seed}] chronicle stays sparse over a year", r.chronicle <= r.days)
        }
    }

    private companion object {
        const val REPEAT_FLAG = 8
    }
}
