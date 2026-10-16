package com.logos.navigator.navigation

import com.logos.navigator.data.model.FloorPlan
import com.logos.navigator.data.model.MapPoint
import com.logos.navigator.data.model.NavigationData
import com.logos.navigator.data.model.Room
import com.logos.navigator.data.model.RouteAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class SiteIndoorRouterTest {
    @Test
    fun manualPointMustBeInsideRoomOrCirculationGeometry() {
        assertTrue(SiteIndoorRouter.isInsideCollege(floor = 1, x = 200.0, y = 168.0))
        assertTrue(SiteIndoorRouter.isInsideCollege(floor = 1, x = 110.0, y = 400.0))
        assertFalse(SiteIndoorRouter.isInsideCollege(floor = 1, x = 700.0, y = 500.0))
        assertFalse(SiteIndoorRouter.isInsideCollege(floor = 2, x = 700.0, y = 520.0))

        val plan = SiteIndoorRouter.plan(
            start = MapPoint(floor = 1, x = 700f, y = 500f),
            target = room("room_115", floor = 1, x = 200.0, y = 120.0),
            rooms = emptyList(),
            transition = SiteIndoorRouter.Transition.STAIRS,
        )
        assertNull(plan)
    }

    @Test
    fun routeLeavesRoomThroughItsMappedDoorAndStaysOrthogonal() {
        val start = MapPoint(floor = 1, x = 120f, y = 400f)
        val target = room("room_115", floor = 1, x = 200.0, y = 120.0)
        val plan = SiteIndoorRouter.plan(
            start = start,
            target = target,
            rooms = emptyList(),
            transition = SiteIndoorRouter.Transition.STAIRS,
            routeSession = 7L,
        )

        assertNotNull(plan)
        val route = plan!!.nodes
        assertEquals(start.x.toDouble(), route.first().x, 0.0)
        assertEquals(start.y.toDouble(), route.first().y, 0.0)
        assertEquals(1, route.last().floor)
        assertEquals("door", route.last().type)
        assertEquals(230.0, route.last().x, 0.0)
        assertEquals(156.0, route.last().y, 0.0)
        assertTrue(route.all { it.id.startsWith("site-route-7-") })
        assertTrue(route.zipWithNext().all { (from, to) ->
            from.floor == to.floor && (abs(from.x - to.x) < 1e-9 || abs(from.y - to.y) < 1e-9)
        })
    }

    @Test
    fun crossFloorRouteMarksTheSelectedLiftOrStairTransition() {
        val start = MapPoint(floor = 1, x = 200f, y = 168f)
        val target = room("room_214", floor = 2, x = 100.0, y = 60.0)
        val rooms = emptyList<Room>()

        val stairRoute = SiteIndoorRouter.plan(
            start = start,
            target = target,
            rooms = rooms,
            transition = SiteIndoorRouter.Transition.STAIRS,
        )
        assertNotNull(stairRoute)
        assertTrue(stairRoute!!.transferKind?.startsWith("stairs-") == true)
        assertEquals("stairs", stairRoute.nodes.last { it.floor == 1 }.type)
        assertEquals("stairs", stairRoute.nodes.first { it.floor == 2 }.type)

        val liftRoute = SiteIndoorRouter.plan(
            start = start,
            target = target,
            rooms = rooms,
            transition = SiteIndoorRouter.Transition.LIFT,
        )
        assertNotNull(liftRoute)
        assertEquals("lift", liftRoute!!.transferKind)
        assertEquals("elevator", liftRoute.nodes.last { it.floor == 1 }.type)
        assertEquals("elevator", liftRoute.nodes.first { it.floor == 2 }.type)

        val graph = SiteIndoorRouter.graphWithRouteTransitions(NavigationData(), liftRoute.nodes)
        val lowerLanding = liftRoute.nodes.last { it.floor == 1 }
        val upperLanding = liftRoute.nodes.first { it.floor == 2 }
        val transitionEdge = graph.edges.single { it.from == lowerLanding.id && it.to == upperLanding.id }
        assertEquals("elevator", transitionEdge.type)
        assertEquals(4.0, transitionEdge.weight, 0.0)
    }

    @Test
    fun routeInstructionsAndDistanceUseTheSameDoorToDoorPolyline() {
        val start = MapPoint(floor = 1, x = 120f, y = 400f)
        val target = room("room_115", floor = 1, x = 200.0, y = 120.0)
        val plan = SiteIndoorRouter.plan(
            start = start,
            target = target,
            rooms = emptyList(),
            transition = SiteIndoorRouter.Transition.STAIRS,
        )!!
        val graph = NavigationData(
            floors = listOf(
                FloorPlan(1, "1 этаж", "floors/floor_1.svg", 758, 552, 0.083333),
                FloorPlan(2, "2 этаж", "floors/floor_2.svg", 767, 561, 0.083333),
            ),
        )
        val routeGraph = SiteIndoorRouter.graphWithRouteTransitions(graph, plan.nodes)
        val finalDoor = plan.nodes.last()
        val destinationAtDoor = target.copy(x = finalDoor.x, y = finalDoor.y)
        val distances = RouteProgressCalculator.distances(routeGraph, plan.nodes, start, destinationAtDoor)
        val expectedMapLength = plan.nodes.zipWithNext().sumOf { (from, to) -> hypot(to.x - from.x, to.y - from.y) }
        val expectedMeters = expectedMapLength * 0.083333
        val steps = RouteStepGenerator.generate(
            graph = routeGraph,
            path = plan.nodes,
            destinationName = target.name,
            totalDistanceMeters = distances.totalMeters,
        )

        assertEquals(0.0, distances.startConnectorMeters, 0.0)
        assertEquals(0.0, distances.destinationConnectorMeters, 0.0)
        assertEquals(expectedMeters, distances.totalMeters, 1e-6)
        assertEquals(RouteAction.START, steps.first().action)
        assertTrue(steps.any { it.action == RouteAction.DOOR })
        assertEquals(RouteAction.ARRIVAL, steps.last().action)
        assertEquals(1f, steps.last().progress, 0f)
    }

    @Test
    fun assemblyHallRouteUsesOnlyItsFirstFloorEntranceAndOtherUnavailableRoomsStayClosed() {
        assertEquals("canteen", SiteIndoorRouter.siteRoomKeyForAppRoom("room_126"))
        assertEquals("meteorology-236", SiteIndoorRouter.siteRoomKeyForAppRoom("room_213"))
        assertEquals("academic-office", SiteIndoorRouter.siteRoomKeyForAppRoom("room_education_department"))
        assertEquals("electro-radio-230", SiteIndoorRouter.siteRoomKeyForAppRoom("room_230"))
        assertEquals("auditorium-104", SiteIndoorRouter.siteRoomKeyForAppRoom("room_104"))

        val assemblyHall = room("room_104", floor = 1, x = 683.0, y = 167.0)
        val firstFloorPlan = SiteIndoorRouter.plan(
            start = MapPoint(floor = 1, x = 200f, y = 168f),
            target = assemblyHall,
            rooms = emptyList(),
            transition = SiteIndoorRouter.Transition.STAIRS,
        )
        assertNotNull(firstFloorPlan)
        assertTrue(firstFloorPlan!!.nodes.all { it.floor == 1 })
        assertEquals("door", firstFloorPlan.nodes.last().type)
        assertEquals(610.0, firstFloorPlan.nodes.last().x, 0.0)
        assertEquals(205.0, firstFloorPlan.nodes.last().y, 0.0)

        val secondFloorPlan = SiteIndoorRouter.plan(
            start = MapPoint(floor = 2, x = 200f, y = 168f),
            target = assemblyHall,
            rooms = emptyList(),
            transition = SiteIndoorRouter.Transition.STAIRS,
        )
        assertNotNull(secondFloorPlan)
        assertTrue(secondFloorPlan!!.nodes.any { it.floor == 2 })
        assertEquals(1, secondFloorPlan.nodes.last().floor)
        assertEquals("door", secondFloorPlan.nodes.last().type)
        assertEquals(610.0, secondFloorPlan.nodes.last().x, 0.0)
        assertEquals(205.0, secondFloorPlan.nodes.last().y, 0.0)

        listOf(
            room("room_144", floor = 1, x = 100.0, y = 200.0, routeAvailable = false),
            room("room_161", floor = 1, x = 100.0, y = 200.0, routeAvailable = false),
            room("room_232", floor = 2, x = 100.0, y = 200.0, routeAvailable = false),
            room("room_233", floor = 2, x = 100.0, y = 200.0, routeAvailable = false),
            room("room_observation_2", floor = 2, x = 100.0, y = 200.0, routeAvailable = false),
        ).forEach { unavailable ->
            assertNull(
                SiteIndoorRouter.plan(
                    start = MapPoint(floor = unavailable.floor, x = 200f, y = 168f),
                    target = unavailable,
                    rooms = emptyList(),
                    transition = SiteIndoorRouter.Transition.STAIRS,
                ),
            )
        }
    }

    private fun room(
        id: String,
        floor: Int,
        x: Double,
        y: Double,
        routeAvailable: Boolean = true,
    ) = Room(
        id = id,
        name = id,
        nameEn = id,
        number = null,
        floor = floor,
        category = "classroom",
        nodeId = "${id}_door",
        x = x,
        y = y,
        centerX = x,
        centerY = y,
        routeAvailable = routeAvailable,
    )
}
