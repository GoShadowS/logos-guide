package com.logos.navigator.navigation.pathfinding

import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.NavEdge
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.RoutePreference
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.hypot

/** A* для сотен и тысяч узлов; рёбра по умолчанию рассматриваются как двусторонние. */
object AStarPathfinder {
    private data class QueueEntry(val nodeId: String, val distanceFromStart: Double, val priority: Double)
    private data class Neighbour(val nodeId: String, val edge: NavEdge)

    /** Возвращает кратчайший путь, включая начальный и конечный узлы, либо null. */
    fun findPath(
        graph: NavigationData,
        startId: String,
        goalId: String,
        preference: RoutePreference = RoutePreference.SHORTEST,
        useElevator: Boolean = false,
    ): List<NavNode>? {
        val nodesById = graph.nodes.associateBy(NavNode::id)
        val start = nodesById[startId] ?: return null
        val goal = nodesById[goalId] ?: return null
        if (startId == goalId) return listOf(start)

        val validEdges = validEdges(graph, nodesById, useElevator)
        val adjacency = HashMap<String, MutableList<Neighbour>>(graph.nodes.size)
        validEdges.forEach { edge ->
            adjacency.getOrPut(edge.from) { mutableListOf() }.add(Neighbour(edge.to, edge))
            if (edge.bidirectional != false) {
                adjacency.getOrPut(edge.to) { mutableListOf() }.add(Neighbour(edge.from, edge))
            }
        }

        val floorScale = buildAdmissibleFloorScales(graph.floors, validEdges, nodesById)
        val bestDistance = HashMap<String, Double>(graph.nodes.size)
        val cameFrom = HashMap<String, String>(graph.nodes.size)
        val openQueue = PriorityQueue<QueueEntry>(compareBy<QueueEntry> { it.priority }
            .thenBy { it.distanceFromStart })

        bestDistance[startId] = 0.0
        openQueue.add(QueueEntry(startId, 0.0, heuristic(start, goal, floorScale)))

        while (openQueue.isNotEmpty()) {
            val current = openQueue.poll()
            val knownDistance = bestDistance[current.nodeId] ?: continue
            // Удаляем из очереди устаревшие записи, не выполняя лишний обход.
            if (current.distanceFromStart > knownDistance + EPSILON) continue
            if (current.nodeId == goalId) return reconstruct(cameFrom, nodesById, goalId, startId)

            adjacency[current.nodeId].orEmpty().forEach { neighbour ->
                val candidate = knownDistance + effectiveWeight(neighbour.edge, preference, nodesById)
                val oldDistance = bestDistance[neighbour.nodeId] ?: Double.POSITIVE_INFINITY
                if (candidate + EPSILON < oldDistance) {
                    val next = nodesById[neighbour.nodeId]
                    if (next != null) {
                        bestDistance[neighbour.nodeId] = candidate
                        cameFrom[neighbour.nodeId] = current.nodeId
                        openQueue.add(
                            QueueEntry(
                                nodeId = neighbour.nodeId,
                                distanceFromStart = candidate,
                                priority = candidate + heuristic(next, goal, floorScale),
                            ),
                        )
                    }
                }
            }
        }
        return null
    }

    /** Стоимость уже найденного пути с теми же ограничениями и предпочтениями, что применяет A*. */
    fun pathCost(
        graph: NavigationData,
        path: List<NavNode>,
        preference: RoutePreference = RoutePreference.SHORTEST,
        useElevator: Boolean = false,
    ): Double {
        if (path.size < 2) return 0.0
        val nodesById = graph.nodes.associateBy(NavNode::id)
        val validEdges = validEdges(graph, nodesById, useElevator)
        var totalCost = 0.0
        for (index in 0 until path.lastIndex) {
            val from = path[index]
            val to = path[index + 1]
            val edgeCost = validEdges.asSequence()
                .filter {
                    (it.from == from.id && it.to == to.id) ||
                        (it.bidirectional != false && it.from == to.id && it.to == from.id)
                }
                .minOfOrNull { effectiveWeight(it, preference, nodesById) }
                ?: return Double.POSITIVE_INFINITY
            totalCost += edgeCost
        }
        return totalCost
    }

    /** Ближайшая точка дорожного графа на выбранном этаже к ручной/GPS-точке. */
    fun nearestNode(graph: NavigationData, floor: Int, x: Double, y: Double): NavNode? {
        if (!x.isFinite() || !y.isFinite()) return null
        return graph.nodes.asSequence()
            .filter { it.floor == floor && it.x.isFinite() && it.y.isFinite() }
            .minByOrNull { node -> hypot(node.x - x, node.y - y) }
    }

    private fun validEdges(
        graph: NavigationData,
        nodesById: Map<String, NavNode>,
        useElevator: Boolean,
    ): List<NavEdge> {
        val accessibleFloorPairs = graph.edges.mapNotNull { edge ->
            val from = nodesById[edge.from] ?: return@mapNotNull null
            val to = nodesById[edge.to] ?: return@mapNotNull null
            val isAccessibleTransition = edge.type.equals("elevator", ignoreCase = true) || edge.requiresAccessibility == true
            if (isAccessibleTransition && from.floor != to.floor) setOf(from.floor, to.floor) else null
        }.toSet()
        return graph.edges.filter { edge ->
            val from = nodesById[edge.from]
            val to = nodesById[edge.to]
            val isElevatorEdge = edge.type.equals("elevator", ignoreCase = true) || edge.requiresAccessibility == true
            val elevatorReplacesStairs = useElevator && edge.type.equals("stairs", ignoreCase = true) &&
                from != null && to != null && from.floor != to.floor && setOf(from.floor, to.floor) in accessibleFloorPairs
            (useElevator || !isElevatorEdge) && !elevatorReplacesStairs && edge.weight.isFinite() && edge.weight > 0.0 &&
                edge.from != edge.to && edge.from in nodesById && edge.to in nodesById
        }
    }

