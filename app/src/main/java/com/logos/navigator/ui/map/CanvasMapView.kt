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
import com.logos.navigator.R
import com.logos.navigator.data.model.AppSettings
import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.LocationMode
import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.NavEdge
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.Room
import com.logos.navigator.data.model.RouteLineStyle
import com.logos.navigator.data.model.MapRenderQuality
import com.logos.navigator.data.model.ThemeMode
import com.logos.navigator.svg.SvgCoordinateConverter
import com.logos.navigator.svg.isPointInPolygon
import com.logos.navigator.svg.polygonBounds
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Нативный Canvas-view: базовый план рисуется сгенерированными Kotlin Path-командами. */
class CanvasMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var floor: FloorPlan? = null
    private var rooms: List<Room> = emptyList()
    private var roomGeometries: List<RoomGeometry> = emptyList()
    private var routeSegments: List<RouteFloorSegment> = emptyList()
    private var selectedRoom: Room? = null
    private var hoveredRoomId: String? = null
    private var nodes: List<NavNode> = emptyList()
    private var nodesById: Map<String, NavNode> = emptyMap()
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
    private val matrixValues = FloatArray(9)
    private val converter = SvgCoordinateConverter(mapMatrix)
    private var pointerOnMarker = false
    private var movedSinceDown = false
    private var dashPhase = 0f
    private var dashedRouteWidth = Float.NaN
    private var dashedRouteEffect: DashPathEffect? = null
    private var animatedRouteWidth = Float.NaN
    private var animatedDashIntervals = FloatArray(2)
    private var lastFrameTime = 0L
    private var pendingCenter: MapPoint? = null

    var onManualPosition: ((Float, Float) -> Unit)? = null
    var onManualPositionInRoom: ((Room, Float, Float) -> Unit)? = null
    var onRoomTap: ((Room) -> Unit)? = null
    var onTapCoordinate: ((Double, Double) -> Unit)? = null

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
    private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val selectionHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xF5FFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val selectionOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
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
        val floorChanged = this.floor != floor
        val geometryChanged = floorChanged || this.rooms !== rooms
        val nextRoute = route.orEmpty()
        val routeChanged = this.route != nextRoute
        val positionChanged = this.userPosition != userPosition
        val accuracyChanged = this.accuracyMeters != accuracyMeters
        val locationModeChanged = this.locationMode != locationMode
        val selectionChanged = this.selectedRoom != selectedRoom || this.destination != destination
        val nodesChanged = this.nodes != nodes
        val edgesChanged = this.edges != edges
        val compassChanged = settings.compassRotationEnabled && this.compassHeadingDegrees != compassHeadingDegrees
        val compassModeChanged = this.settings.compassRotationEnabled != settings.compassRotationEnabled
        val settingsChanged = this.settings != settings
        val oldZoom = zoom

        this.floor = floor
        this.rooms = rooms
        this.selectedRoom = selectedRoom
        if (geometryChanged && floor != null) updateRoomGeometries(floor, rooms)
        if (nodesChanged) nodesById = nodes.associateBy(NavNode::id)
        this.nodes = nodes
        this.edges = edges
        this.userPosition = userPosition
        this.accuracyMeters = accuracyMeters
        this.locationMode = locationMode
        this.manualPositionSelectionMode = manualPositionSelectionMode
        if (routeChanged) routeSegments = buildRouteSegments(nextRoute)
        this.route = nextRoute
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
        val zoomChanged = zoom != oldZoom
        if (floorChanged || zoomChanged || compassModeChanged || compassChanged) updateMatrix()

        val visualsChanged = floorChanged || geometryChanged || routeChanged || positionChanged ||
            accuracyChanged || locationModeChanged || selectionChanged || nodesChanged || edgesChanged ||
            settingsChanged || compassChanged || compassModeChanged
        if (visualsChanged) invalidate()
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
        val currentFloor = floor
        if (currentFloor == null || width <= 0 || height <= 0) return

        val saveCount = canvas.save()
        canvas.setDrawFilter(drawFilterFor(settings.mapRenderQuality))
        canvas.concat(mapMatrix)
        GeneratedFloorMaps.draw(canvas, currentFloor.number)
        val mapScale = fitScale * zoom
        if (settings.showCoordinateGrid) drawCoordinateGrid(canvas, currentFloor)
        if (settings.showNavigationEdges) drawDebugEdges(canvas, currentFloor)
        if (settings.showNavigationNodes || settings.showDoorNodes) drawDebugNodes(canvas, currentFloor, mapScale)
        drawRoomHighlights(canvas, currentFloor, mapScale)
        drawRoute(canvas, currentFloor, mapScale)
        drawUserMarker(canvas, currentFloor, mapScale)
        canvas.restoreToCount(saveCount)
        drawTapCoordinateOverlay(canvas)

        if (settings.animationsEnabled && settings.routeLineStyle == RouteLineStyle.ANIMATED &&
            routeSegments.any { it.floor == currentFloor.number }
        ) {
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
        val cosine: Float
        val sine: Float
        if (rotation == 0f) {
            cosine = 1f
            sine = 0f
        } else {
            val radians = Math.toRadians(rotation.toDouble())
            cosine = kotlin.math.cos(radians).toFloat()
            sine = kotlin.math.sin(radians).toFloat()
        }
        val centerX = width / 2f
        val centerY = height / 2f
        val tx = centerX + cosine * (rawTx - centerX) - sine * (rawTy - centerY)
        val ty = centerY + sine * (rawTx - centerX) + cosine * (rawTy - centerY)
        matrixValues[0] = cosine * scaled
        matrixValues[1] = -sine * scaled
        matrixValues[2] = tx
        matrixValues[3] = sine * scaled
        matrixValues[4] = cosine * scaled
        matrixValues[5] = ty
        matrixValues[6] = 0f
        matrixValues[7] = 0f
        matrixValues[8] = 1f
        mapMatrix.setValues(matrixValues)
        converter.refreshInverse()
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
                    .filter { it.size >= 3 }
                val polygons = if (catalogPolygons.isNotEmpty()) {
                    catalogPolygons
                } else {
                    // Используем заранее сгенерированные Path-координаты для редких записей без полигонов в JSON.
                    GeneratedFloorMaps.roomPolygons(
                        floor.number,
                        (room.svgElementIds + listOfNotNull(room.svgElementId)).distinct(),
                    ).filter { it.size >= 3 }
                }
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
                val paths = polygons.map { polygon ->
                    Path().apply {
                        moveTo(polygon.first().x, polygon.first().y)
                        polygon.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                }
                RoomGeometry(room = room, polygons = polygons, paths = paths, bounds = bounds, area = area)
            }
            .sortedBy(RoomGeometry::area)
            .toList()
    }

    private fun buildRouteSegments(route: List<NavNode>): List<RouteFloorSegment> {
        if (route.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<NavNode>>()
        var activeFloor: Int? = null
        var activeNodes: MutableList<NavNode>? = null
        route.forEach { node ->
            if (activeNodes == null || activeFloor != node.floor) {
                activeFloor = node.floor
                val newSegment = mutableListOf<NavNode>()
                groups += newSegment
                activeNodes = newSegment
            }
            activeNodes?.add(node)
        }
        return groups.map { segmentNodes ->
            val path = Path().apply {
                moveTo(segmentNodes.first().x.toFloat(), segmentNodes.first().y.toFloat())
                segmentNodes.drop(1).forEach { lineTo(it.x.toFloat(), it.y.toFloat()) }
            }
            RouteFloorSegment(segmentNodes.first().floor, segmentNodes, path)
        }
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
                val hovered = if (point == null) null else roomAt(point)?.id
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
        debugPaint.color = 0xAA8E44AD.toInt()
        debugPaint.strokeWidth = 1.6f / max(fitScale * zoom, 0.0001f)
        edges.forEach { edge ->
            val from = nodesById[edge.from]?.takeIf { it.floor == floor.number } ?: return@forEach
            val to = nodesById[edge.to]?.takeIf { it.floor == floor.number } ?: return@forEach
            canvas.drawLine(from.x.toFloat(), from.y.toFloat(), to.x.toFloat(), to.y.toFloat(), debugPaint)
        }
    }

    private fun drawDebugNodes(canvas: Canvas, floor: FloorPlan, mapScale: Float) {
        val radius = 3.5f * resources.displayMetrics.density / max(mapScale, 0.0001f)
        debugPaint.style = Paint.Style.FILL
        for (node in nodes) {
            if (node.floor != floor.number) continue
            val isDoor = node.type.equals("door", ignoreCase = true)
            if (!settings.showNavigationNodes && !(settings.showDoorNodes && isDoor)) continue
            debugPaint.color = if (isDoor) 0xFFE53935.toInt() else 0xFFAF236F.toInt()
            canvas.drawCircle(node.x.toFloat(), node.y.toFloat(), radius * if (isDoor) 1.6f else 1f, debugPaint)
        }
        debugPaint.style = Paint.Style.STROKE
    }

    /** Использует заранее собранные пути: на жестах не создаются списки и новые Path. */
    private fun drawRoute(canvas: Canvas, floor: FloorPlan, mapScale: Float) {
        if (routeSegments.isEmpty()) return
        val widthInSvgUnits = settings.routeLineWidthDp * resources.displayMetrics.density / max(mapScale, 0.0001f)
        routePaint.color = settings.routeColorArgb
        routePaint.strokeWidth = widthInSvgUnits
        routePaint.pathEffect = when (settings.routeLineStyle) {
            RouteLineStyle.SOLID -> null
            RouteLineStyle.DASHED -> {
                if (dashedRouteEffect == null || dashedRouteWidth != widthInSvgUnits) {
                    dashedRouteWidth = widthInSvgUnits
                    dashedRouteEffect = DashPathEffect(floatArrayOf(widthInSvgUnits * 2.5f, widthInSvgUnits * 1.7f), 0f)
                }
                dashedRouteEffect
            }
            RouteLineStyle.ANIMATED -> if (settings.animationsEnabled) {
                if (animatedRouteWidth != widthInSvgUnits) {
                    animatedRouteWidth = widthInSvgUnits
                    animatedDashIntervals[0] = widthInSvgUnits * 0.8f
                    animatedDashIntervals[1] = widthInSvgUnits * 2.1f
                }
                DashPathEffect(animatedDashIntervals, dashPhase)
            } else null
        }
        val firstRouteNode = route.firstOrNull()
        val lastRouteNode = route.lastOrNull()
        routeSegments.forEach { segment ->
            if (segment.floor != floor.number) return@forEach
            val firstNode = segment.nodes.firstOrNull() ?: return@forEach
            val lastNode = segment.nodes.lastOrNull() ?: return@forEach
            canvas.drawPath(segment.path, routePaint)
            if (firstNode.id == firstRouteNode?.id) {
                userPosition?.takeIf { it.floor == floor.number }?.let { point ->
                    canvas.drawLine(point.x, point.y, firstNode.x.toFloat(), firstNode.y.toFloat(), routePaint)
                }
            }
            if (lastNode.id == lastRouteNode?.id) {
                destination?.takeIf { it.floor == floor.number }?.let { room ->
                    val x = room.x
                    val y = room.y
                    if (x != null && y != null) canvas.drawLine(lastNode.x.toFloat(), lastNode.y.toFloat(), x.toFloat(), y.toFloat(), routePaint)
                }
            }
            for (index in 1 until segment.nodes.lastIndex) {
                val node = segment.nodes[index]
                canvas.drawCircle(node.x.toFloat(), node.y.toFloat(), widthInSvgUnits * 0.85f, haloPaint)
                roomPaint.color = settings.routeColorArgb
                canvas.drawCircle(node.x.toFloat(), node.y.toFloat(), widthInSvgUnits * 0.48f, roomPaint)
            }
        }
        routePaint.pathEffect = null
    }

    /** Подсветка выбранной комнаты отделяется от плана контрастным ореолом и акцентной рамкой. */
    private fun drawRoomHighlights(canvas: Canvas, floor: FloorPlan, mapScale: Float) {
        val destinationId = destination?.takeIf { it.floor == floor.number }?.id
        val selectedId = selectedRoom?.takeIf { it.floor == floor.number }?.id
        val screenDensity = resources.displayMetrics.density / max(mapScale, 0.0001f)
        roomGeometries.forEach { geometry ->
            val isDestination = geometry.room.id == destinationId
            val isSelected = geometry.room.id == selectedId
            val isHovered = geometry.room.id == hoveredRoomId
            if (!isDestination && !isSelected && !isHovered) return@forEach

            val fillColor = when {
                isDestination -> 0x8054B96A.toInt()
                isSelected -> Color.argb(
                    0x66,
                    Color.red(settings.accentColorArgb),
                    Color.green(settings.accentColorArgb),
                    Color.blue(settings.accentColorArgb),
                )
                else -> 0x5567B7FF
            }
            val outlineColor = when {
                isDestination -> 0xFF238A42.toInt()
                isSelected -> settings.accentColorArgb
                else -> 0xFF2D83D5.toInt()
            }
            geometry.paths.forEach { path ->
                selectionFillPaint.color = fillColor
                canvas.drawPath(path, selectionFillPaint)

                // Обрезаем обводку по полигону комнаты, чтобы рамка не увеличивала её контур.
                val highlightSaveCount = canvas.save()
                canvas.clipPath(path)
                selectionHaloPaint.strokeWidth = 2.4f * screenDensity
                canvas.drawPath(path, selectionHaloPaint)
                selectionOutlinePaint.color = outlineColor
                selectionOutlinePaint.strokeWidth = 1.5f * screenDensity
                canvas.drawPath(path, selectionOutlinePaint)
                canvas.restoreToCount(highlightSaveCount)
            }
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
        if (locationMode == LocationMode.MANUAL) {
            drawLocationLabel(
                canvas = canvas,
                text = context.getString(R.string.location_me_here),
                x = point.x + radius * 1.2f,
                y = point.y - radius * 1.4f,
                mapScale = mapScale,
            )
        }
    }

    private fun drawLocationLabel(canvas: Canvas, text: String, x: Float, y: Float, mapScale: Float) {
        if (text.isBlank()) return
        val density = resources.displayMetrics.density
        val desiredScreenSize = (11f * resources.displayMetrics.scaledDensity * zoom).coerceIn(8f * density, 22f * density)
        textPaint.textSize = desiredScreenSize / max(mapScale, 0.0001f)
        textPaint.color = 0xFF1D2741.toInt()
        val padding = 3f * density / max(mapScale, 0.0001f)
        val textWidth = textPaint.measureText(text)
        val ascent = textPaint.ascent()
        val descent = textPaint.descent()
        val left = x - textWidth / 2f - padding
        val top = y - (descent - ascent) / 2f - padding
        val bounds = RectF(left, top, left + textWidth + 2 * padding, top + descent - ascent + 2 * padding)
        labelPaint.color = 0xDDF9FAFF.toInt()
        canvas.drawRoundRect(bounds, padding * 1.4f, padding * 1.4f, labelPaint)
        canvas.drawText(text, x - textWidth / 2f, y - (ascent + descent) / 2f, textPaint)
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

    private fun drawFilterFor(quality: MapRenderQuality): PaintFlagsDrawFilter = when (quality) {
        MapRenderQuality.LOW -> LOW_QUALITY_DRAW_FILTER
        MapRenderQuality.MEDIUM -> MEDIUM_QUALITY_DRAW_FILTER
        MapRenderQuality.HIGH -> HIGH_QUALITY_DRAW_FILTER
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
        val paths: List<Path>,
        val bounds: RectF,
        val area: Float,
    )

    private data class RouteFloorSegment(
        val floor: Int,
        val nodes: List<NavNode>,
        val path: Path,
    )

    private companion object {
        const val MARKER_DRAG_RADIUS_DP = 34f
        val LOW_QUALITY_DRAW_FILTER = PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG, 0)
        val MEDIUM_QUALITY_DRAW_FILTER = PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG)
        val HIGH_QUALITY_DRAW_FILTER = PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    }
}
