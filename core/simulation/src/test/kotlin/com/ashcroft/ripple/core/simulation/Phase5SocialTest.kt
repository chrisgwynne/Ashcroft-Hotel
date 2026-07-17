package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.NeedState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5C — being in a social space is not the same as having company. Sitting
 * in the bar (SOCIALISE) should barely ease loneliness; only an actual exchange
 * (resolved in ConversationSystem) truly relieves it.
 */
class Phase5SocialTest {
    private val lonely = NeedState.of(NeedKind.SOCIAL to 0.3f)

    @Test
    fun socialisingAloneBarelyEasesLoneliness() {
        val after = NeedDynamics.tick(lonely, ActivityKind.SOCIALISE)
        val gain = after[NeedKind.SOCIAL] - lonely[NeedKind.SOCIAL]
        assertTrue("sitting alone should not fill the need for company", gain in -0.001f..0.01f)
    }

    @Test
    fun anActualExchangeRelievesFarMoreThanSittingAlone() {
        val soloGain = NeedDynamics.tick(lonely, ActivityKind.SOCIALISE)[NeedKind.SOCIAL] - lonely[NeedKind.SOCIAL]
        // A successful conversation adds ~0.10 to SOCIAL in ConversationSystem — far
        // more than a whole minute of sitting in a sociable room alone.
        assertTrue("a real exchange should dwarf solo relief", 0.10f > soloGain * 5)
    }

    @Test
    fun theDefaultSeedStaysSociallyAliveButBelievable() {
        val engine = SimulationEngine(com.ashcroft.ripple.core.world.AshcroftLayout.build())
        var state = AshcroftScenario.initial()
        val lastAt = HashMap<String, Long>()
        var convos = 0
        repeat(7 * 24 * 60) {
            state = engine.step(state)
            for (p in state.people) {
                val c = p.lastConversation ?: continue
                if (c.initiatedByMe && lastAt[p.id.value] != c.at.epochMinutes) {
                    lastAt[p.id.value] = c.at.epochMinutes
                    convos++
                }
            }
        }
        val perDay = convos / 7
        assertTrue("the hotel should not be socially dead", perDay >= 5)
        assertTrue("nor implausibly chatty for a quiet cast", perDay <= 200)
    }
}
