package com.ashcroft.ripple.core.rendering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonHitTestTest {
    private val projection = IsoProjection()
    private val hitTester = HitTester(projection)
    private val camera = Camera(offset = Vec2(400f, 300f), zoom = 1f)

    private val people = listOf(
        PersonMarker("maya", "MB", level = 0, col = 4f, row = 3f, moving = false),
        PersonMarker("theo", "TW", level = 0, col = 9f, row = 0f, moving = true),
        PersonMarker("guest", "GA", level = 1, col = 0f, row = 3f, moving = false),
    )

    @Test
    fun tapOnAMarkerSelectsThatPerson() {
        val target = people[0]
        val screen = camera.worldToScreen(projection.gridToWorld(target.col + 0.5f, target.row + 0.5f, target.level))
        assertEquals("maya", hitTester.personAt(screen, camera, people, level = 0))
    }

    @Test
    fun personHitTestRespectsTheFocusedFloor() {
        val onFloor1 = people[2]
        val screen = camera.worldToScreen(projection.gridToWorld(onFloor1.col + 0.5f, onFloor1.row + 0.5f, onFloor1.level))
        assertNull("floor-1 person must be ignored while floor 0 is focused", hitTester.personAt(screen, camera, people, level = 0))
        assertEquals("guest", hitTester.personAt(screen, camera, people, level = 1))
    }

    @Test
    fun tapInEmptySpaceSelectsNoOne() {
        assertNull(hitTester.personAt(Vec2(-5000f, -5000f), camera, people, level = 0))
    }
}
