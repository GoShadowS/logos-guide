package com.logos.navigator.navigation

import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.NavEdge
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.Room
import com.logos.navigator.data.model.SvgPoint
import kotlin.math.abs
import kotlin.math.hypot

/** Геометрический вариант маршрутизатора сайта: дверь комнаты → ветка коридора → дверь цели. */
object SiteIndoorRouter {
    /** LIFT/STAIRS ограничивают способ перехода между этажами, ANY выбирает кратчайший из доступных. */
    enum class Transition { LIFT, STAIRS, ANY }

    data class RoutePlan(
        val nodes: List<NavNode>,
        val pointsByFloor: Map<Int, List<SvgPoint>>,
        val transferKind: String? = null,
    )

    private enum class Branch { MAIN, WEST, HALL, AUDITORIUM, LIBRARY }

    private data class RoomRegion(val id: String, val floor: Int, val polygon: List<SvgPoint>)

    private data class RoomAccess(val door: SvgPoint, val branch: Branch)

    private data class Endpoint(
        val floor: Int,
        val anchor: SvgPoint,
        val branch: Branch,
        val path: List<SvgPoint>,
        val roomId: String? = null,
    )

    private data class Leg(val floor: Int, val points: List<SvgPoint>)

    private data class Candidate(
        val legs: List<Leg>,
        val transferKind: String,
        val length: Double,
    )

    private val planSizes = mapOf(
        1 to (758.0 to 552.0),
        2 to (767.0 to 561.0),
    )

    // Координаты и точки доступа синхронизированы с map-geometry.js и map-routing.js сайта.
    private val roomRegions = listOf(
        rect("canteen", 1, 0.0, 0.0, 147.0, 156.0),
        rect("workshop-115", 1, 148.0, 94.0, 109.0, 62.0),
        rect("simulator-114", 1, 257.0, 94.0, 84.0, 62.0),
        rect("academic-office", 1, 341.0, 94.0, 184.0, 62.0),
        rect("lift-1", 1, 525.0, 94.0, 24.0, 62.0),
        rect("stairs-north-1", 1, 549.0, 94.0, 61.0, 62.0),
        rect("auditorium-104", 1, 610.0, 94.0, 147.0, 150.0),
        rect("class-119", 1, 0.0, 179.0, 62.0, 74.0),
        rect("class-118", 1, 0.0, 253.0, 62.0, 73.0),
        rect("toilet-men-1", 1, 86.0, 179.0, 84.0, 32.0),
        rect("toilet-women-1", 1, 86.0, 210.0, 84.0, 32.0),
        rect("language-148", 1, 168.0, 179.0, 77.0, 63.0),
        rect("aerodynamics-120", 1, 244.0, 179.0, 74.0, 63.0),
        rect("stairs-middle-1", 1, 318.0, 179.0, 64.0, 63.0),
        rect("director-149", 1, 382.0, 179.0, 68.0, 63.0),
        rect("changing-room", 1, 449.0, 179.0, 99.0, 63.0),
        rect("hall-1", 1, 548.0, 179.0, 62.0, 63.0),
        rect("entrance-1", 1, 548.0, 240.0, 62.0, 24.0),
        rect("stairs-west-1", 1, 0.0, 322.0, 62.0, 64.0),
        rect("class-139", 1, 86.0, 306.0, 63.0, 69.0),
        rect("class-138", 1, 86.0, 375.0, 63.0, 68.0),
        rect("class-162", 1, 0.0, 419.0, 62.0, 49.0),
        rect("training-centre-113", 1, 0.0, 468.0, 149.0, 84.0),

        rect("library-214", 2, 0.0, 0.0, 144.0, 114.0),
        rect("radio-lab-228", 2, 144.0, 94.0, 115.0, 63.0),
        rect("electro-radio-230", 2, 259.0, 94.0, 98.0, 63.0),
        rect("assistant-230", 2, 357.0, 94.0, 66.0, 63.0),
        rect("uav-lab-209", 2, 423.0, 94.0, 112.0, 63.0),
        rect("lift-2", 2, 535.0, 94.0, 26.0, 63.0),
        rect("stairs-north-2", 2, 561.0, 94.0, 49.0, 63.0),
        rect("meteorology-236", 2, 0.0, 182.0, 62.0, 97.0),
        rect("toilet-men-2", 2, 86.0, 182.0, 62.0, 32.0),
        rect("toilet-women-2", 2, 86.0, 214.0, 62.0, 34.0),
        rect("physics-211", 2, 168.0, 182.0, 154.0, 64.0),
        rect("stairs-middle-2", 2, 322.0, 182.0, 70.0, 64.0),
        rect("psychologist-224", 2, 392.0, 182.0, 33.0, 64.0),
        rect("class-217", 2, 425.0, 182.0, 55.0, 64.0),
        rect("class-229", 2, 480.0, 182.0, 31.0, 64.0),
        rect("informatics-222", 2, 511.0, 182.0, 50.0, 64.0),
        rect("stairs-west-2", 2, 0.0, 279.0, 62.0, 138.0),
        rect("chemistry-biology-202", 2, 86.0, 245.0, 62.0, 83.0),
        rect("assistant-202", 2, 86.0, 328.0, 62.0, 55.0),
        rect("practical-225", 2, 86.0, 383.0, 62.0, 34.0),
        RoomRegion(
            id = "electrical-lab-207",
            floor = 2,
            polygon = listOf(
                point(0.0, 417.0), point(62.0, 417.0), point(62.0, 477.0),
                point(86.0, 477.0), point(86.0, 560.0), point(0.0, 560.0),
            ),
        ),
        rect("biotech-lab-225", 2, 86.0, 417.0, 62.0, 143.0),
    )

