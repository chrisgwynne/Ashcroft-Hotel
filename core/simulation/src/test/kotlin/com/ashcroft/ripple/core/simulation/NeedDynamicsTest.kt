package com.ashcroft.ripple.core.simulation

import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.NeedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedDynamicsTest {
    @Test
    fun needsDecayWhileIdle() {
        var needs = NeedState.full()
        repeat(60) { needs = NeedDynamics.tick(needs, ActivityKind.IDLE) }
        assertTrue("hunger should fall over an idle hour", needs[NeedKind.HUNGER] < 1f)
        assertTrue(needs[NeedKind.REST] < 1f)
    }

    @Test
    fun eatingRestoresHunger() {
        var needs = NeedState.of(NeedKind.HUNGER to 0.2f)
        repeat(30) { needs = NeedDynamics.tick(needs, ActivityKind.EAT) }
        assertTrue("30 minutes of eating should meaningfully restore hunger", needs[NeedKind.HUNGER] > 0.6f)
    }

    @Test
    fun needsStayClampedToUnitRange() {
        var needs = NeedState.of(NeedKind.HYGIENE to 0.99f)
        repeat(120) { needs = NeedDynamics.tick(needs, ActivityKind.WASH) }
        assertTrue(needs[NeedKind.HYGIENE] <= 1f)
        var empty = NeedState.of(NeedKind.HUNGER to 0.01f)
        repeat(600) { empty = NeedDynamics.tick(empty, ActivityKind.IDLE) }
        assertTrue(empty[NeedKind.HUNGER] >= 0f)
    }

    @Test
    fun tickIsPureAndDeterministic() {
        val start = NeedState.full()
        assertEquals(NeedDynamics.tick(start, ActivityKind.SLEEP), NeedDynamics.tick(start, ActivityKind.SLEEP))
    }
}
