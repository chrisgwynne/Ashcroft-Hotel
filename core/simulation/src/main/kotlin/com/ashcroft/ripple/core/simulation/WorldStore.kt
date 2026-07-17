package com.ashcroft.ripple.core.simulation

import kotlinx.serialization.json.Json

/**
 * Save and restore the whole living hotel. A [WorldState] is the single source of
 * truth a tick transforms, and the engine is a pure, seeded function of it, so a
 * saved snapshot reloads to exactly the same world — and, stepped on, unfolds into
 * exactly the same future. Nothing is lost and nothing drifts: the causal record
 * travels with the state, so a reloaded hotel can still explain its whole past.
 */
object WorldStore {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /** Serialise a world snapshot to a portable string. */
    fun save(state: WorldState): String = json.encodeToString(WorldState.serializer(), state)

    /** Restore a world snapshot saved by [save]. */
    fun load(text: String): WorldState = json.decodeFromString(WorldState.serializer(), text)
}