    private val circulation = mapOf(
        1 to listOf(
            polygon(0.0, 156.0, 610.0, 156.0, 610.0, 179.0, 0.0, 179.0),
            polygon(62.0, 179.0, 86.0, 179.0, 86.0, 468.0, 62.0, 468.0),
            polygon(548.0, 179.0, 610.0, 179.0, 610.0, 242.0, 548.0, 242.0),
        ),
        2 to listOf(
            polygon(0.0, 114.0, 144.0, 114.0, 144.0, 182.0, 0.0, 182.0),
            polygon(144.0, 156.0, 610.0, 156.0, 610.0, 182.0, 144.0, 182.0),
            polygon(62.0, 182.0, 86.0, 182.0, 86.0, 477.0, 62.0, 477.0),
        ),
    )

    private val roomAccess = mapOf(
        "canteen" to access(94.0, 156.0),
        "workshop-115" to access(230.0, 156.0),
        "simulator-114" to access(279.0, 156.0),
        "academic-office" to access(412.0, 156.0),
        "lift-1" to access(537.0, 156.0),
        "stairs-north-1" to access(579.0, 156.0),
        "auditorium-104" to access(610.0, 205.0, Branch.AUDITORIUM),
        "class-119" to access(62.0, 246.0, Branch.WEST),
        "class-118" to access(62.0, 294.0, Branch.WEST),
        "toilet-men-1" to access(86.0, 205.0, Branch.WEST),
        "toilet-women-1" to access(86.0, 237.0, Branch.WEST),
        "language-148" to access(183.0, 181.0),
        "aerodynamics-120" to access(261.0, 181.0),
        "stairs-middle-1" to access(359.0, 181.0),
        "director-149" to access(416.0, 181.0),
        "changing-room" to access(505.0, 181.0),
        "hall-1" to access(579.0, 179.0, Branch.HALL),
        "entrance-1" to access(579.0, 241.0, Branch.HALL),
        "stairs-west-1" to access(62.0, 354.0, Branch.WEST),
        "class-139" to access(86.0, 367.0, Branch.WEST),
        "class-138" to access(86.0, 434.0, Branch.WEST),
        "class-162" to access(62.0, 450.0, Branch.WEST),
        "training-centre-113" to access(73.0, 468.0, Branch.WEST),

        "library-214" to access(116.0, 114.0, Branch.LIBRARY),
        "radio-lab-228" to access(217.0, 156.0),
        "electro-radio-230" to access(317.0, 156.0),
        "assistant-230" to access(390.0, 156.0),
        "uav-lab-209" to access(459.0, 156.0),
        "lift-2" to access(548.0, 156.0),
        "stairs-north-2" to access(585.0, 156.0),
        "meteorology-236" to access(62.0, 246.0, Branch.WEST),
        "toilet-men-2" to access(86.0, 193.0, Branch.WEST),
        "toilet-women-2" to access(86.0, 224.0, Branch.WEST),
        "physics-211" to access(249.0, 182.0),
        "stairs-middle-2" to access(355.0, 182.0),
        "psychologist-224" to access(409.0, 182.0),
        "class-217" to access(440.0, 182.0),
        "class-229" to access(495.0, 182.0),
        "informatics-222" to access(526.0, 182.0),
        "stairs-west-2" to access(62.0, 360.0, Branch.WEST),
        "chemistry-biology-202" to access(86.0, 263.0, Branch.WEST),
        "assistant-202" to access(86.0, 355.0, Branch.WEST),
        "practical-225" to access(86.0, 398.0, Branch.WEST),
        "electrical-lab-207" to access(62.0, 450.0, Branch.WEST),
        "biotech-lab-225" to access(86.0, 450.0, Branch.WEST),
    )

