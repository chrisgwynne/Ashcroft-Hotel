package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.Aspiration
import com.ashcroft.ripple.core.model.Department
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.ProfessionalDimension
import com.ashcroft.ripple.core.model.ProfessionalStanding
import com.ashcroft.ripple.core.model.RelationDimension
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.department

/**
 * How careers grow and how leaders shape them — both derived, neither scripted.
 *
 * Aspirations form from what a person has actually made of themselves: an
 * ambitious temperament times a real, settled record of competent work, lifted a
 * little by the recognition leaders have shown them. Leaders, in turn, recognise
 * the people they *believe* are doing well — reading their own observer-specific,
 * possibly-mistaken professional standings, never an omniscient truth — and that
 * recognition feeds back into the recognised person's standing, needs and drive.
 *
 * Nothing here changes anyone's role. There is no promotion event and no
 * threshold that flips a title; a career is only ever the accumulation of these
 * ordinary reinforcements. Fully deterministic.
 */
internal object Careers {
    fun apply(people: List<Person>, now: SimTime): List<Person> {
        val leadership = now.epochMinutes % LEADERSHIP_PERIOD == 0L
        val development = now.epochMinutes % DEVELOPMENT_PERIOD == 0L
        if (!leadership && !development) return people
        val byId = people.associateBy { it.id }.toMutableMap()
        if (leadership) recogniseFromPerceivedStanding(byId, now)
        if (development) developAspirations(byId, now)
        return people.map { byId.getValue(it.id) }
    }

    /**
     * Each manager recognises the one subordinate present that they rate most
     * highly — by their own held professional standing, which another manager need
     * not share and which may be wrong. Recognition is a real leadership act: it
     * meets the recognised person's need to be seen, warms their view of the leader,
     * and lifts their drive, carrying the very evidence ids the standing was built on.
     */
    private fun recogniseFromPerceivedStanding(byId: MutableMap<PersonId, Person>, now: SimTime) {
        val managers = byId.values.filter { it.role.isStaff && it.role.department() == Department.MANAGEMENT }.sortedBy { it.id.value }
        for (manager in managers) {
            val present = byId.values.filter {
                it.id != manager.id &&
                    it.role.isStaff &&
                    it.role.department() != Department.MANAGEMENT &&
                    it.location.roomId == manager.location.roomId
            }
            if (present.isEmpty()) continue
            val chosen = present.maxWithOrNull(
                compareBy({ regardOf(manager.standings.professionalOf(it.id)) }, { it.id.value }),
            ) ?: continue
            val standing = manager.standings.professionalOf(chosen.id) ?: continue
            val regard = regardOf(standing)
            if (regard <= 0.0) continue
            val subject = byId.getValue(chosen.id)
            val prior = subject.aspiration ?: Aspiration(focus = focusFor(subject.role))
            byId[chosen.id] = subject.copy(
                needs = subject.needs.with(
                    NeedKind.RECOGNITION,
                    (subject.needs[NeedKind.RECOGNITION] + (RECOGNITION_RELIEF * regard).toFloat()).coerceAtMost(1f),
                ),
                relationships = subject.relationships.adjust(
                    manager.id,
                    mapOf(RelationDimension.GRATITUDE to 0.05 * regard, RelationDimension.TRUST to 0.03 * regard),
                ),
                aspiration = prior.copy(
                    recognition = (prior.recognition + RECOGNITION_STEP * regard).coerceIn(0.0, 1.0),
                    sourceEvidenceIds = (prior.sourceEvidenceIds + standing.sourceEvidenceIds).take(TRAIL).toSet(),
                    updatedAt = now,
                ),
            )
        }
    }

    /**
     * Re-derive each staff member's aspiration from who they have become: ambition
     * scaled by the record of competent work they have actually settled into, plus
     * the fading memory of recognition received. High drive needs both the wish and
     * the work behind it — temperament alone does not make a career.
     */
    private fun developAspirations(byId: MutableMap<PersonId, Person>, now: SimTime) {
        for (person in byId.values) {
            if (!person.role.isStaff || person.role.department() == null) continue
            val demonstrated = person.habits.settled().filterKeys { it.startsWith("WORK|") }
                .values.maxOfOrNull { it.strength } ?: 0.0
            val ambition = person.personality[com.ashcroft.ripple.core.model.TraitKind.AMBITION].toDouble()
            val prior = person.aspiration ?: Aspiration(focus = focusFor(person.role))
            val recognition = prior.recognition * RECOGNITION_DECAY
            val drive = (ambition * (BASE_DRIVE + DEMONSTRATED_DRIVE * demonstrated) + recognition * RECOGNITION_DRIVE)
                .coerceIn(0.0, 1.0)
            byId[person.id] = person.copy(
                aspiration = prior.copy(
                    focus = focusFor(person.role),
                    drive = drive,
                    demonstrated = demonstrated,
                    recognition = recognition,
                    updatedAt = now,
                ),
            )
        }
    }

    /** A professional regard in 0..1 — the confidence-weighted mean of the good a leader sees, floored at nothing. */
    private fun regardOf(standing: ProfessionalStanding?): Double {
        standing ?: return 0.0
        val confidence = standing.dimensions.values.sumOf { it.confidence }
        if (confidence <= 0.0) return 0.0
        return (standing.dimensions.values.sumOf { it.value * it.confidence } / confidence).coerceIn(0.0, 1.0)
    }

    private fun focusFor(role: RoleKind): ProfessionalDimension = when (role) {
        RoleKind.RECEPTIONIST, RoleKind.CONCIERGE, RoleKind.BARTENDER -> ProfessionalDimension.GUEST_HANDLING
        RoleKind.HOUSEKEEPER, RoleKind.CHEF -> ProfessionalDimension.COMPETENCE
        else -> ProfessionalDimension.JUDGEMENT
    }

    private const val LEADERSHIP_PERIOD = 360L
    private const val DEVELOPMENT_PERIOD = 120L
    private const val RECOGNITION_RELIEF = 0.15
    private const val RECOGNITION_STEP = 0.2
    private const val RECOGNITION_DECAY = 0.98
    private const val RECOGNITION_DRIVE = 0.3
    private const val BASE_DRIVE = 0.4
    private const val DEMONSTRATED_DRIVE = 0.6
    private const val TRAIL = 24
}
