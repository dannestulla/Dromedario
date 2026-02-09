package br.gohan.dromedario.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Route State (legacy single-session model)
@Serializable
data class RouteStateModel(
    @SerialName("_id")
    val id: String = "session",
    val waypoints: List<Waypoint> = emptyList(),
    val encodedPolyline: String? = null,
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Serializable
data class Waypoint(
    val index: Int = 0,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

// Trip Session (multi-group navigation)
@Serializable
data class TripSession(
    @SerialName("_id")
    val id: String,
    val waypoints: List<Waypoint> = emptyList(),
    val groups: List<RouteGroup> = emptyList(),
    val activeGroupIndex: Int = 0,
    val status: TripStatus = TripStatus.PLANNING,
    val createdAt: Long = System.currentTimeMillis() / 1000,
    val updatedAt: Long = System.currentTimeMillis() / 1000
)

@Serializable
enum class TripStatus {
    PLANNING,
    NAVIGATING,
    COMPLETED
}

@Serializable
data class RouteGroup(
    val index: Int,
    val waypointStartIndex: Int,
    val waypointEndIndex: Int,
    val status: GroupStatus = GroupStatus.PENDING
)

@Serializable
enum class GroupStatus {
    PENDING,
    ACTIVE,
    COMPLETED
}

// WebSocket Messages
@Serializable
data class MessageModel(
    val event: EventType,
    val data: JsonElement? = null
)

@Serializable
enum class EventType {
    ADD_WAYPOINT,
    REMOVE_WAYPOINT,
    OPTIMIZE_ROUTE,
    FINALIZE_ROUTE,
    GROUP_COMPLETED,
    SYNC_STATE,
    REORDER_WAYPOINTS,
    CLEAR_ALL
}

@Serializable
data class RemoveWaypointData(
    val waypointIndex: Int
)
