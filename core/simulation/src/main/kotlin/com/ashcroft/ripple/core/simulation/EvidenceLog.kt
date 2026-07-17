package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.CauseId
import com.ashcroft.ripple.core.model.EntityId
import com.ashcroft.ripple.core.model.EvidenceDimension
import com.ashcroft.ripple.core.model.EvidenceId
import com.ashcroft.ripple.core.model.EvidenceLedger
import com.ashcroft.ripple.core.model.HistoricalEvidence
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.ProfessionalDimension
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.StandingDimension

/**
 * A per-tick collector of *evidence* — the recorded basis from which people form
 * observer-specific reputations. Nothing here decides a reputation; it derives
 * evidence from what actually happened (a task served well, a request left waiting,
 * a hand offered) and folds each piece into the standings of the people who
 * witnessed it. Firsthand observers weigh it fully; those who merely shared the
 * room weigh it a little less. Every piece keeps its source cause ids, so any
 * judgement can be traced back to what produced it. Ids are deterministic.
 */
internal class EvidenceLog(private val now: SimTime) {
    private sealed interface Pending {
        val evidence: HistoricalEvidence
    }

    private data class PersonalPending(
        override val evidence: HistoricalEvidence,
        val dimension: StandingDimension,
        val observers: Set<PersonId>,
        val weight: Double,
    ) : Pending

    private data class ProfessionalPending(
        override val evidence: HistoricalEvidence,
        val dimension: ProfessionalDimension,
        val observers: Set<PersonId>,
        val weight: Double,
    ) : Pending

    private data class EntityPending(override val evidence: HistoricalEvidence) : Pending

    private val pending = ArrayList<Pending>()
    private var seq = 0

    val isEmpty: Boolean get() = pending.isEmpty()

    private fun id(): EvidenceId = EvidenceId("e:${now.epochMinutes}:${seq++}")

    /** Evidence one person (or a few witnesses) formed about another's social conduct. */
    fun personal(
        subject: PersonId,
        dimension: StandingDimension,
        direction: Double,
        strength: Double,
        observedBy: PersonId,
        witnesses: Set<PersonId> = emptySet(),
        causes: Set<CauseId> = emptySet(),
        confidence: Double = FIRSTHAND_CONFIDENCE,
    ): EvidenceId {
        val evidence = HistoricalEvidence(
            id = id(),
            subjectId = EntityId.person(subject),
            dimension = EVIDENCE_OF_STANDING.getValue(dimension),
            direction = direction.coerceIn(-1.0, 1.0),
            strength = strength,
            confidence = confidence,
            sourceCauseIds = causes,
            observedBy = observedBy,
            createdAt = now,
        )
        pending += PersonalPending(evidence, dimension, setOf(observedBy) + witnesses, strength)
        return evidence.id
    }

    /** Evidence colleagues/managers formed about someone's professional performance. */
    fun professional(
        subject: PersonId,
        dimension: ProfessionalDimension,
        direction: Double,
        strength: Double,
        observers: Set<PersonId>,
        causes: Set<CauseId> = emptySet(),
        confidence: Double = FIRSTHAND_CONFIDENCE,
    ): EvidenceId {
        val evidence = HistoricalEvidence(
            id = id(),
            subjectId = EntityId.person(subject),
            dimension = EVIDENCE_OF_PROFESSION.getValue(dimension),
            direction = direction.coerceIn(-1.0, 1.0),
            strength = strength,
            confidence = confidence,
            sourceCauseIds = causes,
            observedBy = observers.firstOrNull(),
            createdAt = now,
        )
        pending += ProfessionalPending(evidence, dimension, observers, strength)
        return evidence.id
    }

    /** Evidence about a room, department or the hotel — recorded now, consumed by the culture layers. */
    fun entity(
        subject: EntityId,
        dimension: EvidenceDimension,
        direction: Double,
        strength: Double,
        observedBy: PersonId?,
        causes: Set<CauseId> = emptySet(),
        confidence: Double = FIRSTHAND_CONFIDENCE,
    ): EvidenceId {
        val evidence = HistoricalEvidence(
            id = id(),
            subjectId = subject,
            dimension = dimension,
            direction = direction.coerceIn(-1.0, 1.0),
            strength = strength,
            confidence = confidence,
            sourceCauseIds = causes,
            observedBy = observedBy,
            createdAt = now,
        )
        pending += EntityPending(evidence)
        return evidence.id
    }

