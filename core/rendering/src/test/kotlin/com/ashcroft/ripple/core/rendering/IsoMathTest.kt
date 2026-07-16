package com.ashcroft.ripple.core.rendering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoMathTest {
    private val projection = IsoProjection()

    @Test
    fun projectionRoundTripsForEachFloor() {
        for (level in 0..3) {
            val world = projection.gridToWorld(5f, 7f, level)
            val (col, row) = projection.worldToGrid(world, level)
            assertEquals(5f, col, 1e-3f)
            assertEquals(7f, row, 1e-3f)
        }
    }

    @Test
    fun cameraRoundTripsScreenAndWorld() {
        val camera = Camera(offset = Vec2(100f, 50f), zoom = 1.5f)
        val world = Vec2(12f, -8f)
        val screen = camera.worldToScreen(world)
        val back = camera.screenToWorld(screen)
        assertEquals(world.x, back.x, 1e-3f)
        assertEquals(world.y, back.y, 1e-3f)
    }

    @Test
    fun zoomIsClampedToBand() {
        var camera = Camera()
        repeat(20) { camera = camera.zoomedBy(2f, Vec2(0f, 0f)) }
        assertTrue(camera.zoom <= Camera.MAX_ZOOM + 1e-3f)
        repeat(40) { camera = camera.zoomedBy(0.5f, Vec2(0f, 0f)) }
        assertTrue(camera.zoom >= Camera.MIN_ZOOM - 1e-3f)
    }

    @Test
    fun zoomKeepsFocusPointAnchored() {
        val camera = Camera(offset = Vec2(200f, 200f), zoom = 1f)
        val focus = Vec2(150f, 130f)
        val zoomed = camera.zoomedBy(1.7f, focus)
        // The world point under the focus must be unchanged after zooming.
        val before = camera.screenToWorld(focus)
        val after = zoomed.screenToWorld(focus)
        assertEquals(before.x, after.x, 1e-2f)
        assertEquals(before.y, after.y, 1e-2f)
    }
}
