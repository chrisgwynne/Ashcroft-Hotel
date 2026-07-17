package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.EntityId

/** A day's worth of the hotel's meaningful developments, read from real pulse events. */
data class DaySummary(
    val day: Long,
    val items: List<String>,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

/** A longer reflection over a span of days — who and what changed most. */
data class PeriodReflection(
    val fromDay: Long,
    val toDay: Long,
    val changedMost: String?,
    val newPractices: Int,
    val cultureShifts: Int,
    val returnsWarmed: Int,
    val headlines: List<String>,
) {
    val isEmpty: Boolean get() = headlines.isEmpty()
}

/**
 * Turns a stream of pulse events into readable summaries. Nothing is generated
 * that the events do not support: an uneventful day yields an empty summary, and
 * no category is padded with filler. The point is a calm, honest account of what
 * actually mattered, not a dashboard that must always say something.
 */
object Summaries {
    /** A single day's summary: the most meaningful development of each kind, up to a few lines. */
    fun day(events: List<PulseEvent>, day: Long): DaySummary {
        val ofDay = events.filter { it.at.dayIndex == day }
        if (ofDay.isEmpty()) return DaySummary(day, emptyList())
        val items = ofDay
            .groupBy { it.kind }
            .values
            .map { group -> group.maxBy { it.significance } }
            .sortedByDescending { it.significance }
            .take(MAX_DAY_ITEMS)
            .map { it.headline }
        return DaySummary(day, items)
    }

    /**
     * A reflection over [fromDay]..[toDay]: who changed most (by how often they were
     * at the centre of a development), the season's structural shifts, and a short
     * list of the most significant headlines.
     */
    fun period(events: List<PulseEvent>, fromDay: Long, toDay: Long, nameOf: (EntityId) -> String?): PeriodReflection {
        val span = events.filter { it.at.dayIndex in fromDay..toDay }
        if (span.isEmpty()) return PeriodReflection(fromDay, toDay, null, 0, 0, 0, emptyList())

        val personTally = HashMap<EntityId, Int>()
        for (event in span) {
            for (subject in event.subjects) {
                if (subject.value.startsWith("person:")) personTally.merge(subject, 1, Int::plus)
            }
        }
        val changedMost = personTally.maxByOrNull { it.value }?.key?.let { nameOf(it) }

        return PeriodReflection(
            fromDay = fromDay,
            toDay = toDay,
            changedMost = changedMost,
            newPractices = span.count { it.kind == PulseKind.PRACTICE_ESTABLISHED },
            cultureShifts = span.count { it.kind == PulseKind.DEPARTMENT_CULTURE_SHIFT },
            returnsWarmed = span.count { it.kind == PulseKind.RETURN_LIKELY },
            headlines = span.sortedByDescending { it.significance }.take(MAX_PERIOD_HEADLINES).map { it.headline },
        )
    }

    private const val MAX_DAY_ITEMS = 5
    private const val MAX_PERIOD_HEADLINES = 8
}
