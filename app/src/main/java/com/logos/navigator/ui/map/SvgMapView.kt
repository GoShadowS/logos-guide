package com.logos.navigator.ui.map

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PaintFlagsDrawFilter
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.caverock.androidsvg.SVG
import com.logos.navigator.data.model.AppSettings
import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.LocationMode
import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.NavEdge
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.Room
import com.logos.navigator.data.model.SvgPoint
import com.logos.navigator.data.model.RouteLineStyle
import com.logos.navigator.data.model.RoomLabelMode
import com.logos.navigator.data.model.SvgRenderQuality
import com.logos.navigator.data.model.ThemeMode
import com.logos.navigator.R
import com.logos.navigator.svg.SvgCoordinateConverter
import com.logos.navigator.svg.SvgGeometryExtractor
import com.logos.navigator.utils.localizedName
import com.logos.navigator.svg.isPointInPolygon
import com.logos.navigator.svg.polygonBounds
import com.logos.navigator.svg.SvgRenderer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Векторная карта этажа на Android Canvas. SVG остаётся вектором при любом масштабе;
 * маркеры, граф, точка пользователя и A* маршрут рисуются поверх него.
 */
class SvgMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var svg: SVG? = null
    private var floor: FloorPlan? = null
    private var rooms: List<Room> = emptyList()
    private var roomGeometries: List<RoomGeometry> = emptyList()
    private var selectedRoom: Room? = null
    private var hoveredRoomId: String? = null
    private var nodes: List<NavNode> = emptyList()
    private var edges: List<NavEdge> = emptyList()
    private var userPosition: MapPoint? = null
    private var accuracyMeters: Float? = null
    private var locationMode: LocationMode = LocationMode.MANUAL
    private var manualPositionSelectionMode: Boolean = false
    private var route: List<NavNode> = emptyList()
    private var destination: Room? = null
    private var settings: AppSettings = AppSettings()
    private var compassHeadingDegrees: Float = 0f
    private var tapCoordinates: PointF? = null

    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var fitScale = 1f
    private val mapMatrix = Matrix()
    private var converter = SvgCoordinateConverter(mapMatrix)
    private var pointerOnMarker = false
    private var movedSinceDown = false
    private var dashPhase = 0f
    private var lastFrameTime = 0L
    private var pendingCenter: MapPoint? = null

    var onManualPosition: ((Float, Float) -> Unit)? = null
    var onManualPositionInRoom: ((Room, Float, Float) -> Unit)? = null
    var onRoomTap: ((Room) -> Unit)? = null
    var onTapCoordinate: ((Double, Double) -> Unit)? = null

    private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x443858D8
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val roomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xEEFFFFFF.toInt(); style = Paint.Style.FILL }
    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    init {
        isClickable = true
        contentDescription = context.getString(R.string.map_coordinates_description)
    }

    /** Обновляет данные карты. Вызывается из AndroidView.update при каждом изменении StateFlow. */
    fun submit(
        svg: SVG?,
        floor: FloorPlan?,
        rooms: List<Room>,
        nodes: List<NavNode>,
        edges: List<NavEdge>,
        userPosition: MapPoint?,
        accuracyMeters: Float?,
        locationMode: LocationMode,
        route: List<NavNode>?,
        destination: Room?,
        selectedRoom: Room?,
        settings: AppSettings,
        compassHeadingDegrees: Float = 0f,
        onManualPosition: (Float, Float) -> Unit,
        onRoomTap: (Room) -> Unit,
        onTapCoordinate: (Double, Double) -> Unit,
        manualPositionSelectionMode: Boolean = false,
        onManualPositionInRoom: (Room, Float, Float) -> Unit = { _, x, y -> onManualPosition(x, y) },
    ) {
        val floorChanged = this.floor?.number != floor?.number
        val geometryChanged = floorChanged || this.rooms != rooms
        this.svg = svg
        this.floor = floor
        this.rooms = rooms
        this.selectedRoom = selectedRoom
        if (geometryChanged && floor != null) updateRoomGeometries(floor, rooms)
        this.nodes = nodes
        this.edges = edges
        this.userPosition = userPosition
        this.accuracyMeters = accuracyMeters
        this.locationMode = locationMode
        this.manualPositionSelectionMode = manualPositionSelectionMode
        this.route = route.orEmpty()
        this.destination = destination
        this.settings = settings
        this.compassHeadingDegrees = compassHeadingDegrees
        this.onManualPosition = onManualPosition
        this.onManualPositionInRoom = onManualPositionInRoom
        this.onRoomTap = onRoomTap
        this.onTapCoordinate = onTapCoordinate
        zoom = zoom.coerceIn(settings.minimumZoom, settings.maximumZoom)
        if (floorChanged) {
            zoom = 1f.coerceIn(settings.minimumZoom, settings.maximumZoom)
            panX = 0f
            panY = 0f
        }
        updateMatrix()
        invalidate()
    }

    fun zoomIn() = zoomAt(zoom * 1.25f, width / 2f, height / 2f)
    fun zoomOut() = zoomAt(zoom / 1.25f, width / 2f, height / 2f)

    fun centerOn(point: MapPoint) {
        if (point.floor != floor?.number) return
        if (width <= 0 || height <= 0) {
            pendingCenter = point
            return
        }
        pendingCenter = null
        updateMatrix()
        val screen = converter.svgToView(point.x, point.y)
        panX += width / 2f - screen.x
        panY += height / 2f - screen.y
        updateMatrix()
        invalidate()
    }

    fun resetCamera() {
        zoom = 1f.coerceIn(settings.minimumZoom, settings.maximumZoom)
        panX = 0f
        panY = 0f
        updateMatrix()
        invalidate()
    }

    fun zoomLevel(): Float = zoom

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val darkTheme = settings.themeMode == ThemeMode.DARK || (settings.themeMode == ThemeMode.SYSTEM && systemDark)
        canvas.drawColor(if (darkTheme) 0xFF191D29.toInt() else 0xFFF0F2F8.toInt())
        val currentSvg = svg
        val currentFloor = floor
        if (currentSvg == null || currentFloor == null || width <= 0 || height <= 0) {
            drawCenteredMessage(canvas, context.getString(R.string.map_loading_svg))
            return
        }

        updateMatrix()
        val saveCount = canvas.save()
        canvas.setDrawFilter(drawFilterFor(settings.svgRenderQuality))
        canvas.concat(mapMatrix)
        val highlightRoom = destination?.takeIf { it.floor == currentFloor.number }
            ?: selectedRoom?.takeIf { it.floor == currentFloor.number }
        val highlightElementIds = highlightRoom?.let { room ->
            (room.svgElementIds + listOfNotNull(room.svgElementId)).distinct()
        }.orEmpty()
        SvgRenderer.render(canvas, currentSvg, currentFloor, highlightElementIds)
        val mapScale = fitScale * zoom
        if (settings.showCoordinateGrid) drawCoordinateGrid(canvas, currentFloor)
        if (settings.showNavigationEdges) drawDebugEdges(canvas, currentFloor)
        if (settings.showNavigationNodes || settings.showDoorNodes) drawDebugNodes(canvas, currentFloor, mapScale)
        drawRoomHighlights(canvas, currentFloor, mapScale)
        drawRoute(canvas, currentFloor, mapScale)
        drawRoomLabels(canvas, currentFloor, mapScale)
        drawUserMarker(canvas, currentFloor, mapScale)
        canvas.restoreToCount(saveCount)
        drawTapCoordinateOverlay(canvas)

        if (settings.animationsEnabled && settings.routeLineStyle == RouteLineStyle.ANIMATED && route.isNotEmpty()) {
            val now = SystemClock.uptimeMillis()
            val elapsed = if (lastFrameTime == 0L) 0L else now - lastFrameTime
            lastFrameTime = now
            dashPhase = (dashPhase + elapsed * 0.012f / max(mapScale, 0.0001f)) % (18f / max(mapScale, 0.0001f))
            postInvalidateOnAnimation()
        } else {
            lastFrameTime = 0L
            dashPhase = 0f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
        pendingCenter?.takeIf { it.floor == floor?.number }?.let(::centerOn)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        } else if (event.pointerCount > 1) {
            movedSinceDown = true
        }
        return true
    }

    private fun updateMatrix() {
        val currentFloor = floor ?: return
        if (width <= 0 || height <= 0 || currentFloor.width <= 0 || currentFloor.height <= 0) return
        fitScale = min(width / currentFloor.width.toFloat(), height / currentFloor.height.toFloat())
        val scaled = fitScale * zoom
        val baseLeft = (width - currentFloor.width * fitScale) / 2f
        val baseTop = (height - currentFloor.height * fitScale) / 2f
        val rawTx = baseLeft * zoom + width / 2f * (1f - zoom) + panX - currentFloor.viewBoxX.toFloat() * scaled
        val rawTy = baseTop * zoom + height / 2f * (1f - zoom) + panY - currentFloor.viewBoxY.toFloat() * scaled
        val rotation = if (settings.compassRotationEnabled) -compassHeadingDegrees else 0f
        val radians = Math.toRadians(rotation.toDouble())
        val cosine = kotlin.math.cos(radians).toFloat()
        val sine = kotlin.math.sin(radians).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val tx = centerX + cosine * (rawTx - centerX) - sine * (rawTy - centerY)
        val ty = centerY + sine * (rawTx - centerX) + cosine * (rawTy - centerY)
        mapMatrix.setValues(
            floatArrayOf(
                cosine * scaled, -sine * scaled, tx,
                sine * scaled, cosine * scaled, ty,
                0f, 0f, 1f,
            ),
        )
        converter = SvgCoordinateConverter(mapMatrix)
    }

    private fun zoomAt(target: Float, focusX: Float, focusY: Float) {
        val point = converter.viewToSvg(focusX, focusY)
        zoom = target.coerceIn(settings.minimumZoom, settings.maximumZoom)
        updateMatrix()
        if (point != null) {
            val after = converter.svgToView(point.x, point.y)
            panX += focusX - after.x
            panY += focusY - after.y
            updateMatrix()
        }
        invalidate()
    }

    private fun updateRoomGeometries(floor: FloorPlan, floorRooms: List<Room>) {
        roomGeometries = floorRooms.asSequence()
            .filter { it.floor == floor.number }
            .mapNotNull { room ->
                val catalogPolygons = room.polygons.ifEmpty {
                    listOfNotNull(room.polygon.takeIf { it.size >= 3 })
                }.map { polygon -> polygon.map { point -> PointF(point.x.toFloat(), point.y.toFloat()) } }
                val elementIds = (room.svgElementIds + listOfNotNull(room.svgElementId)).distinct()
                val extractedPolygons = elementIds.mapNotNull { id ->
                    runCatching { SvgGeometryExtractor.polygon(context.assets, floor.mapAsset, id) }.getOrNull()
                }
                val polygons = (catalogPolygons.ifEmpty { extractedPolygons })
                    .filter { it.size >= 3 }
                if (polygons.isEmpty()) return@mapNotNull null
                val bounds = polygons.mapNotNull(::polygonBounds).reduceOrNull { result, next ->
                    result.union(next)
                    result
                } ?: return@mapNotNull null
                val area = polygons.sumOf { polygon -> kotlin.math.abs(polygon.indices.sumOf { index ->
                    val current = polygon[index]
                    val next = polygon[(index + 1) % polygon.size]
                    current.x.toDouble() * next.y.toDouble() - next.x.toDouble() * current.y.toDouble()
                }) / 2.0 }.toFloat()
                RoomGeometry(room = room, polygons = polygons, bounds = bounds, area = area)
            }
            .sortedBy(RoomGeometry::area)
            .toList()
    }

    private fun roomAt(point: PointF): Room? = roomGeometries.asSequence()
        .filter { geometry ->
            point.x >= geometry.bounds.left && point.x <= geometry.bounds.right &&
                point.y >= geometry.bounds.top && point.y <= geometry.bounds.bottom
        }
        .filter { geometry -> geometry.polygons.any { polygon -> isPointInPolygon(point, polygon) } }
        .minByOrNull(RoomGeometry::area)
        ?.room

    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> {
                val point = converter.viewToSvg(event.x, event.y)
                val hovered = point?.let { roomAt(PointF(it.x, it.y)) }?.id
                if (hoveredRoomId != hovered) {
                    hoveredRoomId = hovered
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                if (hoveredRoomId != null) {
                    hoveredRoomId = null
                    invalidate()
                }
                return true
            }
        }
        return super.onHoverEvent(event)
    }

    private fun drawCoordinateGrid(canvas: Canvas, floor: FloorPlan) {
        val step = if (floor.width > 1_500) 100.0 else 25.0
        var x = floor.viewBoxX
        val right = floor.viewBoxX + floor.width
        val bottom = floor.viewBoxY + floor.height
        while (x <= right) {
            canvas.drawLine(x.toFloat(), floor.viewBoxY.toFloat(), x.toFloat(), bottom.toFloat(), gridPaint)
            x += step
        }
        var y = floor.viewBoxY
        while (y <= bottom) {
            canvas.drawLine(floor.viewBoxX.toFloat(), y.toFloat(), right.toFloat(), y.toFloat(), gridPaint)
            y += step
        }
    }

    private fun drawDebugEdges(canvas: Canvas, floor: FloorPlan) {
        val byId = nodes.associateBy(NavNode::id)
        debugPaint.color = 0xAA8E44AD.toInt()
        debugPaint.strokeWidth = 1.6f / max(fitScale * zoom, 0.0001f)
        edges.forEach { edge ->
            val from = byId[edge.from]?.takeIf { it.floor == floor.number } ?: return@forEach
            val to = byId[edge.to]?.takeIf { it.floor == floor.number } ?: return@forEach
            canvas.drawLine(from.x.toFloat(), from.y.toFloat(), to.x.toFloat(), to.y.toFloat(), debugPaint)
        }
    }

    private fun drawDebugNodes(canvas: Canvas, floor: FloorPlan, mapScale: Float) {
        val radius = 3.5f * resources.displayMetrics.density / max(mapScale, 0.0001f)
        debugPaint.style = Paint.Style.FILL
        nodes.asSequence()
            .filter { it.floor == floor.number }
            .filter { settings.showNavigationNodes || (settings.showDoorNodes && it.type.equals("door", ignoreCase = true)) }
            .forEach { node ->
                debugPaint.color = if (node.type.equals("door", ignoreCase = true)) 0xFFE53935.toInt() else 0xFFAF236F.toInt()
                canvas.drawCircle(node.x.toFloat(), node.y.toFloat(), radius * if (node.type.equals("door", true)) 1.6f else 1f, debugPaint)
            }
        debugPaint.style = Paint.Style.STROKE
    }

    private fun drawRoute(canvas: Canvas, floor: FloorPlan, mapScale: Float) {
        if (route.isEmpty()) return
        val segments = mutableListOf<MutableList<NavNode>>()
        var active: MutableList<NavNode>? = null
        route.forEach { node ->
            if (node.floor == floor.number) {
                if (active == null) {
                    val newSegment = mutableListOf<NavNode>()
                    active = newSegment
                    segments.add(newSegment)
                }
                active?.add(node)
            } else active = null
        }
        val widthInSvgUnits = settings.routeLineWidthDp * resources.displayMetrics.density / max(mapScale, 0.0001f)
        routePaint.color = settings.routeColorArgb
        routePaint.strokeWidth = widthInSvgUnits
        routePaint.pathEffect = when (settings.routeLineStyle) {
            RouteLineStyle.SOLID -> null
            RouteLineStyle.DASHED -> DashPathEffect(floatArrayOf(widthInSvgUnits * 2.5f, widthInSvgUnits * 1.7f), 0f)
            RouteLineStyle.ANIMATED -> if (settings.animationsEnabled) {
                DashPathEffect(floatArrayOf(widthInSvgUnits * 0.8f, widthInSvgUnits * 2.1f), dashPhase)
            } else null
        }
        segments.forEach { segment ->
            val points = segment.map { PointF(it.x.toFloat(), it.y.toFloat()) }.toMutableList()
            if (segment.firstOrNull()?.id == route.firstOrNull()?.id) {
                userPosition?.takeIf { it.floor == floor.number }?.let { points.add(0, PointF(it.x, it.y)) }
            }
            if (segment.lastOrNull()?.id == route.lastOrNull()?.id) {
                destination?.takeIf { it.floor == floor.number }?.let { room ->
                    val x = room.x
                    val y = room.y
                    if (x != null && y != null) points += PointF(x.toFloat(), y.toFloat())
                }
            }
            if (points.size < 2) return@forEach
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            canvas.drawPath(path, routePaint)
            routePaint.pathEffect = null
            segment.drop(1).dropLast(1).forEach { node ->
                canvas.drawCircle(node.x.toFloat(), node.y.toFloat(), widthInSvgUnits * 0.85f, haloPaint)
                roomPaint.color = settings.routeColorArgb
                canvas.drawCircle(node.x.toFloat(), node.y.toFloat(), widthInSvgUnits * 0.48f, roomPaint)
            }
            routePaint.pathEffect = when (settings.routeLineStyle) {
                RouteLineStyle.SOLID -> null
                RouteLineStyle.DASHED -> DashPathEffect(floatArrayOf(widthInSvgUnits * 2.5f, widthInSvgUnits * 1.7f), 0f)
                RouteLineStyle.ANIMATED -> if (settings.animationsEnabled) DashPathEffect(floatArrayOf(widthInSvgUnits * 0.8f, widthInSvgUnits * 2.1f), dashPhase) else null
            }
        }
    }

    /** Подсветка рисуется отдельным полупрозрачным слоем поверх исходного SVG. */
    private fun drawRoomHighlights(canvas: Canvas, floor: FloorPlan, mapScale: Float) {
        val destinationId = destination?.takeIf { it.floor == floor.number }?.id
        val selectedId = selectedRoom?.takeIf { it.floor == floor.number }?.id
        roomGeometries.forEach { geometry ->
            val isDestination = geometry.room.id == destinationId
            val isSelected = geometry.room.id == selectedId
            val isHovered = geometry.room.id == hoveredRoomId
            if (!isDestination && !isSelected && !isHovered) return@forEach
            geometry.polygons.forEach polygonLoop@ { polygon ->
                if (polygon.size < 3) return@polygonLoop
                val path = Path().apply {
                    moveTo(polygon.first().x, polygon.first().y)
                    polygon.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                roomPaint.style = Paint.Style.FILL
                roomPaint.color = when {
                    isDestination -> 0x6659C36A
                    isSelected -> 0x66FFE082
                    else -> 0x4467B7FF
                }
                canvas.drawPath(path, roomPaint)
                debugPaint.color = when {
                    isDestination -> 0xFF2E9D48.toInt()
                    isSelected -> 0xFFE69B00.toInt()
                    else -> 0xFF2D83D5.toInt()
                }
                debugPaint.strokeWidth = 2.2f * resources.displayMetrics.density / max(mapScale, 0.0001f)
                canvas.drawPath(path, debugPaint)
            }
        }
    }

    /** Подписи располагаются в центре геометрии, без старого синего маркера-точки. */
    private fun drawRoomLabels(canvas: Canvas, floor: FloorPlan, mapScale: Float) {
        val labelsEnabled = when (settings.roomLabelMode) {
            RoomLabelMode.ALWAYS -> true
            RoomLabelMode.ON_ZOOM -> zoom >= 1.28f
            RoomLabelMode.NEVER -> false
        }
        rooms.asSequence().filter { it.floor == floor.number }.forEach { room ->
            if (!labelsEnabled) return@forEach
            val isTarget = room.id == destination?.id || room.id == selectedRoom?.id
            val geometry = roomGeometries.firstOrNull { it.room.id == room.id }
            val centerX = room.centerX?.toFloat() ?: room.x?.toFloat() ?: geometry?.bounds?.centerX()
                ?: return@forEach
            val centerY = room.centerY?.toFloat() ?: room.y?.toFloat() ?: geometry?.bounds?.centerY()
                ?: return@forEach
            val label = room.number?.takeIf(String::isNotBlank) ?: room.localizedName(context).take(18)
            drawLabel(
                canvas = canvas,
                text = label,
                x = centerX,
                y = centerY,
                mapScale = mapScale,
                highlighted = isTarget,
                polygon = geometry?.polygons.orEmpty().flatten(),
            )
        }
    }

    private fun drawUserMarker(canvas: Canvas, floor: FloorPlan, mapScale: Float) {
        val point = userPosition?.takeIf { it.floor == floor.number } ?: return
        if (settings.showAccuracyCircle && locationMode == LocationMode.GPS) {
            val accuracy = accuracyMeters?.takeIf { it.isFinite() && it > 0f } ?: 0f
            val radius = accuracy / floor.metersPerUnit.toFloat()
            if (radius > 0f) {
                roomPaint.color = 0x331477F8
                canvas.drawCircle(point.x, point.y, radius, roomPaint)
                debugPaint.color = 0x771477F8
                debugPaint.strokeWidth = 1.2f * resources.displayMetrics.density / max(mapScale, 0.0001f)
                canvas.drawCircle(point.x, point.y, radius, debugPaint)
            }
        }
        val radius = 11f * resources.displayMetrics.density / max(mapScale, 0.0001f)
        canvas.drawCircle(point.x, point.y, radius * 1.28f, haloPaint)
        roomPaint.color = if (locationMode == LocationMode.GPS) 0xFF1478F8.toInt() else 0xFF12A76A.toInt()
        canvas.drawCircle(point.x, point.y, radius, roomPaint)
        haloPaint.color = Color.WHITE
        canvas.drawCircle(point.x, point.y, radius * 0.24f, haloPaint)
        haloPaint.color = Color.WHITE
        if (locationMode == LocationMode.MANUAL) drawLabel(canvas, context.getString(R.string.location_me_here), point.x + radius * 1.2f, point.y - radius * 1.4f, mapScale, highlighted = false)
    }

    private fun drawLabel(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        mapScale: Float,
        highlighted: Boolean,
        polygon: List<PointF> = emptyList(),
    ) {
        if (text.isBlank()) return
        val density = resources.displayMetrics.density
        val desiredScreenSize = (11f * resources.displayMetrics.scaledDensity * zoom).coerceIn(8f * density, 22f * density)
        val fontSize = desiredScreenSize / max(mapScale, 0.0001f)
        textPaint.textSize = fontSize
        textPaint.color = if (highlighted) 0xFF315D31.toInt() else 0xFF1D2741.toInt()
        val padding = 3f * density / max(mapScale, 0.0001f)
        val polygonBox = polygonBounds(polygon)
        val maxWidth = polygonBox?.width()?.minus(padding * 3f)?.takeIf { it > 0f }
        val shownText = if (maxWidth != null && textPaint.measureText(text) > maxWidth) {
            var shortened = text
            while (shortened.isNotEmpty() && textPaint.measureText("$shortened…") > maxWidth) shortened = shortened.dropLast(1)
            if (shortened.isEmpty()) "" else "$shortened…"
        } else text
        if (shownText.isBlank()) return
        val textWidth = textPaint.measureText(shownText)
        val ascent = textPaint.ascent()
        val descent = textPaint.descent()
        val left = x - textWidth / 2f - padding
        val top = y - (descent - ascent) / 2f - padding
        val bounds = RectF(left, top, left + textWidth + 2 * padding, top + descent - ascent + 2 * padding)
        labelPaint.color = if (highlighted) 0xE6E8F6C8.toInt() else 0xDDF9FAFF.toInt()
        canvas.drawRoundRect(bounds, padding * 1.4f, padding * 1.4f, labelPaint)
        canvas.drawText(shownText, x - textWidth / 2f, y - (ascent + descent) / 2f, textPaint)
    }

    private fun drawTapCoordinateOverlay(canvas: Canvas) {
        val point = tapCoordinates ?: return
        if (!settings.showTapCoordinates) return
        val density = resources.displayMetrics.density
        val message = context.getString(R.string.coordinate_overlay_format, point.x, point.y)
        textPaint.textSize = 12f * resources.displayMetrics.scaledDensity
        textPaint.color = Color.DKGRAY
        val padding = 8f * density
        val textWidth = textPaint.measureText(message)
        labelPaint.color = 0xEEFFFFFF.toInt()
        canvas.drawRoundRect(12f * density, height - 42f * density, 12f * density + textWidth + padding * 2, height - 12f * density, 8f * density, 8f * density, labelPaint)
        canvas.drawText(message, 12f * density + padding, height - 22f * density, textPaint)
    }

    private fun drawCenteredMessage(canvas: Canvas, message: String) {
        textPaint.textSize = 14f * resources.displayMetrics.scaledDensity
        textPaint.color = 0xFF596276.toInt()
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(message, width / 2f, height / 2f, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawFilterFor(quality: SvgRenderQuality): PaintFlagsDrawFilter = when (quality) {
        SvgRenderQuality.LOW -> PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG, 0)
        SvgRenderQuality.MEDIUM -> PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG)
        SvgRenderQuality.HIGH -> PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var focusPoint: PointF? = null

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            movedSinceDown = true
            focusPoint = converter.viewToSvg(detector.focusX, detector.focusY)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val focusY = detector.focusY
            val anchor = focusPoint ?: converter.viewToSvg(focusX, focusY)
            zoom = (zoom * detector.scaleFactor).coerceIn(settings.minimumZoom, settings.maximumZoom)
            updateMatrix()
            if (anchor != null) {
                val after = converter.svgToView(anchor.x, anchor.y)
                panX += focusX - after.x
                panY += focusY - after.y
                updateMatrix()
            }
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            focusPoint = null
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
            movedSinceDown = false
            val position = userPosition?.takeIf { it.floor == floor?.number && locationMode == LocationMode.MANUAL }
            val marker = position?.let { converter.svgToView(it.x, it.y) }
            pointerOnMarker = marker != null && hypot(marker.x - event.x, marker.y - event.y) <= MARKER_DRAG_RADIUS_DP * resources.displayMetrics.density
            return true
        }

        override fun onScroll(first: MotionEvent?, current: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            movedSinceDown = true
            if (pointerOnMarker && locationMode == LocationMode.MANUAL) {
                converter.viewToSvg(current.x, current.y)?.let { onManualPosition?.invoke(it.x, it.y) }
            } else {
                panX -= distanceX
                panY -= distanceY
                updateMatrix()
                invalidate()
            }
            return true
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            val mapPoint = converter.viewToSvg(event.x, event.y) ?: return true
            tapCoordinates = mapPoint
            onTapCoordinate?.invoke(mapPoint.x.toDouble(), mapPoint.y.toDouble())
            if (pointerOnMarker) {
                invalidate()
                return true
            }
            val tappedRoom = roomAt(mapPoint)
            if (locationMode == LocationMode.MANUAL && manualPositionSelectionMode) {
                if (tappedRoom != null) onManualPositionInRoom?.invoke(tappedRoom, mapPoint.x, mapPoint.y)
                else onManualPosition?.invoke(mapPoint.x, mapPoint.y)
            } else if (tappedRoom != null) {
                onRoomTap?.invoke(tappedRoom)
            } else if (locationMode == LocationMode.MANUAL) {
                onManualPosition?.invoke(mapPoint.x, mapPoint.y)
            }
            invalidate()
            return true
        }
    }

    private data class RoomGeometry(
        val room: Room,
        val polygons: List<List<PointF>>,
        val bounds: RectF,
        val area: Float,
    )

    private companion object {
        const val MARKER_DRAG_RADIUS_DP = 34f
    }
}
