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
        assertEquals(10.0, steps[1].distanceMeters, 0.0001)
        assertEquals(2f / 3f, steps[1].progress, 0.0001f)
        assertEquals(1f, steps.last().progress, 0.0001f)
    }
}
