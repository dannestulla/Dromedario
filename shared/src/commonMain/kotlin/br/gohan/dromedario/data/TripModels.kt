package br.gohan.dromedario.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
@OptIn(ExperimentalTime::class)
data class TripSession(
    @SerialName("_id")
    val id: String,
    val waypoints: List<Waypoint> = emptyList(),
    val groups: List<RouteGroup> = emptyList(),
    val activeGroupIndex: Int = 0,
    val status: TripStatus = TripStatus.PLANNING,
    val createdAt: Long = Clock.System.now().epochSeconds,
    val updatedAt: Long = Clock.System.now().epochSeconds
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
    val waypointEndIndex: Int,  // Exclusive
    val status: GroupStatus = GroupStatus.PENDING
)

@Serializable
enum class GroupStatus {
    PENDING,
    ACTIVE,
    COMPLETED
}
