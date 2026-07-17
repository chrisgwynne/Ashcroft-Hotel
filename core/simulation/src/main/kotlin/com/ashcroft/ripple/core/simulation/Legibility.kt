package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.EvidenceDimension
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.department

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

/** One titled section of a readable life. */
data class ProfileSection(val title: String, val lines: List<String>)

/**
 * A person read as a life, not a record — grouped into the sections a player
 * actually thinks in: what they are doing *now*, the shape of their *life*, the
 * *people* who matter to them, what they *believe*, and who they are becoming
 * (*identity*). Every line is derived from real state; nothing is invented.
 */
data class ProfileView(
    val name: String,
    val subtitle: String,
    val now: ProfileSection,
    val life: ProfileSection,
    val people: ProfileSection,
    val beliefs: ProfileSection,
    val identity: ProfileSection,
)

/** One strand of a place's character: a short label and the evidence-grounded sentence behind it. */
data class IdentityTrait(val label: String, val sentence: String)

/**
 * A readable identity card for a place — its character explained through evidence,
 * the customs it has grown into, and an honest note when nothing has settled yet.
 * Never a bare label or a performance score.
 */
data class IdentityView(
    val title: String,
    val subtitle: String,
    val settled: Boolean,
    val character: List<IdentityTrait>,
    val customs: List<String>,
    val observations: Int,
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

    /** Assemble a person's full, readable profile, grouped into the five natural sections. */
    fun profile(state: WorldState, id: PersonId): ProfileView? {
        val person = state.person(id) ?: return null
        val reputation = reputationOf(state, id)
        return ProfileView(
            name = person.name,
            subtitle = person.role.name.lowercase().replace('_', ' '),
            now = ProfileSection(
                "Now",
                buildList {
                    add("where: ${person.location.roomId?.value ?: "—"}")
                    add("doing: ${person.action.verb.name.lowercase().replace('_', ' ')} (${person.action.phase.name.lowercase()})")
                    person.goals.maxByOrNull { it.priority }?.let { add("trying to ${it.type.name.lowercase().replace('_', ' ')}") }
                    person.emotions.strongest?.let { add("feeling ${it.name.lowercase()}") }
                    person.stay?.let { add("stay satisfaction ${round(it.satisfaction)}") }
                    Inspectors.why(state, id).firstOrNull()?.let { add(it) }
                },
            ),
            life = ProfileSection(
                "Life",
                buildList {
                    val milestones = state.chronicle.filter { id in it.involved }.takeLast(TOP)
                    addAll(milestones.map { "· ${it.headline}" })
                    person.memories.filter { it.importance >= 0.4 }.takeLast(TOP)
                        .forEach { add("remembers: ${it.kind.name.lowercase().replace('_', ' ')}") }
                    person.aspiration?.takeIf { it.drive > 0.0 }
                        ?.let { add("aspires: ${it.focus.name.lowercase()} (drive ${round(it.drive)})") }
                    val settled = person.habits.settled().values.sortedByDescending { it.strength }.take(TOP)
                    if (settled.isNotEmpty()) add("habits: ${settled.joinToString(", ") { readableCustom(it.key) }}")
                },
            ),
            people = ProfileSection(
                "People",
                buildList {
                    person.relationships.all.sortedByDescending { it.warmth() }.take(TOP).forEach {
                        add("${state.person(it.other)?.name ?: it.other.value}: ${headline(it.warmth(), it[RelationDimension.RESENTMENT])}")
                    }
                    if (reputation.divided) add("others are divided about them")
                },
            ),
            beliefs = ProfileSection(
                "Beliefs",
                buildList {
                    addAll(Inspectors.beliefs(state, id).take(TOP))
                    Inspectors.rumours(state, id).take(2).forEach { add("hearsay: $it") }
                },
            ),
            identity = ProfileSection(
                "Identity",
                buildList {
                    add(reputation.summary)
                    addAll(Inspectors.aspirationOf(state, id))
                },
            ),
        )
    }

    fun departmentIdentity(state: WorldState, dept: Department): IdentityView {
        val entity = EntityId.department(dept)
        val team = state.people.count { it.role.isStaff && it.role.department() == dept }
        return identityOf(state, entity, "The ${dept.name.lowercase().replace('_', ' ')} team", "$team on the team")
    }

    fun hotelIdentity(state: WorldState): IdentityView =
        identityOf(state, EntityId.HOTEL, "The Ashcroft", "the house as a whole")

    /**
     * Reads a place's culture into evidence-grounded prose: each characteristic trait
     * becomes a sentence that says *what it is* and *what backs it up* — never a bare
     * label. Customs it has grown into are listed in plain language. If no character
     * has settled yet, it says so honestly rather than inventing one.
     */
    private fun identityOf(state: WorldState, entity: EntityId, title: String, subtitle: String): IdentityView {
        val profile = state.culture.of(entity)
        val pronounced = profile?.pronounced().orEmpty()
        val character = pronounced.entries
            .sortedByDescending { kotlin.math.abs(it.value.value) }
            .map { (dimension, standing) ->
                IdentityTrait(
                    label = traitLabel(dimension, standing.value),
                    sentence = "${traitLabel(dimension, standing.value).replaceFirstChar { it.uppercase() }} — " +
                        "borne out across ${profile?.observations ?: 0} recorded moments here.",
                )
            }
        val customs = state.practices.customary(entity).map { readableCustom(it) }
        return IdentityView(
            title = title,
            subtitle = subtitle,
            settled = pronounced.isNotEmpty(),
            character = character,
            customs = customs,
            observations = profile?.observations ?: 0,
        )
    }

    private fun traitLabel(dimension: EvidenceDimension, value: Double): String {
        val pair = TRAIT_WORDS[dimension] ?: return dimension.name.lowercase()
        return if (value >= 0.0) pair.first else pair.second
    }

    private fun readableCustom(descriptor: String): String {
        val verb = descriptor.substringBefore('|').lowercase()
        return when (verb) {
            "work" -> "keeping steadily to the work"
            "socialise" -> "gathering together off-shift"
            "eat" -> "taking meals together"
            "relax", "take_break" -> "sharing their breaks"
            else -> verb
        }
    }

    private fun round(v: Double): Double = kotlin.math.round(v * 100) / 100.0

    /** The adjective each culture axis reads as, positive and negative. */
    private val TRAIT_WORDS: Map<EvidenceDimension, Pair<String, String>> = mapOf(
        EvidenceDimension.RELIABILITY to ("reliable" to "unreliable"),
        EvidenceDimension.COMPETENCE to ("capable" to "struggling"),
        EvidenceDimension.WARMTH to ("warm and supportive" to "cool"),
        EvidenceDimension.GENEROSITY to ("warm and supportive" to "cool"),
        EvidenceDimension.RESPONSIVENESS to ("attentive" to "slow to respond"),
        EvidenceDimension.DISCRETION to ("discreet" to "indiscreet"),
        EvidenceDimension.CALMNESS to ("unflappable" to "prone to friction"),
        EvidenceDimension.CLEANLINESS to ("immaculate" to "unkempt"),
        EvidenceDimension.PRESTIGE to ("well regarded" to "overlooked"),
        EvidenceDimension.CONSISTENCY to ("steady" to "erratic"),
        EvidenceDimension.INNOVATION to ("inventive" to "set in its ways"),
        EvidenceDimension.TRADITION to ("traditional" to "unceremonious"),
    )

    private const val LIKED = 0.2
    private const val DISLIKED = -0.2
    private const val DIVERGENCE = 0.5
    private const val AXIS_FLOOR = 0.05
    private const val TOP = 5
}
