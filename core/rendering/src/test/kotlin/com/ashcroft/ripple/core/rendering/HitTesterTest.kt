package com.ashcroft.ripple.core.rendering

import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind
import com.ashcroft.ripple.core.world.AshcroftLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HitTesterTest {
    private val projection = IsoProjection()
    private val hitTester = HitTester(projection)
    private val scene = HotelScene.from(AshcroftLayout.build())

    @Test
    fun tapOnRoomCentreSelectsThatRoom() {
        val camera = Camera(offset = Vec2(500f, 400f), zoom = 1f)
        val target = scene.onLevel(0).first { it.kind == RoomKind.BAR }
        val centreWorld =
            projection.gridToWorld(
                target.originCol + target.cols / 2f,
                target.originRow + target.rows / 2f,
                target.level,
            )
        val screen = camera.worldToScreen(centreWorld)
        val hit = hitTester.roomAt(screen, camera, scene.onLevel(0), 0)
        assertEquals(target.id, hit)
    }

    @Test
    fun tapFarAwaySelectsNothing() {
        val camera = Camera(offset = Vec2(500f, 400f), zoom = 1f)
        val hit = hitTester.roomAt(Vec2(-9000f, -9000f), camera, scene.onLevel(0), 0)
        assertNull(hit)
    }

    @Test
    fun hitTestOnlyConsidersFocusedFloor() {
        val camera = Camera(offset = Vec2(500f, 400f), zoom = 1f)
        val guest = scene.onLevel(1).first { it.id == RoomId("room_101") }
        val centreWorld =
            projection.gridToWorld(
                guest.originCol + guest.cols / 2f,
                guest.originRow + guest.rows / 2f,
                guest.level,
            )
        val screen = camera.worldToScreen(centreWorld)
        // Focused on floor 0, so a floor-1 room must not be selected.
        assertNull(hitTester.roomAt(screen, camera, scene.onLevel(0), 0))
        // Focused on floor 1, it is found.
        assertEquals(guest.id, hitTester.roomAt(screen, camera, scene.onLevel(1), 1))
    }
}