    fun foldInto(ledger: EvidenceLedger): EvidenceLedger {
        if (pending.isEmpty()) return ledger
        var next = ledger
        for (p in pending) next = next.add(p.evidence)
        return if (next.size > LEDGER_CAP) next.prunedTo(LEDGER_LOW) else next
    }

    /** Fold each piece of evidence into the standings of the people who witnessed it. */
    fun applyStandings(byId: MutableMap<PersonId, Person>) {
        for (p in pending) {
            when (p) {
                is PersonalPending -> foldPersonal(p, byId)
                is ProfessionalPending -> foldProfessional(p, byId)
                is EntityPending -> Unit
            }
        }
    }

    private fun foldPersonal(p: PersonalPending, byId: MutableMap<PersonId, Person>) {
        val subject = p.evidence.observedByOrSubjectPerson()
        for (observerId in p.observers) {
            if (observerId == subject) continue
            val observer = byId[observerId] ?: continue
            val firsthand = observerId == p.evidence.observedBy
            val weight = p.weight * (if (firsthand) 1.0 else WITNESS_WEIGHT)
            byId[observerId] = observer.copy(
                standings = observer.standings.withPersonal(observerId, subject) {
                    it.observe(p.dimension, p.evidence.direction, weight, p.evidence.id, now, STANDING_DECAY)
                },
            )
        }
    }

    private fun foldProfessional(p: ProfessionalPending, byId: MutableMap<PersonId, Person>) {
        val subject = p.evidence.observedByOrSubjectPerson()
        for (observerId in p.observers) {
            if (observerId == subject) continue
            val observer = byId[observerId] ?: continue
            byId[observerId] = observer.copy(
                standings = observer.standings.withProfessional(observerId, subject) {
                    it.observe(p.dimension, p.evidence.direction, p.weight, p.evidence.id, now, STANDING_DECAY)
                },
            )
        }
    }

    /** Recover the subject PersonId from an entity id of form "person:x". */
    private fun HistoricalEvidence.observedByOrSubjectPerson(): PersonId =
        PersonId(subjectId.value.removePrefix("person:"))

    private companion object {
        const val FIRSTHAND_CONFIDENCE = 0.85
        const val WITNESS_WEIGHT = 0.5
        const val STANDING_DECAY = 0.999
        const val LEDGER_CAP = 4_000
        const val LEDGER_LOW = 3_000

        /** The evidence axis each social standing axis is recorded under (same names). */
        val EVIDENCE_OF_STANDING: Map<StandingDimension, EvidenceDimension> = mapOf(
            StandingDimension.RELIABILITY to EvidenceDimension.RELIABILITY,
            StandingDimension.COMPETENCE to EvidenceDimension.COMPETENCE,
            StandingDimension.WARMTH to EvidenceDimension.WARMTH,
            StandingDimension.DISCRETION to EvidenceDimension.DISCRETION,
            StandingDimension.HONESTY to EvidenceDimension.HONESTY,
            StandingDimension.GENEROSITY to EvidenceDimension.GENEROSITY,
            StandingDimension.FAIRNESS to EvidenceDimension.FAIRNESS,
            StandingDimension.CALMNESS to EvidenceDimension.CALMNESS,
            StandingDimension.RESPONSIVENESS to EvidenceDimension.RESPONSIVENESS,
        )

        /** The nearest evidence axis for each professional axis (the ledger label is coarse; the fold is precise). */
        val EVIDENCE_OF_PROFESSION: Map<ProfessionalDimension, EvidenceDimension> = mapOf(
            ProfessionalDimension.RELIABILITY to EvidenceDimension.RELIABILITY,
            ProfessionalDimension.COMPETENCE to EvidenceDimension.COMPETENCE,
            ProfessionalDimension.INITIATIVE to EvidenceDimension.INNOVATION,
            ProfessionalDimension.JUDGEMENT to EvidenceDimension.COMPETENCE,
            ProfessionalDimension.TEAMWORK to EvidenceDimension.WARMTH,
            ProfessionalDimension.LEADERSHIP to EvidenceDimension.PRESTIGE,
            ProfessionalDimension.GUEST_HANDLING to EvidenceDimension.RESPONSIVENESS,
            ProfessionalDimension.DISCRETION to EvidenceDimension.DISCRETION,
            ProfessionalDimension.PUNCTUALITY to EvidenceDimension.RELIABILITY,
            ProfessionalDimension.RESILIENCE to EvidenceDimension.CONSISTENCY,
        )
    }
}