    // ID Android → ID сайта; составные кабинеты 202, 225 и 230 содержат несколько зон сайта.
    private val appRoomSiteIds = mapOf(
        "room_104" to listOf("auditorium-104"),
        "room_113" to listOf("training-centre-113"),
        "room_114" to listOf("simulator-114"),
        "room_115" to listOf("workshop-115"),
        "room_118" to listOf("class-118"),
        "room_119" to listOf("class-119"),
        "room_120" to listOf("aerodynamics-120"),
        "room_126" to listOf("canteen"),
        "room_138" to listOf("class-138"),
        "room_139" to listOf("class-139"),
        "room_education_department" to listOf("academic-office"),
        "room_148" to listOf("language-148"),
        "room_149" to listOf("director-149"),
        "room_202" to listOf("chemistry-biology-202", "assistant-202"),
        "room_207" to listOf("electrical-lab-207"),
        "room_209" to listOf("uav-lab-209"),
        "room_211" to listOf("physics-211"),
        "room_213" to listOf("meteorology-236"),
        "room_214" to listOf("library-214"),
        "room_217" to listOf("class-217"),
        "room_222" to listOf("informatics-222"),
        "room_224" to listOf("psychologist-224"),
        "room_225" to listOf("biotech-lab-225", "practical-225"),
        "room_228" to listOf("radio-lab-228"),
        "room_229" to listOf("class-229"),
        "room_230" to listOf("electro-radio-230", "assistant-230"),
        "room_hall_1" to listOf("hall-1"),
        "room_entrance_main" to listOf("entrance-1"),
        "room_cloakroom" to listOf("changing-room"),
        "room_wc_m_1" to listOf("toilet-men-1"),
        "room_wc_w_1" to listOf("toilet-women-1"),
        "room_stairs_west_1" to listOf("stairs-west-1"),
        "room_stairs_central_1" to listOf("stairs-middle-1"),
        "room_stairs_east_1" to listOf("stairs-north-1"),
        "room_lift_1" to listOf("lift-1"),
        "room_162" to listOf("class-162"),
        "room_wc_m_2" to listOf("toilet-men-2"),
        "room_wc_w_2" to listOf("toilet-women-2"),
        "room_stairs_west_2" to listOf("stairs-west-2"),
        "room_stairs_central_2" to listOf("stairs-middle-2"),
        "room_stairs_east_2" to listOf("stairs-north-2"),
        "room_lift_2" to listOf("lift-2"),
    )

    /** Проверяет фактическую область плана по комнатам и замкнутым коридорным контурам сайта. */
    fun isInsideCollege(floor: Int, x: Double, y: Double, rooms: List<Room> = emptyList()): Boolean {
        val size = planSizes[floor] ?: return false
        if (!x.isFinite() || !y.isFinite() || x < 0.0 || y < 0.0 || x > size.first || y > size.second) return false
        val point = SvgPoint(x, y)
        return roomRegions.any { it.floor == floor && contains(point, it.polygon) } ||
            rooms.asSequence()
                .filter { it.floor == floor }
                .flatMap { roomPolygons(it).asSequence() }
                .any { contains(point, it) } ||
            circulation[floor].orEmpty().any { contains(point, it) }
    }

    /** Возвращает ключ сайта, чтобы ручной старт использовал дверь именно того помещения. */
    fun siteRoomKeyForAppRoom(roomId: String): String? = appRoomSiteIds[roomId]?.firstOrNull()

