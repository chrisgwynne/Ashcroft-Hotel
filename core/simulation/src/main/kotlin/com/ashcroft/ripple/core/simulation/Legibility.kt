package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.RoleKind

/** One observer's opinion of a subject, with how sure they are and whether they saw it firsthand. */
data class ObserverOpinion(
    val observer: PersonId,
    val observerName: String,
    val standing: Double,
    val confidence: Double,
    val firsthand: Boolean,
    val evidenceCount: Int,
)

/**
 * How a person is regarded — as a spread of opinions, never a single averaged
 * score. Different observers hold different views; this keeps them apart and says
 * so plainly. [admirers] and [doubters] are the strongest views each way, and
 * [summary] characterises the split by audience ("trusted by staff, viewed
 * cautiously by management").
 */
data class ReputationView(
    val subject: PersonId,
    val subjectName: String,
    val summary: String,
    val admirers: List<ObserverOpinion>,
    val doubters: List<ObserverOpinion>,
    val divided: Boolean,
)

/** One direction of a relationship, read into plain language across the axes that stand out. */
data class DirectedFeeling(
    val fromName: String,
    val toName: String,
    val headline: String,
    val axes: List<String>,
)

/**
 * A relationship shown *both ways round*. Arthur's view of Maya may differ from
 * Maya's view of Arthur, and this keeps them separate — there is no single
 * combined friendship score. [differ] flags when the two directions materially
 * disagree.
 */
data class RelationshipView(
    val a: PersonId,
    val b: PersonId,
    val aTowardB: DirectedFeeling,
    val bTowardA: DirectedFeeling,
    val differ: Boolean,
    val sharedContext: List<String>,
)

/**
 * Turns the observer-specific standings and directional relationships of Phase 4
 * and 7 into readable views — the "Understand" layer. Pure, deterministic, and
 * careful never to collapse divergent opinion into an average or a relationship
 * into one number.
 */
object Legibility {
    fun reputationOf(state: WorldState, subject: PersonId): ReputationView {
        val subjectName = state.person(subject)?.name ?: subject.value
        val opinions = state.people.mapNotNull { observer ->
            if (observer.id == subject) return@mapNotNull null
            opinionOf(state, observer, subject)
        }
        val admirers = opinions.filter { it.standing >= LIKED }.sortedByDescending { it.standing }.take(TOP)
        val doubters = opinions.filter { it.standing <= DISLIKED }.sortedBy { it.standing }.take(TOP)
        return ReputationView(
            subject = subject,
            subjectName = subjectName,
            summary = summarise(state, subject, opinions),
            admirers = admirers,
            doubters = doubters,
            divided = admirers.isNotEmpty() && doubters.isNotEmpty(),
        )
    }

    private fun opinionOf(state: WorldState, observer: Person, subject: PersonId): ObserverOpinion? {
        val personal = observer.standings.personalOf(subject)
        val professional = observer.standings.professionalOf(subject)
        if (personal == null && professional == null) return null
        val values = buildList {
            personal?.let { add(it.overall to it.dimensions.values.sumOf { d -> d.confidence }) }
            professional?.let { p ->
                val conf = p.dimensions.values.sumOf { d -> d.confidence }
                val mean = if (conf <= 0.0) 0.0 else p.dimensions.values.sumOf { d -> d.value * d.confidence } / conf
                add(mean to conf)
            }
        }
        val totalConf = values.sumOf { it.second }
        val standing = if (totalConf <= 0.0) 0.0 else values.sumOf { it.first * it.second } / totalConf
        val evidenceIds = (personal?.sourceEvidenceIds ?: emptySet()) + (professional?.sourceEvidenceIds ?: emptySet())
        val firsthand = evidenceIds.any { id ->
            state.evidence.entries.firstOrNull { it.id == id }?.observedBy == observer.id
        }
        return ObserverOpinion(
            observer = observer.id,
            observerName = observer.name,
            standing = standing,
            confidence = totalConf / (totalConf + 4.0),
            firsthand = firsthand,
            evidenceCount = evidenceIds.size,
        )
    }

