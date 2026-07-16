package com.ashcroft.ripple.core.model

import kotlinx.serialization.Serializable

/**
 * Stable identifiers used across the simulation. They are deliberately thin
 * value classes so that they cost nothing at runtime yet cannot be confused
 * with one another (a [RoomId] can never be passed where a [PersonId] is
 * expected).
 *
 * Simulation content is intentionally NOT implemented in Phase 1 — these ids
 * exist so that later phases have stable anchors to build on.
 */
@JvmInline
@Serializable
value class PersonId(
    val value: String,
)

@JvmInline
@Serializable
value class RoomId(
    val value: String,
)

@JvmInline
@Serializable
value class RoomTemplateId(
    val value: String,
)

@JvmInline
@Serializable
value class FloorId(
    val value: String,
)

@JvmInline
@Serializable
value class LocationId(
    val value: String,
)
