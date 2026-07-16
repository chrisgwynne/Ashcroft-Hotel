package com.ashcroft.ripple.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimTimeTest {
    @Test
    fun hourAndDayDerivedCorrectly() {
        val t = SimTime.START + (SimTime.MINUTES_PER_DAY + 9 * SimTime.MINUTES_PER_HOUR + 30).toLong()
        assertEquals(1, t.dayIndex)
        assertEquals(9, t.hourOfDay)
        assertEquals(9 * 60 + 30, t.minuteOfDay)
    }

    @Test
    fun timeIsMonotonicUnderAddition() {
        val a = SimTime.START
        val b = a + 1
        assertTrue(b > a)
    }
}
