package com.logos.navigator.navigation

import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.NavEdge
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.RouteAction
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteStepGeneratorTest {
    @Test
    fun instructionProgressIncludesStartConnectorAndArrivalCompletesRoute() {
        val start = NavNode(id = "start", floor = 1, x = 0.0, y = 0.0)
        val goal = NavNode(id = "goal", floor = 1, x = 10.0, y = 0.0)
        val graph = NavigationData(
            floors = listOf(FloorPlan(1, "1 этаж", "floors/floor_1.svg", 100, 100, 1.0)),
            nodes = listOf(start, goal),
            edges = listOf(NavEdge("start", "goal", 10.0)),
        )

        val steps = RouteStepGenerator.generate(
            graph = graph,
            path = listOf(start, goal),
            destinationName = "Назначение",
            totalDistanceMeters = 30.0,
            startConnectorMeters = 10.0,
        )

        assertEquals(listOf(RouteAction.START, RouteAction.STRAIGHT, RouteAction.ARRIVAL), steps.map { it.action })
        assertEquals(listOf("start", "goal", "goal"), steps.map { it.targetNodeId })
        assertEquals(10.0, steps[1].distanceMeters, 0.0001)
        // Прогресс шага — точка, где он начинается: прямой участок стартует после 10 м подхода.
        assertEquals(1f / 3f, steps[1].progress, 0.0001f)
        assertEquals(1f, steps.last().progress, 0.0001f)
    }

    @Test
    fun turnIsAnnouncedShortlyBeforeTheCorner() {
        val a = NavNode(id = "a", floor = 1, x = 0.0, y = 0.0)
        val b = NavNode(id = "b", floor = 1, x = 20.0, y = 0.0)
        val c = NavNode(id = "c", floor = 1, x = 20.0, y = 20.0)
        val graph = NavigationData(
            floors = listOf(FloorPlan(1, "1 этаж", "floors/floor_1.svg", 100, 100, 1.0)),
            nodes = listOf(a, b, c),
            edges = listOf(NavEdge("a", "b", 20.0), NavEdge("b", "c", 20.0)),
        )
        val steps = RouteStepGenerator.generate(graph, listOf(a, b, c), "Цель", totalDistanceMeters = 40.0)

        assertEquals(
            listOf(RouteAction.START, RouteAction.STRAIGHT, RouteAction.TURN_RIGHT, RouteAction.STRAIGHT, RouteAction.ARRIVAL),
            steps.map { it.action },
        )
        // В начале — первый прямой участок.
        assertEquals(1, RouteStepGenerator.activeStepIndex(steps, progress = 0.1f, totalDistanceMeters = 40.0))
        // За 2 м до поворота показываем сам поворот.
        assertEquals(2, RouteStepGenerator.activeStepIndex(steps, progress = 0.45f, totalDistanceMeters = 40.0))
        // После поворота — второй прямой участок, у цели — прибытие.
        assertEquals(3, RouteStepGenerator.activeStepIndex(steps, progress = 0.6f, totalDistanceMeters = 40.0))
        assertEquals(4, RouteStepGenerator.activeStepIndex(steps, progress = 1f, totalDistanceMeters = 40.0))
    }
}
