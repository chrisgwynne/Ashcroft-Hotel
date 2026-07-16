package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.EmotionKind
import com.ashcroft.ripple.core.model.GoalType
import com.ashcroft.ripple.core.model.Memory
import com.ashcroft.ripple.core.model.MemoryKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.model.TraitKind
import kotlin.math.abs
import kotlin.math.min

/**
 * Deterministic memory recall. A memory is brought to mind only when the present
 * moment is relevant to it — the room it happened in, a person who was involved,
 * a similar action or feeling, related news, an anniversary, or a goal it bears
 * on. How readily it surfaces then depends on how important, vivid, confident,
 * recent and often-recalled it is, shaded by temperament.
 *
 * There is no randomness and no irrelevant recall: a memory with no connection to
 * the current context is never surfaced, and the highest-scoring relevant memory
 * wins only if it clears a threshold. Recall never rewrites objective truth — it
 * only colours the rememberer's feelings and, through them, their choices.
 */
internal object MemoryRecall {
    data class Context(
        val room: RoomId?,
        val presentPeople: Set<PersonId>,
        val currentVerb: ActionVerb?,
        val strongestEmotion: EmotionKind?,
        val goalSubjects: Set<PersonId>,
        val goalTypes: Set<GoalType>,
        val rumourSubjects: Set<PersonId>,
    )

    data class Result(val recalled: Memory?, val emotionDeltas: Map<EmotionKind, Double>)

    private const val LOCATION_CUE = 1.0
    private const val PERSON_CUE = 1.2
    private const val ACTION_CUE = 0.6
    private const val EMOTION_CUE = 0.7
    private const val INFO_CUE = 1.0
    private const val ANNIVERSARY_CUE = 0.9
    private const val GOAL_CUE = 0.8
    private const val RECALL_THRESHOLD = 1.1
    private const val DAY = 1440L
    private const val ANNIVERSARY_WINDOW = 90L
    private const val STIR = 0.4

    fun recall(person: Person, ctx: Context, now: SimTime): Result {
        val best = person.memories
            .mapNotNull { memory ->
                val relevance = relevance(memory, ctx, now)
                if (relevance <= 0.0) null else Triple(memory, relevance * strength(person, memory, now), memory.id.value)
            }
            .filter { it.second >= RECALL_THRESHOLD }
            // Deterministic: strongest recall wins, ties broken by stable memory id.
            .maxWithOrNull(compareBy({ it.second }, { it.third }))
            ?.first
            ?: return Result(null, emptyMap())
        return Result(best, emotionalEcho(best))
    }

    /** How connected a memory is to the present moment; 0 means "no reason to think of this". */
    private fun relevance(memory: Memory, ctx: Context, now: SimTime): Double {
        var r = 0.0
        if (memory.placeId != null && memory.placeId == ctx.room) r += LOCATION_CUE
        if (memory.subjectId != null && memory.subjectId in ctx.presentPeople) r += PERSON_CUE
        if (ctx.currentVerb != null && sharesActionFlavour(memory.kind, ctx.currentVerb)) r += ACTION_CUE
        if (ctx.strongestEmotion != null && sharesEmotionalTone(memory, ctx.strongestEmotion)) r += EMOTION_CUE
        if (memory.subjectId != null && memory.subjectId in ctx.rumourSubjects) r += INFO_CUE
        if (isAnniversary(memory, now)) r += ANNIVERSARY_CUE
        if (memory.subjectId != null && memory.subjectId in ctx.goalSubjects) r += GOAL_CUE
        if (sharesGoalFlavour(memory.kind, ctx.goalTypes)) r += GOAL_CUE
        return r
    }

    /** How easily a memory rises, independent of the cue: vivid, important, fresh, oft-recalled ones surface first. */
    private fun strength(person: Person, memory: Memory, now: SimTime): Double {
        val vividness = abs(memory.valence)
        val openness = person.personality[TraitKind.OPENNESS].toDouble()
        val dwellsOnBad = if (memory.valence < 0) (1.0 - person.personality[TraitKind.PATIENCE]) * 0.5 else 0.0
        val recallEcho = min(memory.recallCount, 5) * 0.05
        return memory.salience(now) * (1.0 + vividness) * (0.7 + openness * 0.6 + dwellsOnBad) + recallEcho
    }

    private fun emotionalEcho(memory: Memory): Map<EmotionKind, Double> {
        val intensity = abs(memory.valence) * memory.importance * STIR
        return if (memory.valence >= 0) {
            mapOf(EmotionKind.HAPPINESS to intensity)
        } else {
            when (memory.kind) {
                MemoryKind.WAS_EMBARRASSED -> mapOf(EmotionKind.EMBARRASSMENT to intensity, EmotionKind.ANXIETY to intensity * 0.5)
                MemoryKind.WAS_IGNORED, MemoryKind.WAS_INTERRUPTED -> mapOf(
                    EmotionKind.FRUSTRATION to intensity,
                    EmotionKind.ANXIETY to intensity * 0.5,
                )
                else -> mapOf(EmotionKind.ANXIETY to intensity)
            }
        }
    }

    private fun sharesActionFlavour(kind: MemoryKind, verb: ActionVerb): Boolean {
        val social = kind in SOCIAL_MEMORIES
        return social && verb.social
    }

    private fun sharesEmotionalTone(memory: Memory, emotion: EmotionKind): Boolean {
        val pleasant = emotion == EmotionKind.HAPPINESS || emotion == EmotionKind.EXCITEMENT || emotion == EmotionKind.CONFIDENCE
        return (memory.valence >= 0) == pleasant
    }

    private fun sharesGoalFlavour(kind: MemoryKind, goals: Set<GoalType>): Boolean = when {
        GoalType.GAIN_APPROVAL in goals -> kind == MemoryKind.WAS_PRAISED || kind == MemoryKind.WAS_THANKED
        GoalType.FULFIL_WORK in goals -> kind == MemoryKind.WORKED_WELL
        else -> false
    }

    private fun isAnniversary(memory: Memory, now: SimTime): Boolean {
        val age = now.epochMinutes - memory.occurredAt.epochMinutes
        if (age < DAY) return false
        val intoDay = age % DAY
        return intoDay < ANNIVERSARY_WINDOW && memory.importance > 0.5
    }

    private val SOCIAL_MEMORIES = setOf(
        MemoryKind.HAD_PLEASANT_CHAT,
        MemoryKind.WAS_IGNORED,
        MemoryKind.WAS_INTERRUPTED,
        MemoryKind.WAS_PRAISED,
        MemoryKind.WAS_EMBARRASSED,
        MemoryKind.WAS_THANKED,
        MemoryKind.SHARED_NEWS,
    )
}
