package com.logos.navigator.navigation

import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.NavEdge
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.RouteAction
import com.logos.navigator.data.model.RouteInstruction
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Создаёт последовательность понятных действий из A*-пути и весов рёбер в метрах. */
object RouteStepGenerator {
    fun generate(
        graph: NavigationData,
        path: List<NavNode>,
        destinationName: String,
        totalDistanceMeters: Double,
    ): List<RouteInstruction> {
        if (path.isEmpty()) return emptyList()
        val steps = mutableListOf<RouteInstruction>()
        val edgeByPair = graph.edges.associateBy { directedKey(it.from, it.to) }
        var completedMeters = 0.0
        var straightMeters = 0.0
        var straightTarget: NavNode? = null
        val total = totalDistanceMeters.takeIf { it.isFinite() && it > 0.0 } ?: 1.0

        fun progress() = (completedMeters / total).toFloat().coerceIn(0f, 0.99f)
        fun flushStraight() {
            if (straightMeters <= 0.05) return
            steps += RouteInstruction(
                action = RouteAction.STRAIGHT,
                distanceMeters = straightMeters,
                targetNodeId = straightTarget?.id,
                progress = progress(),
            )
            straightMeters = 0.0
            straightTarget = null
        }

        steps += RouteInstruction(action = RouteAction.START, targetNodeId = path.first().id, progress = 0f)

        for (index in 0 until path.lastIndex) {
            val from = path[index]
            val to = path[index + 1]
            val edge = edgeByPair[directedKey(from.id, to.id)]
                ?: graph.edges.firstOrNull { it.bidirectional != false && it.from == to.id && it.to == from.id }
            val metersPerUnit = graph.floors.firstOrNull { it.number == from.floor }?.metersPerUnit ?: DEFAULT_METERS_PER_UNIT
            val edgeMeters = edge?.weight?.takeIf { it.isFinite() && it > 0.0 }
                ?: hypot(to.x - from.x, to.y - from.y) * metersPerUnit

            if (index > 0 && from.floor == to.floor && !from.type.isTransitionNode()) {
                val turnAction = classifyTurn(path[index - 1], from, to)
                if (turnAction != null) {
                    flushStraight()
                    steps += RouteInstruction(action = turnAction, targetNodeId = from.id, progress = progress())
                }
            }

            val transition = classifyTransition(from, to, edge)
            if (transition != null) {
                flushStraight()
                completedMeters += edgeMeters
                steps += RouteInstruction(
                    action = transition,
                    distanceMeters = edgeMeters,
                    targetFloor = to.floor.takeIf { it != from.floor },
                    targetNodeId = to.id,
                    progress = progress(),
                )
                continue
            }

            if (to.type.equals("door", ignoreCase = true)) {
                flushStraight()
                completedMeters += edgeMeters
                steps += RouteInstruction(
                    action = RouteAction.DOOR,
                    targetNodeId = to.id,
                    destinationName = destinationName,
                    progress = progress(),
                )
            } else {
                straightMeters += edgeMeters
                completedMeters += edgeMeters
                straightTarget = to
            }
        }
        flushStraight()
        steps += RouteInstruction(
            action = RouteAction.ARRIVAL,
            destinationName = destinationName,
            targetNodeId = path.last().id,
            progress = 1f,
        )
        return steps
    }

    private fun classifyTransition(from: NavNode, to: NavNode, edge: NavEdge?): RouteAction? {
        val edgeType = edge?.type.orEmpty()
        val fromType = from.type
        val toType = to.type
        if (from.floor == to.floor) return null
        val isElevator = edgeType.equals("elevator", true) || fromType.equals("elevator", true) || toType.equals("elevator", true)
        if (isElevator) return RouteAction.ELEVATOR
        return if (to.floor > from.floor) RouteAction.STAIRS_UP else RouteAction.STAIRS_DOWN
    }

    private fun classifyTurn(previous: NavNode, current: NavNode, next: NavNode): RouteAction? {
        val incomingX = current.x - previous.x
        val incomingY = current.y - previous.y
        val outgoingX = next.x - current.x
        val outgoingY = next.y - current.y
        if (hypot(incomingX, incomingY) < MIN_SEGMENT || hypot(outgoingX, outgoingY) < MIN_SEGMENT) return null
        // SVG-координаты растут вниз по экрану: положительный угол означает поворот направо.
        val cross = incomingX * outgoingY - incomingY * outgoingX
        val dot = incomingX * outgoingX + incomingY * outgoingY
        val degrees = Math.toDegrees(atan2(cross, dot)).toFloat()
        val magnitude = abs(degrees)
        return when {
            magnitude < STRAIGHT_ANGLE_DEGREES -> null
            magnitude >= U_TURN_ANGLE_DEGREES -> RouteAction.U_TURN
            magnitude < SLIGHT_TURN_MAX_DEGREES -> if (degrees > 0) RouteAction.SLIGHT_RIGHT else RouteAction.SLIGHT_LEFT
            else -> if (degrees > 0) RouteAction.TURN_RIGHT else RouteAction.TURN_LEFT
        }
    }

    private fun String.isTransitionNode(): Boolean =
        equals("stairs", true) || equals("elevator", true) || equals("door", true)

    private fun directedKey(from: String, to: String): String = "$from->$to"

    private const val DEFAULT_METERS_PER_UNIT = 0.083333
    private const val MIN_SEGMENT = 0.01
    private const val STRAIGHT_ANGLE_DEGREES = 18f
    private const val SLIGHT_TURN_MAX_DEGREES = 45f
    private const val U_TURN_ANGLE_DEGREES = 155f
}