    private fun effectiveWeight(
        edge: NavEdge,
        preference: RoutePreference,
        nodesById: Map<String, NavNode>,
    ): Double {
        if (preference == RoutePreference.SHORTEST) return edge.weight
        val type = edge.type.lowercase()
        val from = nodesById[edge.from]
        val to = nodesById[edge.to]
        val penalty = when (preference) {
            RoutePreference.SHORTEST -> 1.0
            RoutePreference.AVOID_STAIRS -> if (type == "stairs" || from?.type == "stairs" || to?.type == "stairs") STAIR_AVOIDANCE_FACTOR else 1.0
            RoutePreference.AVOID_ELEVATOR -> if (type == "elevator" || from?.type == "elevator" || to?.type == "elevator") ELEVATOR_AVOIDANCE_FACTOR else 1.0
        }
        return edge.weight * penalty
    }

    private data class FloorHeuristicScale(
        val metersPerUnit: Map<Int, Double>,
        val metersPerFloor: Double,
    )

    /**
     * Ограничиваем евклидову оценку длиной рёбер графа. Если демонстрационный вес ребра
     * меньше длины в SVG units, масштаб уменьшается — эвристика остаётся оптимистичной.
     * Межэтажная часть также не превышает минимальную стоимость заданного перехода.
     */
    private fun buildAdmissibleFloorScales(
        floors: List<FloorPlan>,
        edges: List<NavEdge>,
        nodesById: Map<String, NavNode>,
    ): FloorHeuristicScale {
        val minimumInterfloorWeight = edges.asSequence().mapNotNull { edge ->
            val from = nodesById[edge.from] ?: return@mapNotNull null
            val to = nodesById[edge.to] ?: return@mapNotNull null
            if (from.floor == to.floor) null else edge.weight
        }.minOrNull()

        val horizontal = floors.associate { floor ->
            val ratios = edges.asSequence().mapNotNull { edge ->
                val from = nodesById[edge.from] ?: return@mapNotNull null
                val to = nodesById[edge.to] ?: return@mapNotNull null
                if (from.floor != floor.number || to.floor != floor.number) return@mapNotNull null
                val unitDistance = hypot(from.x - to.x, from.y - to.y)
                if (unitDistance <= EPSILON) null else edge.weight / unitDistance
            }
            val baseScale = floor.metersPerUnit.takeIf { it.isFinite() && it > 0.0 }
                ?: DEFAULT_METERS_PER_UNIT
            val sameFloorScale = minOf(baseScale, ratios.minOrNull() ?: baseScale)
            // Ограничение сохраняет оптимистичность и при обходном пути через другой этаж.
            val floorDiameter = hypot(floor.width.toDouble(), floor.height.toDouble())
            val crossFloorSafeScale = if (minimumInterfloorWeight != null && floorDiameter > EPSILON) {
                minimumInterfloorWeight / floorDiameter
            } else {
                sameFloorScale
            }
            floor.number to minOf(sameFloorScale, crossFloorSafeScale)
        }
        val verticalRatios = edges.asSequence().mapNotNull { edge ->
            val from = nodesById[edge.from] ?: return@mapNotNull null
            val to = nodesById[edge.to] ?: return@mapNotNull null
            val floorDifference = abs(from.floor - to.floor)
            if (floorDifference == 0) null else edge.weight / floorDifference
        }
        val vertical = minOf(DEFAULT_METERS_PER_FLOOR, verticalRatios.minOrNull() ?: 0.0)
        return FloorHeuristicScale(horizontal, vertical)
    }

    private fun heuristic(from: NavNode, goal: NavNode, scale: FloorHeuristicScale): Double {
        val verticalLowerBound = abs(from.floor - goal.floor) * scale.metersPerFloor
        // Координаты разных SVG viewBox сравниваются только внутри одного этажа.
        val horizontalLowerBound = if (from.floor == goal.floor) {
            hypot(from.x - goal.x, from.y - goal.y) *
                (scale.metersPerUnit[from.floor] ?: DEFAULT_METERS_PER_UNIT)
        } else {
            0.0
        }
        return horizontalLowerBound + verticalLowerBound
    }

    private fun reconstruct(
        cameFrom: Map<String, String>,
        nodesById: Map<String, NavNode>,
        goalId: String,
        startId: String,
    ): List<NavNode>? {
        val reversed = ArrayList<NavNode>()
        var cursor = goalId
        while (true) {
            reversed += nodesById[cursor] ?: return null
            if (cursor == startId) break
            cursor = cameFrom[cursor] ?: return null
        }
        reversed.reverse()
        return reversed
    }

    private const val EPSILON = 1e-9
    private const val DEFAULT_METERS_PER_UNIT = 0.02
    private const val DEFAULT_METERS_PER_FLOOR = 3.0
    private const val STAIR_AVOIDANCE_FACTOR = 2.5
    private const val ELEVATOR_AVOIDANCE_FACTOR = 2.5
}
