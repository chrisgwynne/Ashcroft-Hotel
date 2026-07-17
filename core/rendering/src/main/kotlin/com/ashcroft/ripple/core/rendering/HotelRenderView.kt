package com.ashcroft.ripple.core.rendering

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.ashcroft.ripple.core.model.RoomId
import com.ashcroft.ripple.core.model.RoomKind

/**
 * A custom Android view that renders an isometric cut-away of the hotel using
 * the pure [IsoProjection] / [Camera] maths. This is the Phase 1 renderer: a
 * performant Canvas surface proving out pan, pinch-zoom, floor focus and
 * tap-to-select. The maths is deliberately kept in pure classes so a future
 * OpenGL/LibGDX backend can replace this view without touching the model.
 */
class HotelRenderView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        private val projection = IsoProjection()
        private val hitTester = HitTester(projection)

        private var scene: HotelScene = HotelScene(emptyList())
        private var focusedLevel: Int = 0
        private var selectedRoom: RoomId? = null
        private var centred = false

        /** Invoked when a room is tapped (or null when empty space is tapped). */
        var onRoomSelected: ((RoomId?) -> Unit)? = null

        /** Invoked when a person is tapped. */
        var onPersonSelected: ((String?) -> Unit)? = null

        private var people: List<PersonMarker> = emptyList()
        private var selectedPerson: String? = null

        var camera: Camera = Camera()
            private set

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0x33000000
                strokeWidth = 1.5f
            }
        private val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFF3E9D2.toInt()
                textSize = 22f
                textAlign = Paint.Align.CENTER
            }
        private val selectionPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0xFFD9B25A.toInt()
                strokeWidth = 4f
            }
        private val personFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFEADFC4.toInt()
        }
        private val personRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFF2B211A.toInt()
            strokeWidth = 2f
        }
        private val personSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFD9B25A.toInt()
            strokeWidth = 3.5f
        }
        private val personLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2B211A.toInt()
            textSize = 16f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        fun setScene(newScene: HotelScene) {
            scene = newScene
            centred = false
            invalidate()
        }

        fun setFocusedLevel(level: Int) {
            focusedLevel = level
            invalidate()
        }

        fun setSelectedRoom(id: RoomId?) {
            selectedRoom = id
            invalidate()
        }

        fun setPeople(markers: List<PersonMarker>) {
            people = markers
            invalidate()
        }

        fun setSelectedPerson(id: String?) {
            selectedPerson = id
            invalidate()
        }

        private val scaleDetector =
            ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        camera = camera.zoomedBy(detector.scaleFactor, Vec2(detector.focusX, detector.focusY))
                        invalidate()
                        return true
                    }
                },
            )

        private val gestureDetector =
            GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        dx: Float,
                        dy: Float,
                    ): Boolean {
                        camera = camera.pannedBy(-dx, -dy)
                        invalidate()
                        return true
                    }

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        val tap = Vec2(e.x, e.y)
                        val person = hitTester.personAt(tap, camera, people, focusedLevel)
                        if (person != null) {
                            selectedPerson = person
                            onPersonSelected?.invoke(person)
                        } else {
                            val hit = hitTester.roomAt(tap, camera, scene.onLevel(focusedLevel), focusedLevel)
                            selectedRoom = hit
                            selectedPerson = null
                            onRoomSelected?.invoke(hit)
                            onPersonSelected?.invoke(null)
                        }
                        invalidate()
                        return true
                    }
                },
            )

        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            return true
        }

        private fun centreOnHotel() {
            camera = Camera(offset = Vec2(width / 2f, height / 3f), zoom = 1f)
            centred = true
        }

        override fun onDraw(canvas: Canvas) {
            if (!centred && width > 0) centreOnHotel()
            canvas.drawColor(BACKDROP)
            val rooms = scene.onLevel(focusedLevel)
            for (room in rooms) {
                drawRoom(canvas, room)
            }
            drawPeople(canvas)
        }

        private fun drawPeople(canvas: Canvas) {
            for (marker in people) {
                if (marker.level != focusedLevel) continue
                val centre = worldScreen(marker.col + 0.5f, marker.row + 0.5f, marker.level)
                val radius = 9f
                canvas.drawCircle(centre.x, centre.y, radius, personFillPaint)
                canvas.drawCircle(centre.x, centre.y, radius, personRingPaint)
                if (marker.id == selectedPerson) {
                    canvas.drawCircle(centre.x, centre.y, radius + 4f, personSelectedPaint)
                }
                canvas.drawText(marker.label, centre.x, centre.y + 5f, personLabelPaint)
            }
        }

        private fun drawRoom(
            canvas: Canvas,
            room: SceneRoom,
        ) {
            val path = Path()
            val topLeft = worldScreen(room.originCol.toFloat(), room.originRow.toFloat(), room.level)
            val topRight = worldScreen((room.originCol + room.cols).toFloat(), room.originRow.toFloat(), room.level)
            val bottomRight = worldScreen((room.originCol + room.cols).toFloat(), (room.originRow + room.rows).toFloat(), room.level)
            val bottomLeft = worldScreen(room.originCol.toFloat(), (room.originRow + room.rows).toFloat(), room.level)
            path.moveTo(topLeft.x, topLeft.y)
            path.lineTo(topRight.x, topRight.y)
            path.lineTo(bottomRight.x, bottomRight.y)
            path.lineTo(bottomLeft.x, bottomLeft.y)
            path.close()

            fillPaint.color = colorFor(room.kind)
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)
            if (room.id == selectedRoom) canvas.drawPath(path, selectionPaint)

            val centre =
                worldScreen(
                    room.originCol + room.cols / 2f,
                    room.originRow + room.rows / 2f,
                    room.level,
                )
            canvas.drawText(room.label, centre.x, centre.y, labelPaint)
        }

        private fun worldScreen(
            col: Float,
            row: Float,
            level: Int,
        ): Vec2 = camera.worldToScreen(projection.gridToWorld(col, row, level))

        private fun colorFor(kind: RoomKind): Int =
            when (kind) {
                RoomKind.LOBBY -> 0xFF6E5A3C.toInt()
                RoomKind.RECEPTION -> 0xFF7A6540.toInt()
                RoomKind.BAR -> 0xFF3E5641.toInt()
                RoomKind.RESTAURANT -> 0xFF8A5A34.toInt()
                RoomKind.KITCHEN -> 0xFF556065.toInt()
                RoomKind.STAFF_ROOM -> 0xFF5B4E3A.toInt()
                RoomKind.CORRIDOR -> 0xFF4A4234.toInt()
                RoomKind.MANAGER_OFFICE -> 0xFF7A5A46.toInt()
                RoomKind.HOUSEKEEPING_STORE -> 0xFF605544.toInt()
                RoomKind.SUITE -> 0xFF8E6A3A.toInt()
                RoomKind.GUEST_SINGLE, RoomKind.GUEST_DOUBLE, RoomKind.GUEST_TWIN -> 0xFF9A7B4E.toInt()
                RoomKind.STAIRWELL, RoomKind.LIFT -> 0xFF3C3830.toInt()
            }

        private companion object {
            val BACKDROP = Color.argb(255, 20, 18, 16)
        }
    }
