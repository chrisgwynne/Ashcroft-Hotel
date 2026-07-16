package com.ashcroft.ripple.feature.hotel

import androidx.lifecycle.ViewModel
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind
import com.ashcroft.ripple.core.rendering.HotelScene
import com.ashcroft.ripple.core.world.AshcroftLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Holds the observable state for the hotel view. In Phase 1 there is no live
 * simulation: the clock, weather and occupancy are fixed illustrative values,
 * and the time controls record intent without driving anything yet. Room
 * selection and floor focus are fully wired.
 */
@HiltViewModel
class HotelViewModel
    @Inject
    constructor() : ViewModel() {
        private val layout: HotelLayout = AshcroftLayout.build()

        /** Static isometric geometry handed to the renderer. */
        val scene: HotelScene = HotelScene.from(layout)

        private val _uiState = MutableStateFlow(initialState())
        val uiState: StateFlow<HotelUiState> = _uiState.asStateFlow()

        private fun initialState(): HotelUiState =
            HotelUiState(
                hotelName = layout.name,
                establishedYear = layout.establishedYear,
                clockLabel = "Day 1 · 09:30",
                weatherLabel = "Overcast, 11°C",
                occupancyLabel = "${layout.allRooms.count { it.kind.isGuestRoom() }} guest rooms",
                focusedLevel = 0,
                floors =
                    layout.floors
                        .sortedBy { it.level }
                        .map { FloorOption(it.level, it.displayName) },
                timeSpeed = TimeSpeed.PAUSED,
                selectedRoom = null,
            )

        fun selectRoom(id: RoomId?) {
            _uiState.update { state ->
                state.copy(selectedRoom = id?.let { describe(it) })
            }
        }

        fun focusFloor(level: Int) {
            _uiState.update { it.copy(focusedLevel = level, selectedRoom = null) }
        }

        fun setTimeSpeed(speed: TimeSpeed) {
            _uiState.update { it.copy(timeSpeed = speed) }
        }

        private fun describe(id: RoomId): SelectedRoom? {
            val room = layout.room(id) ?: return null
            val floorName = layout.floors.first { it.id == room.floorId }.displayName
            return SelectedRoom(
                id = room.id,
                name = room.displayName,
                kindLabel = room.kind.displayLabel(),
                floorLabel = floorName,
                sizeLabel = "${room.size.cols} × ${room.size.rows} cells",
            )
        }
    }

private fun RoomKind.isGuestRoom(): Boolean =
    when (this) {
        RoomKind.GUEST_SINGLE, RoomKind.GUEST_DOUBLE, RoomKind.GUEST_TWIN, RoomKind.SUITE -> true
        else -> false
    }

private fun RoomKind.displayLabel(): String =
    name
        .lowercase()
        .split('_')
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
