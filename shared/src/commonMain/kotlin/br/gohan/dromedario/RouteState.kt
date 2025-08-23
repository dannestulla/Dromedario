package br.gohan.dromedario

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
@OptIn(ExperimentalUuidApi::class)
data class RouteState @OptIn(ExperimentalTime::class) constructor(
    @SerialName("_id")
    val id: String = Uuid.random().toString(),
    val waypoints: List<Waypoint> = emptyList(),
    val updatedAt: Long = Clock.System.now().epochSeconds
)

@Serializable
data class Waypoint(
    val address: String,
    val latitude: Double,
    val longitude: Double,
)