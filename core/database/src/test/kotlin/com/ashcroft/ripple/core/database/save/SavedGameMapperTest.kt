package com.ashcroft.ripple.core.database.save

import com.ashcroft.ripple.core.model.SimTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedGameMapperTest {
    @Test
    fun domainEntityRoundTripIsLossless() {
        val original =
            SavedGame(
                id = "slot-1",
                name = "The Ashcroft — Autumn",
                seed = 4815162342L,
                simTime = SimTime(1440L),
                createdAtEpoch = 1000L,
                updatedAtEpoch = 2000L,
            )
        val restored = original.toEntity().toDomain()
        assertEquals(original, restored)
    }
}
