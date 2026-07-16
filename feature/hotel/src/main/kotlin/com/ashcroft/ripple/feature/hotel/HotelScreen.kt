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
 * The default screen of Ripple: a premium isometric cut-away of The Ashcroft
 * with unobtrusive chrome — date/time, weather and occupancy up top, floor
 * focus and observer time controls, and a temporary information panel for the
 * selected room.
 */
@Composable
fun HotelScreen(
    modifier: Modifier = Modifier,
    viewModel: HotelViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        HotelSurface(
            focusedLevel = state.focusedLevel,
            selectedRoomId = state.selectedRoom?.id,
            onRoomSelected = viewModel::selectRoom,
            viewModel = viewModel,
        )

        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            HotelHeader(state)
            FloorSelector(state, viewModel::focusFloor)
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.selectedRoom?.let { RoomInfoPanel(it) }
            TimeControls(state.timeSpeed, viewModel::setTimeSpeed)
        }
    }
}

@Composable
private fun HotelSurface(
    focusedLevel: Int,
    selectedRoomId: com.ashcroft.ripple.core.model.RoomId?,
    onRoomSelected: (com.ashcroft.ripple.core.model.RoomId?) -> Unit,
    viewModel: HotelViewModel,
) {
    val scene = remember { viewModel.scene }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            HotelRenderView(context).apply {
                setScene(scene)
                this.onRoomSelected = onRoomSelected
            }
        },
        update = { view ->
            view.setFocusedLevel(focusedLevel)
            view.setSelectedRoom(selectedRoomId)
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
private fun FloorSelector(
    state: HotelUiState,
    onFocusFloor: (Int) -> Unit,
) {
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
            Text(
                text = "Footprint ${room.sizeLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TimeControls(
    current: TimeSpeed,
    onSetSpeed: (TimeSpeed) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TimeSpeed.entries.forEach { speed ->
            FilledTonalButton(
                onClick = { onSetSpeed(speed) },
                modifier = Modifier.padding(0.dp),
            ) {
                Text(
                    text = speed.label,
                    fontWeight = if (speed == current) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
