package br.gohan.dromedario.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
@OptIn(ExperimentalTime::class)
data class RouteStateModel(
    @SerialName("_id")
    val id: String = "session",
    val waypoints: List<Waypoint> = emptyList(),
    val encodedPolyline: String? = null,
    val updatedAt: Long = Clock.System.now().epochSeconds
)

@Serializable
data class Waypoint(
    val index: Int = 0,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class RemoveWaypointData(
    val waypointIndex: Int
)