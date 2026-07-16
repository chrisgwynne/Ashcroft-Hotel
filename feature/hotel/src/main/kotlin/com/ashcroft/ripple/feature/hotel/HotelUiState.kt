package com.ashcroft.ripple.feature.hotel

import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.rendering.PersonMarker

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
    val occupants: List<String>,
)

/** A readable, observation-level view of a selected person (no raw stat dump). */
data class PersonView(
    val id: String,
    val name: String,
    val ageAndRole: String,
    val mood: String,
    val activity: String,
    val whereabouts: String,
    val needs: List<NeedReadout>,
)

data class NeedReadout(val label: String, val note: String, val level: Float)

/**
 * Everything the hotel screen needs to render its chrome and the living scene.
 * The isometric room geometry is static (supplied separately); [people] and
 * [selectedPerson] change every simulated minute.
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
    val people: List<PersonMarker>,
    val selectedRoom: SelectedRoom?,
    val selectedPerson: PersonView?,
)

data class FloorOption(val level: Int, val label: String)
