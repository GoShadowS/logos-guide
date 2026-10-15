package com.logos.navigator.navigation

import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.NavEdge
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.Room
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RouteProgressCalculatorTest {
    @Test
    fun progressIncludesStartAndDestinationConnectors() {
        val graph = graph(
            nodes = listOf(
                node("start", 1, 0.0, 0.0),
                node("goal", 1, 10.0, 0.0),
            ),
            edges = listOf(NavEdge("start", "goal", 10.0)),
        )
        val route = graph.nodes
        val origin = MapPoint(floor = 1, x = 0f, y = -10f)
        val destination = room(floor = 1, x = 10.0, y = 10.0)

        val start = RouteProgressCalculator.project(graph, route, origin, destination, origin)
        val atGraphStart = RouteProgressCalculator.project(graph, route, origin, destination, MapPoint(1, 0f, 0f))
        val onGraph = RouteProgressCalculator.project(graph, route, origin, destination, MapPoint(1, 5f, 0f))
        val atGraphEnd = RouteProgressCalculator.project(graph, route, origin, destination, MapPoint(1, 10f, 0f))
        val atDestination = RouteProgressCalculator.project(graph, route, origin, destination, MapPoint(1, 10f, 10f))

        assertNotNull(start)
        assertNotNull(atGraphStart)
        assertNotNull(onGraph)
        assertNotNull(atGraphEnd)
        assertNotNull(atDestination)
        assertEquals(0f, start!!.progress, 0.0001f)
        assertEquals(1f / 3f, atGraphStart!!.progress, 0.0001f)
        assertEquals(0.5f, onGraph!!.progress, 0.0001f)
        assertEquals(2f / 3f, atGraphEnd!!.progress, 0.0001f)
        assertEquals(1f, atDestination!!.progress, 0.0001f)
        assertEquals(30.0, atDestination.totalMeters, 0.0001)
    }

    @Test
    fun progressCarriesInterfloorDistanceAcrossTheTransition() {
        val graph = graph(
            nodes = listOf(
                node("start", 1, 0.0, 0.0),
                node("stairs1", 1, 10.0, 0.0, "stairs"),
                node("stairs2", 2, 10.0, 0.0, "stairs"),
                node("goal", 2, 20.0, 0.0),
            ),
            edges = listOf(
                NavEdge("start", "stairs1", 10.0),
                NavEdge("stairs1", "stairs2", 4.0, "stairs"),
                NavEdge("stairs2", "goal", 10.0),
            ),
        )
        val route = graph.nodes
        val origin = MapPoint(floor = 1, x = 0f, y = 3f)
        val destination = room(floor = 2, x = 23.0, y = 0.0)

        val atUpperLanding = RouteProgressCalculator.project(graph, route, origin, destination, MapPoint(2, 10f, 0f))
        val atDestination = RouteProgressCalculator.project(graph, route, origin, destination, MapPoint(2, 23f, 0f))

        assertNotNull(atUpperLanding)
        assertNotNull(atDestination)
        assertEquals(17f / 30f, atUpperLanding!!.progress, 0.0001f)
        assertEquals(1f, atDestination!!.progress, 0.0001f)
    }

    private fun graph(nodes: List<NavNode>, edges: List<NavEdge>) = NavigationData(
        floors = listOf(
            FloorPlan(1, "1 этаж", "floors/floor_1.svg", 100, 100, 1.0),
            FloorPlan(2, "2 этаж", "floors/floor_2.svg", 100, 100, 1.0),
        ),
        nodes = nodes,
        edges = edges,
    )

    private fun node(id: String, floor: Int, x: Double, y: Double, type: String = "hallway") =
        NavNode(id = id, floor = floor, x = x, y = y, name = id, type = type)

    private fun room(floor: Int, x: Double, y: Double) = Room(
        id = "destination",
        name = "Destination",
        nameEn = "Destination",
        number = null,
        floor = floor,
        category = "classroom",
        x = x,
        y = y,
        routeAvailable = true,
    )
}
