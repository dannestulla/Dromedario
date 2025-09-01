package br.gohan.dromedario.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import br.gohan.dromedario.data.model.LatLngData
import br.gohan.dromedario.data.model.LegDetails
import br.gohan.dromedario.data.model.LocationData
import br.gohan.dromedario.data.model.RouteDetails
import br.gohan.dromedario.data.model.RouteModifiers
import br.gohan.dromedario.data.model.RouteRequest
import br.gohan.dromedario.data.model.RouteResponse
import br.gohan.dromedario.data.model.WaypointData
import com.google.android.gms.maps.model.LatLng
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json

class MobileRepository(
    private val client: HttpClient,
    private val mapsApiKey: String
) {

    /**
     * Busca rota usando Routes API v2 com suporte a múltiplas paradas
     * @param origin Ponto de origem
     * @param destination Ponto de destino
     * @param waypoints Lista de paradas intermediárias
     */
    suspend fun getOptimizedRoute(
        origin: LatLng,
        destination: LatLng,
        waypoints: List<LatLng> = emptyList(),
    ): RouteResponse? {
        return try {
            val request = RouteRequest(
                origin = WaypointData(
                    location = LocationData(
                        latLng = LatLngData(origin.latitude, origin.longitude)
                    )
                ),
                destination = WaypointData(
                    location = LocationData(
                        latLng = LatLngData(destination.latitude, destination.longitude)
                    )
                ),
                intermediates = waypoints.map { waypoint ->
                    WaypointData(
                        location = LocationData(
                            latLng = LatLngData(waypoint.latitude, waypoint.longitude)
                        )
                    )
                },
                routeModifiers = RouteModifiers()
            )

            val response = client.post("https://routes.googleapis.com/directions/v2:computeRoutes") {
                headers {
                    append("X-Goog-Api-Key", mapsApiKey)
                    append("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.legs")
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            Json.decodeFromString<RouteResponse>(response.body())

        } catch (e: Exception) {
            Napier.e { "Erro na Routes API: ${e.message}" }
            null
        }
    }

    suspend fun getRoutePolyline(
        origin: LatLng,
        destination: LatLng,
        waypoints: List<LatLng> = emptyList()
    ): String? {
        val routeResponse = getOptimizedRoute(origin, destination, waypoints)
        return routeResponse?.routes?.firstOrNull()?.polyline?.encodedPolyline
    }

    /**
     * Busca rota otimizada com múltiplas paradas e retorna informações completas
     */
    suspend fun getRouteWithDetails(
        origin: LatLng,
        destination: LatLng,
        waypoints: List<LatLng> = emptyList()
    ): RouteDetails? {
        val routeResponse = getOptimizedRoute(origin, destination, waypoints)
        val route = routeResponse?.routes?.firstOrNull() ?: return null

        return RouteDetails(
            polyline = route.polyline?.encodedPolyline ?: "",
            totalDistanceMeters = route.distanceMeters ?: 0,
            totalDuration = route.duration ?: "",
            legs = route.legs.map { leg ->
                LegDetails(
                    distanceMeters = leg.distanceMeters,
                    duration = leg.duration,
                    staticDuration = leg.staticDuration,
                    startLocation = LatLng(
                        leg.startLocation.latLng.latitude,
                        leg.startLocation.latLng.longitude
                    ),
                    endLocation = LatLng(
                        leg.endLocation.latLng.latitude,
                        leg.endLocation.latLng.longitude
                    ),
                    localizedDistance = leg.localizedValues?.distance?.text,
                    localizedDuration = leg.localizedValues?.duration?.text
                )
            }
        )
    }

    /**
     * Busca rota com detalhes completos incluindo steps de navegação
     */
    suspend fun getDetailedRoute(
        origin: LatLng,
        destination: LatLng,
        waypoints: List<LatLng> = emptyList()
    ): RouteResponse? {
        return try {
            val request = RouteRequest(
                origin = WaypointData(
                    location = LocationData(
                        latLng = LatLngData(origin.latitude, origin.longitude)
                    )
                ),
                destination = WaypointData(
                    location = LocationData(
                        latLng = LatLngData(destination.latitude, destination.longitude)
                    )
                ),
                intermediates = waypoints.map { waypoint ->
                    WaypointData(
                        location = LocationData(
                            latLng = LatLngData(waypoint.latitude, waypoint.longitude)
                        )
                    )
                }
            )

            val response = client.post("https://routes.googleapis.com/directions/v2:computeRoutes") {
                headers {
                    append("X-Goog-Api-Key", mapsApiKey)
                    append("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.legs.duration,routes.legs.distanceMeters,routes.legs.polyline,routes.legs.startLocation,routes.legs.endLocation,routes.legs.steps,routes.legs.localizedValues")
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            Json.decodeFromString<RouteResponse>(response.body())

        } catch (e: Exception) {
            Napier.e { "Erro na Routes API detalhada: ${e.message}" }
            null
        }
    }

    fun openInGoogleMaps(
        context: Context,
        origin: LatLng,
        destination: LatLng,
        waypoints: List<LatLng> = emptyList()
    ) {
        val waypointsStr = if (waypoints.isNotEmpty()) {
            waypoints.joinToString("|") { "${it.latitude},${it.longitude}" }
        } else ""

        val uri = buildString {
            append("https://www.google.com/maps/dir/?api=1")
            append("&origin=${origin.latitude},${origin.longitude}")
            append("&destination=${destination.latitude},${destination.longitude}")
            if (waypointsStr.isNotEmpty()) {
                append("&waypoints=$waypointsStr")
            }
            append("&travelmode=driving")
        }

        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }
}