    /** Строит ортогональный путь и те же межэтажные переходы, что и маршрутизатор сайта. */
    fun plan(
        start: MapPoint,
        target: Room,
        rooms: List<Room>,
        transition: Transition,
        startRoomId: String? = null,
        routeSession: Long = 0L,
    ): RoutePlan? {
        if (!target.routeAvailable || target.nodeId.isNullOrBlank()) return null
        if (!isInsideCollege(start.floor, start.x.toDouble(), start.y.toDouble(), rooms)) return null

        val targetKey = siteRoomKeyAtTarget(target) ?: return null
        val from = endpointForManual(start, rooms, startRoomId) ?: return null
        val to = endpointForRoom(targetKey) ?: return null

        val legs: List<Leg>
        val transferKind: String?
        if (from.floor == to.floor) {
            val points = if (from.roomId == targetKey) {
                listOf(point(start.x.toDouble(), start.y.toDouble()))
            } else {
                walk(from, to)
            }
            if (points.isEmpty()) return null
            legs = listOf(Leg(from.floor, points))
            transferKind = null
        } else {
            val candidates = transferKeys(transition).mapNotNull { key ->
                val departure = endpointForRoom("$key-${from.floor}") ?: return@mapNotNull null
                val arrival = endpointForRoom("$key-${to.floor}") ?: return@mapNotNull null
                val firstLeg = walk(from, departure)
                val secondLeg = walk(arrival, to)
                if (firstLeg.isEmpty() || secondLeg.isEmpty()) return@mapNotNull null
                Candidate(
                    legs = listOf(Leg(from.floor, firstLeg), Leg(to.floor, secondLeg)),
                    transferKind = key,
                    length = pathLength(firstLeg) + pathLength(secondLeg),
                )
            }
            val best = candidates.minByOrNull(Candidate::length) ?: return null
            legs = best.legs
            transferKind = best.transferKind
        }
        return makeRoutePlan(legs, transferKind, target, routeSession)
    }

    /** Добавляет явную стоимость лифта/лестницы, чтобы инструкция и прогресс совпадали с линией. */
    fun graphWithRouteTransitions(graph: NavigationData, route: List<NavNode>): NavigationData {
        val transitions = route.zipWithNext().mapNotNull { (from, to) ->
            if (from.floor == to.floor) return@mapNotNull null
            val isElevator = from.type.equals("elevator", ignoreCase = true) ||
                to.type.equals("elevator", ignoreCase = true)
            NavEdge(
                from = from.id,
                to = to.id,
                weight = FLOOR_TRANSITION_METERS,
                type = if (isElevator) "elevator" else "stairs",
            )
        }
        return if (transitions.isEmpty()) graph else graph.copy(edges = graph.edges + transitions)
    }

    private fun endpointForManual(mapPoint: MapPoint, rooms: List<Room>, preferredRoomId: String?): Endpoint? {
        val startPoint = point(mapPoint.x.toDouble(), mapPoint.y.toDouble())
        val roomKey = roomKeyAtPoint(mapPoint.floor, startPoint, rooms, preferredRoomId)
        if (roomKey != null) {
            val roomEndpoint = endpointForRoom(roomKey) ?: return null
            val door = roomAccess[roomKey]?.door ?: return null
            val via = if (roomEndpoint.branch == Branch.WEST) {
                point(door.x, startPoint.y)
            } else {
                point(startPoint.x, door.y)
            }
            return roomEndpoint.copy(path = cleanPath(listOf(startPoint, via) + roomEndpoint.path))
        }

        if (!isInsideCollege(mapPoint.floor, startPoint.x, startPoint.y, rooms)) return null
        val mainCorridorMaxY = if (mapPoint.floor == 1) 179.0 else 182.0
        val inMain = startPoint.y >= 156.0 && startPoint.y <= mainCorridorMaxY && startPoint.x <= 610.0
        val inWest = startPoint.x in 62.0..86.0 && startPoint.y >= 179.0
        val inHall = mapPoint.floor == 1 && circulation[1].orEmpty().getOrNull(2)?.let { contains(startPoint, it) } == true
        val anchor: SvgPoint
        val branch: Branch
        val path: List<SvgPoint>
        when {
            inMain -> {
                anchor = point(startPoint.x, 168.0)
                branch = Branch.MAIN
                path = cleanPath(listOf(startPoint, anchor))
            }
            inWest -> {
                anchor = point(73.0, startPoint.y)
                branch = Branch.WEST
                path = cleanPath(listOf(startPoint, anchor))
            }
            inHall -> {
                anchor = point(579.0, 168.0)
                branch = Branch.MAIN
                path = cleanPath(listOf(startPoint, point(579.0, startPoint.y), anchor))
            }
            else -> {
                anchor = point(73.0, 168.0)
                branch = Branch.MAIN
                path = cleanPath(listOf(startPoint, point(73.0, startPoint.y), anchor))
            }
        }
        return Endpoint(mapPoint.floor, anchor, branch, path)
    }