    /** Characterise the split by audience — staff, management, guests — without one global number. */
    private fun summarise(state: WorldState, subject: PersonId, opinions: List<ObserverOpinion>): String {
        if (opinions.isEmpty()) return "Not yet widely known."
        val byId = state.people.associateBy { it.id }

        fun groupMean(predicate: (RoleKind) -> Boolean): Double? {
            val group = opinions.filter { byId[it.observer]?.role?.let(predicate) == true }
            return if (group.isEmpty()) null else group.sumOf { it.standing } / group.size
        }
        val management = groupMean { it == RoleKind.GENERAL_MANAGER || it == RoleKind.DUTY_MANAGER || it == RoleKind.OWNER }
        val staff = groupMean { it.isStaff && it != RoleKind.GENERAL_MANAGER && it != RoleKind.DUTY_MANAGER && it != RoleKind.OWNER }
        val guests = groupMean { it == RoleKind.GUEST || it == RoleKind.RESIDENT }
        val parts = buildList {
            staff?.let { add("${verdict(it)} by the staff") }
            management?.let { add("${verdict(it)} by management") }
            guests?.let { add("${verdict(it)} among guests") }
        }
        val divided = opinions.any { it.standing >= LIKED } && opinions.any { it.standing <= DISLIKED }
        val lead = if (divided) "Opinion is divided — " else ""
        return lead + parts.joinToString(", ") + "."
    }

    private fun verdict(v: Double): String = when {
        v >= 0.5 -> "well regarded"
        v >= 0.15 -> "thought well of"
        v > -0.15 -> "seen neutrally"
        v > -0.5 -> "viewed cautiously"
        else -> "poorly regarded"
    }

    fun relationship(state: WorldState, a: PersonId, b: PersonId): RelationshipView {
        val pa = state.person(a)
        val pb = state.person(b)
        val aName = pa?.name ?: a.value
        val bName = pb?.name ?: b.value
        val aToB = directed(pa, b, aName, bName)
        val bToA = directed(pb, a, bName, aName)
        val differ = kotlin.math.abs(warmthOf(pa, b) - warmthOf(pb, a)) >= DIVERGENCE ||
            resents(pa, b) != resents(pb, a)
        return RelationshipView(a, b, aToB, bToA, differ, sharedContext(pa, pb, a, b))
    }

    private fun directed(person: Person?, other: PersonId, fromName: String, toName: String): DirectedFeeling {
        if (person == null) return DirectedFeeling(fromName, toName, "unknown", emptyList())
        val edge = person.relationships.with(other)
        val axes = RelationDimension.entries
            .map { it to edge[it] }
            .filter { kotlin.math.abs(it.second) >= AXIS_FLOOR }
            .sortedByDescending { kotlin.math.abs(it.second) }
            .map { "${axisName(it.first)} ${describe(it.first, it.second)}" }
        return DirectedFeeling(fromName, toName, headline(edge.warmth(), edge[RelationDimension.RESENTMENT]), axes)
    }

    private fun headline(warmth: Double, resentment: Double): String = when {
        resentment >= 0.5 -> "holds a grievance"
        warmth >= 1.2 -> "close"
        warmth >= 0.4 -> "friendly"
        warmth > 0.05 -> "warming"
        else -> "distant"
    }

    private fun sharedContext(pa: Person?, pb: Person?, a: PersonId, b: PersonId): List<String> = buildList {
        val aMem = pa?.memories?.count { it.subjectId == b } ?: 0
        val bMem = pb?.memories?.count { it.subjectId == a } ?: 0
        if (aMem + bMem > 0) add("$aMem shared memories on one side, $bMem on the other")
        pa?.lastConversation?.takeIf { it.withPerson == b }?.let { add("last spoke: ${it.summary}") }
    }

    private fun warmthOf(person: Person?, other: PersonId): Double = person?.relationships?.with(other)?.warmth() ?: 0.0

    private fun resents(person: Person?, other: PersonId): Boolean =
        (person?.relationships?.with(other)?.get(RelationDimension.RESENTMENT) ?: 0.0) >= 0.5

    private fun axisName(dimension: RelationDimension): String = dimension.name.lowercase().replace('_', ' ')

    private fun describe(dimension: RelationDimension, value: Double): String {
        val strong = kotlin.math.abs(value) >= 0.5
        return when {
            dimension == RelationDimension.RESENTMENT || dimension == RelationDimension.FEAR ->
                if (strong) "runs high" else "present"
            value >= 0.5 -> "runs high"
            value > 0.0 -> "is there"
            else -> "is low"
        }
    }

    private const val LIKED = 0.2
    private const val DISLIKED = -0.2
    private const val DIVERGENCE = 0.5
    private const val AXIS_FLOOR = 0.05
    private const val TOP = 5
}
