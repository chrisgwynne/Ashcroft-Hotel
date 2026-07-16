package com.ashcroft.ripple.feature.hotel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashcroft.ripple.core.decision.ExplanationBuilder
import com.ashcroft.ripple.core.model.ActionPhase
import com.ashcroft.ripple.core.model.ActionState
import com.ashcroft.ripple.core.model.ActionVerb
import com.ashcroft.ripple.core.model.DecisionRecord
import com.ashcroft.ripple.core.model.Goal
import com.ashcroft.ripple.core.model.GoalTarget
import com.ashcroft.ripple.core.model.GoalType
import com.ashcroft.ripple.core.model.HotelLayout
import com.ashcroft.ripple.core.model.NeedKind
import com.ashcroft.ripple.core.model.Person
import com.ashcroft.ripple.core.model.PersonId
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
 * Drives the living hotel and projects it into a readable, explainable UI. It
 * owns the deterministic [SimulationEngine] and a seeded [WorldState], advances
 * the world under the observer time controls, and — for a selected person —
 * translates their needs, goals, action and recorded decision into plain
 * language, including a full "Why?" account with the alternatives they weighed.
 */
@HiltViewModel
class HotelViewModel
    @Inject
    constructor() : ViewModel() {
        private val layout: HotelLayout = AshcroftLayout.build()
        private val engine = SimulationEngine(layout)

        val scene: HotelScene = HotelScene.from(layout)

        private var world: WorldState = AshcroftScenario.initial()
        private var focusedLevel: Int = 0
        private var timeSpeed: TimeSpeed = TimeSpeed.PAUSED
        private var selectedPersonId: String? = null
        private var selectedRoomId: RoomId? = null
        private var whyOpen: Boolean = false
        private var developerMode: Boolean = false

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
            whyOpen = false
            _uiState.value = project()
        }

        fun selectPerson(id: String?) {
            selectedPersonId = id
            if (id != null) selectedRoomId = null
            whyOpen = false
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

        fun toggleWhy() {
            whyOpen = !whyOpen
            _uiState.value = project()
        }

        fun toggleDeveloperMode() {
            developerMode = !developerMode
            _uiState.value = project()
        }

        private fun personName(id: PersonId): String = world.person(id)?.name ?: "someone"

        private fun roomName(id: RoomId): String = layout.room(id)?.displayName ?: id.value

        private val explanations = ExplanationBuilder(roomName = ::roomName, personName = ::personName)

        private fun project(): HotelUiState {
            val selected = selectedPersonId?.let { id -> world.people.firstOrNull { it.id.value == id } }
            return HotelUiState(
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
                selectedPerson = selected?.let(::personView),
                whyOpen = whyOpen && selected?.lastDecision != null,
                developerMode = developerMode,
                why = if (whyOpen) selected?.lastDecision?.let { whyView(it) } else null,
            )
        }

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
            return SelectedRoom(
                id = room.id,
                name = room.displayName,
                kindLabel = readable(room.kind.name),
                floorLabel = floorName,
                occupants = world.people.filter { it.location.roomId == id }.map { it.name },
            )
        }

        private fun personView(person: Person): PersonView {
            val chosen = person.lastDecision?.let { record ->
                record.consideredActions.firstOrNull { it.candidate == record.chosenAction }
            }
            return PersonView(
                id = person.id.value,
                name = person.name,
                ageAndRole = "${person.identity.age} · ${readableRole(person.role)}",
                mood = moodOf(person),
                currentAction = actionHeadline(person.action),
                actionPhase = phaseLabel(person.action.phase),
                destination = person.action.targetRoom?.takeIf { person.location.isMoving }?.let(::roomName),
                currentGoal = topGoalLabel(person.goals),
                reasonSummary = person.action.reasonSummary,
                topSupport = chosen?.topPositive()?.explanationKey,
                topConflict = chosen?.topNegative()?.explanationKey,
                needs = readouts(person),
            )
        }

        private fun whyView(record: DecisionRecord): WhyView {
            val explanation = explanations.explain(record)
            val chosen = record.consideredActions.firstOrNull { it.candidate == record.chosenAction }
            return WhyView(
                headline = explanation.headline,
                summary = explanation.summary,
                positives = explanation.positives,
                negatives = explanation.negatives,
                alternatives = explanation.alternatives.map { AlternativeView(it.label, it.whyLower) },
                developerLines = chosen?.components?.map { "${it.type}: ${format(it.value)}" } ?: emptyList(),
                stochastic = chosen?.let { "noise ${format(it.stochasticAdjustment)} · total ${format(it.finalScore)}" } ?: "",
            )
        }

        private fun actionHeadline(action: ActionState): String {
            val where = action.targetRoom?.let(::roomName)
            val who = action.targetPerson?.let(::personName)
            return when (action.verb) {
                ActionVerb.WORK -> if (where != null) "Working at $where" else "Working"
                ActionVerb.EAT -> if (where != null) "Eating at $where" else "Eating"
                ActionVerb.SLEEP -> if (where != null) "Resting in $where" else "Resting"
                ActionVerb.WASH -> "Freshening up"
                ActionVerb.RELAX, ActionVerb.RETURN_HOME -> if (where != null) "Relaxing in $where" else "Relaxing"
                ActionVerb.TAKE_BREAK -> "Taking a break"
                ActionVerb.SOCIALISE -> if (where != null) "Sitting in $where" else "Socialising"
                ActionVerb.GREET -> if (who != null) "Greeting $who" else "Greeting someone"
                ActionVerb.CONVERSE -> if (who != null) "Talking with $who" else "Chatting"
                ActionVerb.WANDER -> "Wandering"
                ActionVerb.WAIT -> "Waiting"
            }
        }

        private fun phaseLabel(phase: ActionPhase): String = when (phase) {
            ActionPhase.PROPOSED, ActionPhase.ACCEPTED -> "About to start"
            ActionPhase.TRAVELLING -> "On the way"
            ActionPhase.IN_PROGRESS -> "In progress"
            ActionPhase.COMPLETED -> "Just finished"
            ActionPhase.FAILED -> "Didn't work out"
            ActionPhase.INTERRUPTED -> "Interrupted"
            ActionPhase.ABANDONED -> "Abandoned"
        }

        private fun topGoalLabel(goals: List<Goal>): String? {
            val goal = goals.maxByOrNull { it.priority } ?: return null
            return when (goal.type) {
                GoalType.SATISFY_NEED -> (goal.target as? GoalTarget.Need)?.let { "see to a ${it.kind.name.lowercase()} need" }
                GoalType.FULFIL_WORK -> "do their job well"
                GoalType.GAIN_APPROVAL -> "earn some approval"
                GoalType.COMPLETE_STAY -> "make the most of their stay"
                GoalType.PRESERVE_RELATIONSHIP -> "keep a relationship warm"
                GoalType.AVOID_DISCOMFORT -> "avoid discomfort"
                GoalType.SEEK_PRIVACY -> "find some privacy"
                GoalType.IMPROVE_COMPETENCE -> "get better at something"
                GoalType.SAVE_RESOURCES -> "be careful with money"
            }
        }

        private fun moodOf(person: Person): String {
            val need = person.needs.mostPressing()
            if (person.needs[need] > 0.5f) return "Content"
            return when (need) {
                NeedKind.HUNGER -> "Hungry"
                NeedKind.REST -> "Tired"
                NeedKind.SOCIAL -> "Craving company"
                NeedKind.HYGIENE -> "Wanting to freshen up"
                NeedKind.PRIVACY -> "In need of quiet"
                NeedKind.COMFORT -> "Unsettled"
                NeedKind.SAFETY -> "Uneasy"
                NeedKind.PURPOSE -> "Restless"
                NeedKind.RECOGNITION -> "Under-appreciated"
                NeedKind.AUTONOMY -> "Hemmed in"
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

        private fun format(value: Double): String {
            val rounded = kotlin.math.round(value * 100) / 100.0
            return rounded.toString()
        }

        private fun initials(name: String): String {
            val parts = name.trim().split(' ').filter { it.isNotEmpty() }
            return when {
                parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                parts.isNotEmpty() -> parts.first().take(2).uppercase()
                else -> "?"
            }
        }

        private fun readableRole(role: RoleKind): String = readable(role.name)

        private fun readable(raw: String): String = raw
            .lowercase()
            .split('_')
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

        private companion object {
            const val TICK_MS = 450L
        }
    }