    private fun endpointForRoom(roomId: String): Endpoint? {
        val access = roomAccess[roomId] ?: return null
        val anchor = when (access.branch) {
            Branch.WEST -> point(73.0, access.door.y)
            Branch.HALL, Branch.AUDITORIUM -> point(579.0, 168.0)
            Branch.LIBRARY -> point(73.0, 168.0)
            Branch.MAIN -> point(access.door.x, 168.0)
        }
        val path = when (access.branch) {
            Branch.LIBRARY -> listOf(access.door, point(116.0, 126.0), point(73.0, 126.0), anchor)
            Branch.AUDITORIUM -> listOf(access.door, point(579.0, access.door.y), anchor)
            else -> listOf(access.door, anchor)
        }
        val floor = roomRegions.firstOrNull { it.id == roomId }?.floor ?: when {
            roomId.endsWith("-1") -> 1
            roomId.endsWith("-2") -> 2
            else -> return null
        }
        return Endpoint(floor, anchor, if (access.branch == Branch.WEST) Branch.WEST else Branch.MAIN, cleanPath(path), roomId)
    }

    private fun roomKeyAtPoint(
        floor: Int,
        point: SvgPoint,
        rooms: List<Room>,
        preferredRoomId: String?,
    ): String? {
        val regionId = roomRegions.firstOrNull { it.floor == floor && contains(point, it.polygon) }?.id
        val preferredRoom = preferredRoomId
            ?.let { id -> rooms.firstOrNull { it.id == id && it.floor == floor } }
            ?.takeIf { room -> roomPolygons(room).any { contains(point, it) } }
        val actualRoom = preferredRoom ?: rooms.asSequence()
            .filter { it.floor == floor }
            .filter { room -> roomPolygons(room).any { contains(point, it) } }
            .minByOrNull { room -> roomPolygons(room).sumOf(::polygonArea) }
        val appRoomKeys = actualRoom?.let { room ->
            appRoomSiteIds[room.id].orEmpty().filter(roomAccess::containsKey)
        }.orEmpty()
        if (regionId != null && regionId in appRoomKeys) return regionId
        return appRoomKeys.firstOrNull() ?: regionId
    }

    private fun siteRoomKeyAtTarget(room: Room): String? {
        val validKeys = appRoomSiteIds[room.id].orEmpty().filter(roomAccess::containsKey)
        if (validKeys.isEmpty()) return null
        val x = room.centerX ?: room.x
        val y = room.centerY ?: room.y
        if (x != null && y != null && x.isFinite() && y.isFinite()) {
            val regionId = roomRegions.firstOrNull { it.floor == room.floor && contains(point(x, y), it.polygon) }?.id
            if (regionId != null && regionId in validKeys) return regionId
        }
        return validKeys.firstOrNull()
    }

    private fun walk(from: Endpoint, to: Endpoint): List<SvgPoint> {
        if (from.floor != to.floor) return emptyList()
        val middle = if (from.branch != to.branch) listOf(point(73.0, 168.0)) else emptyList()
        return cleanPath(from.path + middle + to.path.asReversed())
    }

