package com.ashcroft.ripple.core.database.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "ripple_settings")

/** User-facing observer preferences, persisted with DataStore. */
data class RippleSettings(
    val timeSpeedIndex: Int = 1,
    val developerModeEnabled: Boolean = false,
)

@Singleton
class SettingsDataStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val settings: Flow<RippleSettings> =
            context.settingsStore.data.map { prefs ->
                RippleSettings(
                    timeSpeedIndex = prefs[TIME_SPEED] ?: 1,
                    developerModeEnabled = prefs[DEV_MODE] ?: false,
                )
            }

        suspend fun setTimeSpeedIndex(index: Int) {
            context.settingsStore.edit { it[TIME_SPEED] = index }
        }

        suspend fun setDeveloperMode(enabled: Boolean) {
            context.settingsStore.edit { it[DEV_MODE] = enabled }
        }

        private companion object {
            val TIME_SPEED = intPreferencesKey("time_speed_index")
            val DEV_MODE = booleanPreferencesKey("developer_mode")
        }
    }
