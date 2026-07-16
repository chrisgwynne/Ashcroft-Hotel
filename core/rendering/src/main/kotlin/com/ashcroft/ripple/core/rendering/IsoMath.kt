package com.ashcroft.ripple.core.rendering

/**
 * Pure, allocation-light rendering maths. None of this touches the Android
 * framework, so it is exercised by fast JVM unit tests — satisfying the rule
 * that the game must remain testable without launching graphics, and that
 * rendering state is deterministic and separate from simulation state.
 */
data class Vec2(
    val x: Float,
    val y: Float,
) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)

    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
}

/**
 * Isometric projection of the hotel's logical grid into an abstract "world"
 * plane (before the camera is applied). A cell `(col,row)` on floor `level`
 * projects to a diamond; floors are stacked upward by [floorHeight].
 */
class IsoProjection(
    val tileWidth: Float = 64f,
    val tileHeight: Float = 32f,
    val floorHeight: Float = 96f,
) {
    private val halfW = tileWidth / 2f
    private val halfH = tileHeight / 2f

    /** Grid cell centre -> world point. */
    fun gridToWorld(
        col: Float,
        row: Float,
        level: Int,
    ): Vec2 {
        val x = (col - row) * halfW
        val y = (col + row) * halfH - level * floorHeight
        return Vec2(x, y)
    }

    /** Inverse of [gridToWorld] for a known floor [level]. */
    fun worldToGrid(
        world: Vec2,
        level: Int,
    ): Pair<Float, Float> {
        val a = world.x / halfW
        val b = (world.y + level * floorHeight) / halfH
        val col = (a + b) / 2f
        val row = (b - a) / 2f
        return col to row
    }
}

/**
 * A 2D pan/zoom camera. World points are mapped to screen points by
 * `screen = world * zoom + offset`. Zoom is clamped to a sane band so the
 * hotel can never be lost off-scale.
 */
data class Camera(
    val offset: Vec2 = Vec2(0f, 0f),
    val zoom: Float = 1f,
) {
    fun worldToScreen(world: Vec2): Vec2 = Vec2(world.x * zoom + offset.x, world.y * zoom + offset.y)

    fun screenToWorld(screen: Vec2): Vec2 = Vec2((screen.x - offset.x) / zoom, (screen.y - offset.y) / zoom)

    fun pannedBy(
        dx: Float,
        dy: Float,
    ): Camera = copy(offset = Vec2(offset.x + dx, offset.y + dy))

    /**
     * Zoom by [factor] keeping the world point currently under [focus]
     * (a screen coordinate) fixed, so pinch-zoom feels anchored.
     */
    fun zoomedBy(
        factor: Float,
        focus: Vec2,
    ): Camera {
        val newZoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val worldFocus = screenToWorld(focus)
        val newOffset = Vec2(focus.x - worldFocus.x * newZoom, focus.y - worldFocus.y * newZoom)
        return copy(offset = newOffset, zoom = newZoom)
    }

    companion object {
        const val MIN_ZOOM = 0.4f
        const val MAX_ZOOM = 3.5f
    }
}
