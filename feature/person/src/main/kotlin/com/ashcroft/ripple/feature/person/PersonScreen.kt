package com.ashcroft.ripple.feature.person

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder for the person feature.
 *
 * Phase 2 — the person profile: portrait, role, current activity,
 * relationships, goals, recent memories and the "Why are they doing this?" view.
 *
 * The module and its route exist now so navigation and boundaries are in
 * place; the screen is intentionally minimal in Phase 1.
 */
@Composable
fun PersonScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Person — coming in a later phase",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(24.dp),
        )
    }
}