    private fun makeRoutePlan(
        legs: List<Leg>,
        transferKind: String?,
        target: Room,
        routeSession: Long,
    ): RoutePlan {
        val floorPoints = linkedMapOf<Int, List<SvgPoint>>()
        legs.forEach { leg -> floorPoints[leg.floor] = leg.points }
        val nodes = buildList {
            var index = 0
            legs.forEachIndexed { legIndex, leg ->
                leg.points.forEachIndexed { pointIndex, point ->
                    val isTransferEndpoint = transferKind != null && when (legIndex) {
                        0 -> pointIndex == leg.points.lastIndex
                        else -> pointIndex == 0
                    }
                    val isArrivalDoor = legIndex == legs.lastIndex && pointIndex == leg.points.lastIndex &&
                        leg.points.size > 1 && !isTransferEndpoint
                    val type = when {
                        isTransferEndpoint && transferKind == "lift" -> "elevator"
                        isTransferEndpoint -> "stairs"
                        isArrivalDoor -> "door"
                        else -> "hallway"
                    }
                    add(
                        NavNode(
                            id = "site-route-$routeSession-$index",
                            floor = leg.floor,
                            x = point.x,
                            y = point.y,
                            name = if (isArrivalDoor) target.name else "",
                            type = type,
                            nameEn = if (isArrivalDoor) target.nameEn else null,
                            connectsRoom = target.id.takeIf { isArrivalDoor },
                            roomId = target.id.takeIf { isArrivalDoor },
                            stairsGroup = transferKind?.takeIf { isTransferEndpoint && it != "lift" },
                            elevatorGroup = transferKind?.takeIf { isTransferEndpoint && it == "lift" },
                        ),
                    )
                    index++
                }
            }
        }
        return RoutePlan(nodes, floorPoints, transferKind)
    }

    private fun transferKeys(transition: Transition): List<String> = when (transition) {
        Transition.LIFT -> listOf("lift")
        Transition.STAIRS -> STAIR_KEYS
        Transition.ANY -> STAIR_KEYS + "lift"
    }

    private val STAIR_KEYS = listOf("stairs-west", "stairs-middle", "stairs-north")

    private fun pathLength(points: List<SvgPoint>): Double = points.zipWithNext().sumOf { (from, to) ->
        hypot(to.x - from.x, to.y - from.y)
    }

    private fun cleanPath(points: List<SvgPoint>): List<SvgPoint> {
        val unique = points.filterIndexed { index, point -> index == 0 || !samePoint(point, points[index - 1]) }
        if (unique.size < 3) return unique
        return unique.filterIndexed { index, point ->
            if (index == 0 || index == unique.lastIndex) return@filterIndexed true
            val previous = unique[index - 1]
            val next = unique[index + 1]
            (point.x - previous.x) * (next.y - point.y) != (point.y - previous.y) * (next.x - point.x)
        }
    }

    private fun samePoint(first: SvgPoint, second: SvgPoint): Boolean = first.x == second.x && first.y == second.y

    private fun roomPolygons(room: Room): List<List<SvgPoint>> = buildList {
        room.polygon.takeIf { it.size >= 3 }?.let(::add)
        room.polygons.filter { it.size >= 3 }.forEach(::add)
    }

    private fun contains(point: SvgPoint, polygon: List<SvgPoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var previous = polygon.last()
        for (current in polygon) {
            val cross = (point.x - previous.x) * (current.y - previous.y) -
                (point.y - previous.y) * (current.x - previous.x)
            if (abs(cross) < POLYGON_EPSILON &&
                point.x >= minOf(previous.x, current.x) && point.x <= maxOf(previous.x, current.x) &&
                point.y >= minOf(previous.y, current.y) && point.y <= maxOf(previous.y, current.y)
            ) return true
            if ((current.y > point.y) != (previous.y > point.y) &&
                point.x < (previous.x - current.x) * (point.y - current.y) / (previous.y - current.y) + current.x
            ) inside = !inside
            previous = current
        }
        return inside
    }

    private fun polygonArea(polygon: List<SvgPoint>): Double = abs(polygon.indices.sumOf { index ->
        val current = polygon[index]
        val next = polygon[(index + 1) % polygon.size]
        current.x * next.y - next.x * current.y
    }) / 2.0

    private fun rect(id: String, floor: Int, x: Double, y: Double, width: Double, height: Double) =
        RoomRegion(id, floor, listOf(point(x, y), point(x + width, y), point(x + width, y + height), point(x, y + height)))

    private fun access(x: Double, y: Double, branch: Branch = Branch.MAIN) = RoomAccess(point(x, y), branch)

    private fun polygon(vararg coordinates: Double): List<SvgPoint> = coordinates.toList()
        .chunked(2)
        .map { (x, y) -> point(x, y) }

    private fun point(x: Double, y: Double) = SvgPoint(x, y)

    private const val POLYGON_EPSILON = 1e-6
    private const val FLOOR_TRANSITION_METERS = 4.0
}
