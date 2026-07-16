package com.ashcroft.ripple.feature.hotel

import app.cash.turbine.test
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HotelViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModel = HotelViewModel()

    @Test
    fun initialStateReflectsTheAshcroft() =
        runTest {
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals("The Ashcroft", state.hotelName)
                assertEquals(1924, state.establishedYear)
                assertEquals(TimeSpeed.PAUSED, state.timeSpeed)
                assertNull(state.selectedRoom)
                assertTrue(state.floors.size >= 2)
            }
        }

    @Test
    fun selectingAKnownRoomPopulatesTheInfoPanel() =
        runTest {
            viewModel.selectRoom(RoomId("bar"))
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals("The Ashcroft Bar", state.selectedRoom?.name)
                assertEquals("Ground Floor", state.selectedRoom?.floorLabel)
            }
        }

    @Test
    fun changingFloorClearsSelection() =
        runTest {
            viewModel.selectRoom(RoomId("bar"))
            viewModel.focusFloor(1)
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(1, state.focusedLevel)
                assertNull(state.selectedRoom)
            }
        }

    @Test
    fun selectingNullClearsSelection() =
        runTest {
            viewModel.selectRoom(RoomId("bar"))
            viewModel.selectRoom(null)
            viewModel.uiState.test {
                assertNull(awaitItem().selectedRoom)
            }
        }

    @Test
    fun timeSpeedIntentIsRecorded() =
        runTest {
            viewModel.setTimeSpeed(TimeSpeed.FIVE)
            viewModel.uiState.test {
                assertEquals(TimeSpeed.FIVE, awaitItem().timeSpeed)
            }
        }
}
