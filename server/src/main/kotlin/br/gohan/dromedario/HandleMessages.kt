package br.gohan.dromedario

import br.gohan.dromedario.data.EventType
import br.gohan.dromedario.data.GroupStatus
import br.gohan.dromedario.data.MessageModel
import br.gohan.dromedario.data.RemoveWaypointData
import br.gohan.dromedario.data.RouteGroup
import br.gohan.dromedario.data.TripSession
import br.gohan.dromedario.data.TripStatus
import br.gohan.dromedario.data.Waypoint
import br.gohan.dromedario.routes.RouteOptimizer
import io.github.aakira.napier.Napier
import io.ktor.websocket.Frame
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Handles user events like add or remove waypoints
 */
@OptIn(ExperimentalTime::class)
suspend fun handleMessage(
    message: MessageModel,
    db: DatabaseManager,
    routeOptimizer: RouteOptimizer?
) {
    when (message.event) {
        EventType.ADD_WAYPOINT -> {
            val data = message.data
            if (data == null) {
                Napier.e("Null data for ADD_WAYPOINT")
                return
            }
            val waypoint = Json.decodeFromJsonElement<Waypoint>(data)
            db.addWaypoint(waypoint)
            Napier.i("Waypoint added: ${waypoint.address}")

            // Compute route polyline if Routes API is available
            if (routeOptimizer != null) {
                val state = db.getCurrentState()
                if (state.waypoints.size >= 2) {
                    try {
                        val result = routeOptimizer.computeRoute(state.waypoints)
                        db.updateEncodedPolyline(result.encodedPolyline)
                    } catch (e: Exception) {
                        Napier.e("Error computing route after add: ${e.message}")
                    }
                }
            }
        }

        EventType.REMOVE_WAYPOINT -> {
            val data = message.data
            if (data == null) {
                Napier.e("Null data for REMOVE_WAYPOINT")
                return
            }
            val removeData = Json.decodeFromJsonElement<RemoveWaypointData>(data)
            db.removeWaypoint(removeData.waypointIndex)
            Napier.i("Waypoint removed at index: ${removeData.waypointIndex}")

            // Recompute route polyline
            if (routeOptimizer != null) {
                val state = db.getCurrentState()
                if (state.waypoints.size >= 2) {
                    try {
                        val result = routeOptimizer.computeRoute(state.waypoints)
                        db.updateEncodedPolyline(result.encodedPolyline)
                    } catch (e: Exception) {
                        Napier.e("Error computing route after remove: ${e.message}")
                    }
                } else {
                    db.updateEncodedPolyline(null)
                }
            }
        }

        EventType.OPTIMIZE_ROUTE -> {
            if (routeOptimizer == null) {
                Napier.w("Route optimizer not configured - Google Routes API key missing")
                return
            }
            val currentState = db.getCurrentState()
            if (currentState.waypoints.size < 2) {
                Napier.w("Not enough waypoints to optimize")
                return
            }
            try {
                val result = routeOptimizer.optimizeWaypoints(currentState.waypoints)
                db.updateWaypointsWithPolyline(result.waypoints, result.encodedPolyline)
                Napier.i("Route optimized - waypoints reordered")
            } catch (e: Exception) {
                Napier.e("Error optimizing route: ${e.message}")
            }
        }

        EventType.FINALIZE_ROUTE -> {
            val currentState = db.getCurrentState()
            if (currentState.waypoints.isEmpty()) {
                Napier.w("No waypoints to finalize")
                return
            }
            val groups = generateGroups(currentState.waypoints)
            val updatedTrip = TripSession(
                id = currentState.id,
                waypoints = currentState.waypoints,
                groups = groups,
                activeGroupIndex = 0,
                status = TripStatus.NAVIGATING,
                createdAt = Clock.System.now().epochSeconds,
                updatedAt = Clock.System.now().epochSeconds
            )
            db.updateTripSession(updatedTrip)
            broadcastSyncState(updatedTrip)
            Napier.i("Route finalized - ${groups.size} groups created")
        }

        EventType.GROUP_COMPLETED -> {
            val trip = db.getCurrentTripSession()
            if (trip == null) {
                Napier.w("No active trip session")
                return
            }
            val updatedGroups = trip.groups.mapIndexed { idx, group ->
                when {
                    idx == trip.activeGroupIndex -> group.copy(status = GroupStatus.COMPLETED)
                    idx == trip.activeGroupIndex + 1 -> group.copy(status = GroupStatus.ACTIVE)
                    else -> group
                }
            }
            val newActiveIndex = trip.activeGroupIndex + 1
            val newStatus = if (newActiveIndex >= trip.groups.size) TripStatus.COMPLETED else TripStatus.NAVIGATING

            val updatedTrip = trip.copy(
                groups = updatedGroups,
                activeGroupIndex = newActiveIndex,
                status = newStatus,
                updatedAt = Clock.System.now().epochSeconds
            )
            db.updateTripSession(updatedTrip)
            broadcastSyncState(updatedTrip)
            Napier.i("Group ${trip.activeGroupIndex} completed, advancing to group $newActiveIndex")
        }

        EventType.REORDER_WAYPOINTS -> {
            val data = message.data
            if (data == null) {
                Napier.e("Null data for REORDER_WAYPOINTS")
                return
            }
            val newOrder = Json.decodeFromJsonElement<List<Waypoint>>(data)
            db.updateWaypoints(newOrder)
            Napier.i("Waypoints reordered")

            if (routeOptimizer != null && newOrder.size >= 2) {
                try {
                    val result = routeOptimizer.computeRoute(newOrder)
                    db.updateEncodedPolyline(result.encodedPolyline)
                } catch (e: Exception) {
                    Napier.e("Error computing route after reorder: ${e.message}")
                }
            } else if (newOrder.size < 2) {
                db.updateEncodedPolyline(null)
            }
        }

        EventType.CLEAR_ALL -> {
            db.clearAllWaypoints()
            db.updateEncodedPolyline(null)
            Napier.i("All waypoints cleared")
        }

        else -> {
            Napier.w("Unhandled event type: ${message.event}")
        }
    }
}

/**
 * Generates route groups from waypoints, with max 9 waypoints per group.
 * This is to comply with Google Maps URL limit.
 */
private fun generateGroups(waypoints: List<Waypoint>, maxGroupSize: Int = 9): List<RouteGroup> {
    if (waypoints.isEmpty()) return emptyList()

    val groups = mutableListOf<RouteGroup>()
    var startIndex = 0
    var groupIndex = 0

    while (startIndex < waypoints.size) {
        val endIndex = minOf(startIndex + maxGroupSize, waypoints.size)
        groups.add(
            RouteGroup(
                index = groupIndex,
                waypointStartIndex = startIndex,
                waypointEndIndex = endIndex,
                status = if (groupIndex == 0) GroupStatus.ACTIVE else GroupStatus.PENDING
            )
        )
        startIndex = endIndex
        groupIndex++
    }

    return groups
}


/**
 * Broadcasts a SYNC_STATE message to all connected clients.
 */
private suspend fun broadcastSyncState(trip: TripSession) {
    val syncMessage = MessageModel(
        event = EventType.SYNC_STATE,
        data = Json.encodeToJsonElement(trip)
    )
    val json = Json.encodeToString(syncMessage)

    sessions.forEach { session ->
        try {
            session.send(Frame.Text(json))
        } catch (e: Exception) {
            Napier.e("Error broadcasting to session: ${e.message}")
        }
    }
}
