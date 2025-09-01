package br.gohan.dromedario.data.model

import kotlinx.serialization.Serializable

// Request models
@Serializable
data class LatLngData(
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class LocationData(
    val latLng: LatLngData
)

@Serializable
data class WaypointData(
    val location: LocationData,
    val via: Boolean = false
)

@Serializable
data class RouteRequest(
    val origin: WaypointData,
    val destination: WaypointData,
    val intermediates: List<WaypointData> = emptyList(),
    val travelMode: String = "DRIVE",
    val routingPreference: String = "TRAFFIC_AWARE",
    val computeAlternativeRoutes: Boolean = false,
    val routeModifiers: RouteModifiers = RouteModifiers(),
    val languageCode: String = "pt-BR",
    val units: String = "METRIC"
)

@Serializable
data class RouteModifiers(
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val avoidFerries: Boolean = false
)

// Response models - baseados na resposta real da API
@Serializable
data class RouteResponse(
    val routes: List<Route>
)

@Serializable
data class Route(
    val legs: List<RouteLeg> = emptyList(),
    val distanceMeters: Int? = null,
    val duration: String? = null,
    val polyline: Polyline? = null
)

@Serializable
data class RouteLeg(
    val distanceMeters: Int,
    val duration: String,
    val staticDuration: String,
    val polyline: Polyline? = null,
    val startLocation: LocationData,
    val endLocation: LocationData,
    val steps: List<RouteStep> = emptyList(),
    val localizedValues: LocalizedValues? = null
)

@Serializable
data class RouteStep(
    val distanceMeters: Int,
    val staticDuration: String,
    val polyline: Polyline,
    val startLocation: LocationData,
    val endLocation: LocationData,
    val navigationInstruction: NavigationInstruction? = null,
    val localizedValues: LocalizedValues? = null,
    val travelMode: String
)

@Serializable
data class NavigationInstruction(
    val maneuver: String,
    val instructions: String
)

@Serializable
data class LocalizedValues(
    val distance: LocalizedText? = null,
    val duration: LocalizedText? = null,
    val staticDuration: LocalizedText? = null
)

@Serializable
data class LocalizedText(
    val text: String
)

@Serializable
data class Polyline(
    val encodedPolyline: String
)
