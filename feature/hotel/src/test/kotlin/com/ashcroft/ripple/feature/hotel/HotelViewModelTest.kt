package com.ashcroft.ripple.feature.hotel

import app.cash.turbine.test
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HotelViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Constructed inside each test so the Main dispatcher (set by the rule) is
    // already in place for the view model's ticking coroutine.
    private fun newViewModel() = HotelViewModel()

    @Test
    fun initialStateReflectsTheAshcroftAndItsPopulation() = runTest {
        newViewModel().uiState.test {
            val state = awaitItem()
            assertEquals("The Ashcroft", state.hotelName)
            assertEquals(1924, state.establishedYear)
            assertEquals(TimeSpeed.PAUSED, state.timeSpeed)
            assertNull(state.selectedPerson)
            assertNull(state.selectedRoom)
            assertTrue(state.floors.size >= 2)
            assertEquals(12, state.people.size)
        }
    }

    @Test
    fun selectingAPersonPopulatesAReadableProfile() = runTest {
        val vm = newViewModel()
        vm.selectPerson("maya")
        vm.uiState.test {
            val person = awaitItem().selectedPerson
            assertNotNull(person)
            assertEquals("Maya Bennett", person!!.name)
            assertTrue(person.needs.isNotEmpty())
            assertTrue(person.activity.isNotBlank())
        }
    }

    @Test
    fun selectingARoomListsWhoIsThere() = runTest {
        val vm = newViewModel()
        vm.selectRoom(RoomId("staff_room"))
        vm.uiState.test {
            val room = awaitItem().selectedRoom
            assertNotNull(room)
            assertEquals("Staff Break Room", room!!.name)
            assertEquals("Ground Floor", room.floorLabel)
        }
    }

    @Test
    fun selectingAPersonClearsRoomSelection() = runTest {
        val vm = newViewModel()
        vm.selectRoom(RoomId("bar"))
        vm.selectPerson("arthur")
        vm.uiState.test {
            val state = awaitItem()
            assertNull(state.selectedRoom)
            assertNotNull(state.selectedPerson)
        }
    }

    @Test
    fun changingFloorClearsRoomSelection() = runTest {
        val vm = newViewModel()
        vm.selectRoom(RoomId("bar"))
        vm.focusFloor(1)
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.focusedLevel)
            assertNull(state.selectedRoom)
        }
    }

    @Test
    fun timeSpeedIntentIsRecorded() = runTest {
        val vm = newViewModel()
        vm.setTimeSpeed(TimeSpeed.FIVE)
        vm.uiState.test {
            assertEquals(TimeSpeed.FIVE, awaitItem().timeSpeed)
        }
    }
}
