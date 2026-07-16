package com.ashcroft.ripple.feature.hotel

import com.ashcroft.ripple.core.model.RoomId

/** The set of playback speeds offered by the observer time controls. */
enum class TimeSpeed(
    val label: String,
    val multiplier: Int,
) {
    PAUSED("II", 0),
    ONE("1×", 1),
    TWO("2×", 2),
    FIVE("5×", 5),
    TWENTY("20×", 20),
}

/** Details of the currently selected room, shown in the information panel. */
data class SelectedRoom(
    val id: RoomId,
    val name: String,
    val kindLabel: String,
    val floorLabel: String,
    val sizeLabel: String,
)

/**
 * Everything the hotel screen needs to render its chrome. The isometric scene
 * geometry is static in Phase 1 and supplied separately by the view model;
 * this state carries the observable, changing pieces.
 */
data class HotelUiState(
    val hotelName: String,
    val establishedYear: Int,
    val clockLabel: String,
    val weatherLabel: String,
    val occupancyLabel: String,
    val focusedLevel: Int,
    val floors: List<FloorOption>,
    val timeSpeed: TimeSpeed,
    val selectedRoom: SelectedRoom?,
)

data class FloorOption(
    val level: Int,
    val label: String,
)
