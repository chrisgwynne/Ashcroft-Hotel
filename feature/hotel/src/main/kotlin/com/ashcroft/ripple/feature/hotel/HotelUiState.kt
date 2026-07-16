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

data class NeedReadout(val label: String, val note: String, val level: Float)

/**
 * A readable, observation-level view of a selected person: what they are doing,
 * why, and how they feel — never a raw stat dump.
 */
data class PersonView(
    val id: String,
    val name: String,
    val ageAndRole: String,
    val mood: String,
    val currentAction: String,
    val actionPhase: String,
    val destination: String?,
    val currentGoal: String?,
    val reasonSummary: String,
    val topSupport: String?,
    val topConflict: String?,
    val needs: List<NeedReadout>,
)

/** A plausible alternative the person weighed, and why it lost. */
data class AlternativeView(val label: String, val whyLower: String)

/** The full "Why?" account for the selected person's current action. */
data class WhyView(
    val headline: String,
    val summary: String,
    val positives: List<String>,
    val negatives: List<String>,
    val alternatives: List<AlternativeView>,
    val developerLines: List<String>,
    val stochastic: String,
)

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
    val whyOpen: Boolean,
    val developerMode: Boolean,
    val why: WhyView?,
)

data class FloorOption(val level: Int, val label: String)
