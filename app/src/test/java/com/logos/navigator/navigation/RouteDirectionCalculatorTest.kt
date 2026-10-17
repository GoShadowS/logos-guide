package com.logos.navigator.navigation

import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.NavNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteDirectionCalculatorTest {
    @Test
    fun pointsFromTheStartingLocationToTheFirstRouteNode() {
        val route = listOf(node("door", floor = 1, x = 10.0, y = 0.0), node("hall", floor = 1, x = 20.0, y = 0.0))

        assertEquals(
            MapPoint(floor = 1, x = 10f, y = 0f),
            RouteDirectionCalculator.nextTarget(route, origin = MapPoint(1, 0f, 0f), position = MapPoint(1, 0f, 0f)),
        )
    }

    @Test
    fun keepsPointingToTheFirstRouteNodeAlongTheStartConnector() {
        val route = listOf(node("door", floor = 1, x = 10.0, y = 0.0), node("hall", floor = 1, x = 20.0, y = 0.0))

        assertEquals(
            MapPoint(floor = 1, x = 10f, y = 0f),
            RouteDirectionCalculator.nextTarget(route, origin = MapPoint(1, 0f, 0f), position = MapPoint(1, 4f, 1f)),
        )
    }

    @Test
    fun pointsToTheEndOfTheCurrentRouteSegment() {
        val route = listOf(
            node("a", floor = 1, x = 0.0, y = 0.0),
            node("b", floor = 1, x = 10.0, y = 0.0),
            node("c", floor = 1, x = 10.0, y = 10.0),
        )

        assertEquals(
            MapPoint(floor = 1, x = 10f, y = 0f),
            RouteDirectionCalculator.nextTarget(route, origin = null, position = MapPoint(1, 5f, 0f)),
        )
    }

    @Test
    fun pointsAheadWhenPositionIsAlreadyAtARouteWaypoint() {
        val route = listOf(
            node("a", floor = 1, x = 0.0, y = 0.0),
            node("b", floor = 1, x = 10.0, y = 0.0),
            node("c", floor = 1, x = 10.0, y = 10.0),
        )

        assertEquals(
            MapPoint(floor = 1, x = 10f, y = 10f),
            RouteDirectionCalculator.nextTarget(route, origin = null, position = MapPoint(1, 10f, 0f)),
        )
    }

    @Test
    fun doesNotPointAcrossFloorTransitionsOrWhenNoRouteRemainsOnCurrentFloor() {
        val route = listOf(
            node("a", floor = 1, x = 0.0, y = 0.0),
            node("stair", floor = 1, x = 10.0, y = 0.0),
            node("stair-upper", floor = 2, x = 0.0, y = 0.0),
            node("b", floor = 2, x = 10.0, y = 0.0),
        )

        assertNull(RouteDirectionCalculator.nextTarget(route, origin = null, position = MapPoint(1, 10f, 0f)))
        assertEquals(
            MapPoint(floor = 2, x = 10f, y = 0f),
            RouteDirectionCalculator.nextTarget(route, origin = null, position = MapPoint(2, 0f, 0f)),
        )
    }

    private fun node(id: String, floor: Int, x: Double, y: Double) = NavNode(
        id = id,
        floor = floor,
        x = x,
        y = y,
    )
}
