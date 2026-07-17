package com.ashcroft.ripple.core.database.save

import com.ashcroft.ripple.core.model.SimTime
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldSnapshotMapperTest {
    @Test
    fun domainEntityRoundTripIsLossless() {
        val original =
            WorldSnapshot(
                saveId = "slot-1",
                simTime = SimTime(525_600L),
                causeNodeCount = 4_211,
                payload = """{"seed":1924,"clock":{"epochMinutes":525600}}""",
            )
        val restored = original.toEntity().toDomain()
        assertEquals(original, restored)
    }
}
