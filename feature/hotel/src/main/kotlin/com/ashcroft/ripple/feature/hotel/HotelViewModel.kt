package com.ashcroft.ripple.feature.hotel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashcroft.ripple.core.model.ActivityKind
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.RoleKind
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.SimTime
import com.ashcroft.ripple.core.rendering.HotelScene
import com.ashcroft.ripple.core.rendering.PersonMarker
import com.ashcroft.ripple.core.simulation.AshcroftScenario
import com.ashcroft.ripple.core.simulation.SimulationEngine
import com.ashcroft.ripple.core.simulation.WorldState
import com.ashcroft.ripple.core.world.AshcroftLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the living hotel. It owns the deterministic [SimulationEngine] and a
 * seeded [WorldState], advances the world on a cadence set by the observer time
 * controls, and projects the result into a readable [HotelUiState]. Pausing or
 * changing speed only changes how fast time is consumed — never the outcomes,
 * which stay a pure function of seed and elapsed minutes.
 */
@HiltViewModel
class HotelViewModel
    @Inject
    constructor() : ViewModel() {
        private val layout: HotelLayout = AshcroftLayout.build()
        private val engine = SimulationEngine(layout)

        /** Static isometric geometry handed to the renderer. */
        val scene: HotelScene = HotelScene.from(layout)

        private var world: WorldState = AshcroftScenario.initial()
        private var focusedLevel: Int = 0
        private var timeSpeed: TimeSpeed = TimeSpeed.PAUSED
        private var selectedPersonId: String? = null
        private var selectedRoomId: RoomId? = null

        private val _uiState = MutableStateFlow(project())
        val uiState: StateFlow<HotelUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    val steps = timeSpeed.multiplier
                    if (steps > 0) {
                        repeat(steps) { world = engine.step(world) }
                        _uiState.value = project()
                    }
                }
            }
        }

        fun selectRoom(id: RoomId?) {
            selectedRoomId = id
            selectedPersonId = null
            _uiState.value = project()
        }

        fun selectPerson(id: String?) {
            selectedPersonId = id
            if (id != null) selectedRoomId = null
            _uiState.value = project()
        }

        fun focusFloor(level: Int) {
            focusedLevel = level
            selectedRoomId = null
            _uiState.value = project()
        }

        fun setTimeSpeed(speed: TimeSpeed) {
            timeSpeed = speed
            _uiState.value = project()
        }

        private fun project(): HotelUiState = HotelUiState(
            hotelName = layout.name,
            establishedYear = layout.establishedYear,
            clockLabel = clockLabel(world.clock),
            weatherLabel = "Overcast, 11°C",
            occupancyLabel = "${world.people.size} people in the hotel",
            focusedLevel = focusedLevel,
            floors = layout.floors.sortedBy { it.level }.map { FloorOption(it.level, it.displayName) },
            timeSpeed = timeSpeed,
            people = world.people.map(::markerFor),
            selectedRoom = selectedRoomId?.let(::roomView),
            selectedPerson = selectedPersonId?.let { id -> world.people.firstOrNull { it.id.value == id }?.let(::personView) },
        )

        private fun markerFor(person: Person): PersonMarker = PersonMarker(
            id = person.id.value,
            label = initials(person.name),
            level = person.location.pos.floor,
            col = person.location.pos.cell.col.toFloat(),
            row = person.location.pos.cell.row.toFloat(),
            moving = person.location.isMoving,
        )

        private fun roomView(id: RoomId): SelectedRoom? {
            val room = layout.room(id) ?: return null
            val floorName = layout.floors.first { it.id == room.floorId }.displayName
            val occupants = world.people.filter { it.location.roomId == id }.map { it.name }
            return SelectedRoom(
                id = room.id,
                name = room.displayName,
                kindLabel = readableKind(room.kind.name),
                floorLabel = floorName,
                occupants = occupants,
            )
        }

        private fun personView(person: Person): PersonView = PersonView(
            id = person.id.value,
            name = person.name,
            ageAndRole = "${person.identity.age} · ${readableRole(person.role)}",
            mood = moodOf(person),
            activity = activityText(person),
            whereabouts = whereaboutsText(person),
            needs = readouts(person),
        )

        private fun activityText(person: Person): String {
            if (person.location.isMoving) {
                val dest = person.currentActivity.targetRoom?.let { layout.room(it)?.displayName }
                return if (dest != null) "Walking to $dest" else "On the move"
            }
            val here = person.location.roomId?.let { layout.room(it)?.displayName } ?: "the hotel"
            return when (person.currentActivity.kind) {
                ActivityKind.SLEEP -> "Resting in $here"
                ActivityKind.EAT -> "Eating in $here"
                ActivityKind.WORK -> "Working in $here"
                ActivityKind.SOCIALISE -> "Socialising in $here"
                ActivityKind.WASH -> "Freshening up in $here"
                ActivityKind.RELAX -> "Relaxing in $here"
                ActivityKind.TRAVEL -> "On the move"
                ActivityKind.IDLE -> "Pausing in $here"
            }
        }

        private fun whereaboutsText(person: Person): String {
            if (person.location.isMoving) return "On the move"
            val room = person.location.roomId?.let { layout.room(it)?.displayName }
            return room ?: "In the hotel"
        }

        private fun moodOf(person: Person): String {
            val need = person.needs.mostPressing()
            val value = person.needs[need]
            if (value > 0.5f) return "Content"
            return when (need) {
                NeedKind.HUNGER -> "Hungry"
                NeedKind.REST -> "Tired"
                NeedKind.SOCIAL -> "Craving company"
                NeedKind.HYGIENE -> "Wanting to freshen up"
                NeedKind.PRIVACY -> "In need of quiet"
                NeedKind.PURPOSE -> "Restless"
            }
        }

        private fun readouts(person: Person): List<NeedReadout> = listOf(
            NeedKind.HUNGER to "Appetite",
            NeedKind.REST to "Energy",
            NeedKind.SOCIAL to "Sociability",
            NeedKind.HYGIENE to "Freshness",
        ).map { (need, label) ->
            val v = person.needs[need]
            NeedReadout(label = label, note = bucket(v), level = v)
        }

        private fun bucket(v: Float): String = when {
            v > 0.66f -> "Fine"
            v > 0.33f -> "Slipping"
            else -> "Pressing"
        }

        private fun clockLabel(time: SimTime): String {
            val hh = time.hourOfDay.toString().padStart(2, '0')
            val mm = (time.minuteOfDay % SimTime.MINUTES_PER_HOUR).toString().padStart(2, '0')
            return "Day ${time.dayIndex + 1} · $hh:$mm"
        }

        private fun initials(name: String): String {
            val parts = name.trim().split(' ').filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                parts.isNotEmpty() -> parts.first().take(2).uppercase()
                else -> "?"
            }
        }

        private fun readableRole(role: RoleKind): String = readableKind(role.name)

        private fun readableKind(raw: String): String = raw
            .lowercase()
            .split('_')
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

        private companion object {
            const val TICK_MS = 450L
        }
    }
