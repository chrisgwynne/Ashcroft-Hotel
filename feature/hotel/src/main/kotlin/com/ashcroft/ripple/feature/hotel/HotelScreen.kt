package com.ashcroft.ripple.feature.hotel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashcroft.ripple.core.rendering.HotelRenderView

/**
 * The default screen of Ripple: a premium isometric cut-away of The Ashcroft,
 * now alive with people moving between rooms. Unobtrusive chrome shows the
 * clock, weather and occupancy; floor focus and observer time controls sit at
 * the edges; tapping a person or room opens a readable information panel.
 */
@Composable
fun HotelScreen(
    modifier: Modifier = Modifier,
    viewModel: HotelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        HotelSurface(state = state, viewModel = viewModel)

        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            HotelHeader(state)
            FloorSelector(state, viewModel::focusFloor)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.whyOpen && state.why != null) {
                WhyPanel(
                    why = state.why,
                    developerMode = state.developerMode,
                    onToggleDeveloperMode = viewModel::toggleDeveloperMode,
                    onClose = viewModel::toggleWhy,
                )
            }
            state.selectedPerson?.let { PersonPanel(it, onWhy = viewModel::toggleWhy) }
            if (state.selectedPerson == null) {
                state.selectedRoom?.let { RoomInfoPanel(it) }
            }
            TimeControls(state.timeSpeed, viewModel::setTimeSpeed)
        }
    }
}

@Composable
private fun HotelSurface(state: HotelUiState, viewModel: HotelViewModel) {
    val scene = remember { viewModel.scene }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            HotelRenderView(context).apply {
                setScene(scene)
                onRoomSelected = { viewModel.selectRoom(it) }
                onPersonSelected = { viewModel.selectPerson(it) }
            }
        },
        update = { view ->
            view.setFocusedLevel(state.focusedLevel)
            view.setPeople(state.people)
            view.setSelectedRoom(state.selectedRoom?.id)
            view.setSelectedPerson(state.selectedPerson?.id)
        },
    )
}

@Composable
private fun HotelHeader(state: HotelUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = "${state.hotelName}  ·  est. ${state.establishedYear}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${state.clockLabel}   •   ${state.weatherLabel}   •   ${state.occupancyLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FloorSelector(state: HotelUiState, onFocusFloor: (Int) -> Unit) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.floors.forEach { floor ->
            FilterChip(
                selected = floor.level == state.focusedLevel,
                onClick = { onFocusFloor(floor.level) },
                label = { Text(floor.label) },
            )
        }
    }
}

@Composable
private fun PersonPanel(person: PersonView, onWhy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = person.ageAndRole,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val doing = buildString {
                append(person.currentAction)
                append("  ·  ")
                append(person.actionPhase)
                person.destination?.let { append("  →  $it") }
            }
            Text(
                text = "${person.mood}  ·  $doing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            person.currentGoal?.let { goal ->
                Text(
                    text = "Trying to $goal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = person.reasonSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
            person.topSupport?.let { Hint("Draws them: $it", top = 4) }
            person.topConflict?.let { Hint("Pulls against it: $it", top = 2) }
            NeedsRow(person.needs)
            FilledTonalButton(
                onClick = onWhy,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text("Why?")
            }
        }
    }
}

@Composable
private fun WhyPanel(
    why: WhyView,
    developerMode: Boolean,
    onToggleDeveloperMode: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = why.headline,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = why.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            BulletSection("What drew them in", why.positives)
            BulletSection("What weighed against it", why.negatives)
            BulletSection(
                "What they might have done instead",
                why.alternatives.map { "${it.label} — ${it.whyLower}" },
            )
            if (developerMode) DeveloperDetail(why)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onToggleDeveloperMode) {
                    Text(if (developerMode) "Hide detail" else "Developer detail")
                }
                FilledTonalButton(onClick = onClose) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, top: Int) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top.dp),
    )
}

@Composable
private fun NeedsRow(needs: List<NeedReadout>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        needs.forEach { need ->
            Text(
                text = "${need.label}: ${need.note}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BulletSection(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    lines.forEach { line ->
        Text(
            text = "•  $line",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DeveloperDetail(why: WhyView) {
    Text(
        text = "Score components",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp),
    )
    why.developerLines.forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = why.stochastic,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun RoomInfoPanel(room: SelectedRoom) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = room.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${room.kindLabel}  ·  ${room.floorLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val occupancy = if (room.occupants.isEmpty()) {
                "Empty right now"
            } else {
                "Here now: ${room.occupants.joinToString(", ")}"
            }
            Text(
                text = occupancy,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TimeControls(current: TimeSpeed, onSetSpeed: (TimeSpeed) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeSpeed.entries.forEach { speed ->
            FilledTonalButton(onClick = { onSetSpeed(speed) }) {
                Text(
                    text = speed.label,
                    fontWeight = if (speed == current) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
