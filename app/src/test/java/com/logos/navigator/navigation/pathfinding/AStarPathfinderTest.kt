package com.logos.navigator.navigation.pathfinding

import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.NavEdge
import com.logos.navigator.data.model.NavNode
import com.logos.navigator.data.model.RoutePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AStarPathfinderTest {
    @Test
    fun returnsCheapestWalkableRoute() {
        val graph = graph(
            nodes = listOf(
                node("start", 1, 0.0, 0.0),
                node("long", 1, 50.0, 0.0),
                node("short", 1, 0.0, 20.0),
                node("goal", 1, 50.0, 20.0),
            ),
            edges = listOf(
                edge("start", "long", 10.0),
                edge("long", "goal", 10.0),
                edge("start", "short", 2.0),
                edge("short", "goal", 3.0),
            ),
        )

        assertEquals(listOf("start", "short", "goal"), AStarPathfinder.findPath(graph, "start", "goal")?.map { it.id })
    }

    @Test
    fun avoidsStairsWhenAlternativeRouteIsReasonable() {
        val graph = graph(
            nodes = listOf(
                node("start", 1, 0.0, 0.0),
                node("stairs", 1, 5.0, 0.0, type = "stairs"),
                node("hall", 1, 0.0, 5.0),
                node("goal", 1, 10.0, 0.0),
            ),
            edges = listOf(
                edge("start", "stairs", 1.0),
                edge("stairs", "goal", 1.0),
                edge("start", "hall", 2.2),
                edge("hall", "goal", 2.2),
            ),
        )

        assertEquals(
            listOf("start", "hall", "goal"),
            AStarPathfinder.findPath(graph, "start", "goal", RoutePreference.AVOID_STAIRS)?.map { it.id },
        )
    }

    @Test
    fun connectsDifferentFloorsAcrossInterfloorEdge() {
        val graph = graph(
            nodes = listOf(
                node("ground", 1, 10.0, 10.0, "Вход", "entrance"),
                node("stairs1", 1, 50.0, 50.0, "Лестница", "stairs"),
                node("stairs2", 2, 50.0, 50.0, "Лестница", "stairs"),
                node("room2", 2, 90.0, 50.0, "Кабинет", "hallway"),
            ),
            edges = listOf(
                edge("ground", "stairs1", 6.0),
                edge("stairs1", "stairs2", 14.0, "stairs"),
                edge("stairs2", "room2", 4.0),
            ),
        )

        assertEquals(listOf("ground", "stairs1", "stairs2", "room2"), AStarPathfinder.findPath(graph, "ground", "room2")?.map { it.id })
    }

    @Test
    fun accessiblePreferenceUsesElevatorAndKeepsDefaultRoutesOnStairs() {
        val graph = graph(
            nodes = listOf(
                node("start", 1, 0.0, 0.0),
                node("stairs1", 1, 1.0, 0.0, type = "stairs"),
                node("lift1", 1, 0.0, 1.0, type = "elevator"),
                node("stairs2", 2, 1.0, 0.0, type = "stairs"),
                node("lift2", 2, 0.0, 1.0, type = "elevator"),
                node("goal", 2, 2.0, 1.0),
            ),
            edges = listOf(
                edge("start", "stairs1", 1.0),
                edge("stairs1", "stairs2", 3.0, "stairs"),
                edge("stairs2", "goal", 1.0),
                edge("start", "lift1", 1.0),
                NavEdge("lift1", "lift2", 3.0, "elevator", requiresAccessibility = true),
                edge("lift2", "goal", 1.0),
            ),
        )

        assertEquals(
            listOf("start", "stairs1", "stairs2", "goal"),
            AStarPathfinder.findPath(graph, "start", "goal")?.map { it.id },
        )
        assertEquals(
            listOf("start", "lift1", "lift2", "goal"),
            AStarPathfinder.findPath(graph, "start", "goal", useElevator = true)?.map { it.id },
        )
    }

    @Test
    fun returnsNullWhenDestinationIsDisconnected() {
        val graph = graph(
            nodes = listOf(node("start", 1, 0.0, 0.0), node("goal", 1, 100.0, 100.0)),
            edges = emptyList(),
        )

        assertNull(AStarPathfinder.findPath(graph, "start", "goal"))
    }

    @Test
    fun respectsOneWayEdges() {
        val graph = graph(
            nodes = listOf(node("start", 1, 0.0, 0.0), node("middle", 1, 10.0, 0.0), node("goal", 1, 20.0, 0.0)),
            edges = listOf(
                edge("start", "middle", 2.0, bidirectional = false),
                edge("middle", "goal", 3.0, bidirectional = false),
            ),
        )

        assertEquals(listOf("start", "middle", "goal"), AStarPathfinder.findPath(graph, "start", "goal")?.map { it.id })
        assertNull(AStarPathfinder.findPath(graph, "goal", "start"))
    }

    @Test
    fun nearestNodeUsesOnlyRequestedFloorAndRejectsInvalidCoordinates() {
        val graph = graph(
            nodes = listOf(
                node("ground", 1, 0.0, 0.0),
                node("upper", 2, 100.0, 100.0),
            ),
            edges = emptyList(),
        )

        assertEquals("ground", AStarPathfinder.nearestNode(graph, 1, 99.0, 99.0)?.id)
        assertNull(AStarPathfinder.nearestNode(graph, 3, 0.0, 0.0))
        assertNull(AStarPathfinder.nearestNode(graph, 1, Double.NaN, 0.0))
    }

    private fun graph(nodes: List<NavNode>, edges: List<NavEdge>) = NavigationData(
        floors = listOf(
            FloorPlan(1, "1 этаж", "floors/floor_1.svg", 1000, 1000, 0.02),
            FloorPlan(2, "2 этаж", "floors/floor_2.svg", 1000, 1000, 0.02),
        ),
        nodes = nodes,
        edges = edges,
    )

    private fun node(id: String, floor: Int, x: Double, y: Double, name: String = "", type: String = "hallway") =
        NavNode(id = id, floor = floor, x = x, y = y, name = name, type = type)

    private fun edge(
        from: String,
        to: String,
        weight: Double,
        type: String = "corridor",
        bidirectional: Boolean = true,
    ) = NavEdge(from = from, to = to, weight = weight, type = type, bidirectional = bidirectional)
}
