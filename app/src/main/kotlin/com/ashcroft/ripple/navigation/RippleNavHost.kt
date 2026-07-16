package com.ashcroft.ripple.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ashcroft.ripple.feature.history.HistoryScreen
import com.ashcroft.ripple.feature.hotel.HotelScreen
import com.ashcroft.ripple.feature.person.PersonScreen
import com.ashcroft.ripple.feature.relationships.RelationshipsScreen
import com.ashcroft.ripple.feature.settings.SettingsScreen
import com.ashcroft.ripple.feature.timeline.TimelineScreen

/** Top-level navigation destinations. Hotel is the default screen. */
object RippleDestinations {
    const val HOTEL = "hotel"
    const val PERSON = "person"
    const val TIMELINE = "timeline"
    const val HISTORY = "history"
    const val RELATIONSHIPS = "relationships"
    const val SETTINGS = "settings"
}

@Composable
fun RippleNavHost() {
    val navController = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = RippleDestinations.HOTEL) {
            composable(RippleDestinations.HOTEL) { HotelScreen() }
            composable(RippleDestinations.PERSON) { PersonScreen() }
            composable(RippleDestinations.TIMELINE) { TimelineScreen() }
            composable(RippleDestinations.HISTORY) { HistoryScreen() }
            composable(RippleDestinations.RELATIONSHIPS) { RelationshipsScreen() }
            composable(RippleDestinations.SETTINGS) { SettingsScreen() }
        }
    }
}
