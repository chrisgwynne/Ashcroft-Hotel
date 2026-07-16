package com.ashcroft.ripple.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RippleDestinationsTest {
    @Test
    fun destinationRoutesAreUnique() {
        val routes =
            listOf(
                RippleDestinations.HOTEL,
                RippleDestinations.PERSON,
                RippleDestinations.TIMELINE,
                RippleDestinations.HISTORY,
                RippleDestinations.RELATIONSHIPS,
                RippleDestinations.SETTINGS,
            )
        assertEquals(routes.size, routes.toSet().size)
    }

    @Test
    fun hotelIsTheDefaultDestination() {
        assertEquals("hotel", RippleDestinations.HOTEL)
    }
}
