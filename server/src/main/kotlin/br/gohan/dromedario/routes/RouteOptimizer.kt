package br.gohan.dromedario.routes

import br.gohan.dromedario.data.Waypoint
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/**
 * Handles route optimization using Google Routes API.
 * Uses the optimizeWaypointOrder parameter to find the most efficient route.
 */
class RouteOptimizer(private val apiKey: String) {

    private val client = HttpClient(CIO)
    private val json = Json { ignoreUnknownKeys = true }

    data class OptimizationResult(
        val waypoints: List<Waypoint>,
        val encodedPolyline: String?
    )

    /**
     * Optimizes the order of waypoints using Google Routes API.
     * Origin and destination remain fixed, only intermediate waypoints are reordered.
     * Also returns the encoded polyline for the optimized route.
     *
     * @param waypoints List of waypoints to optimize (minimum 2 required)
     * @return OptimizationResult with optimized waypoints and encoded polyline
     */
    suspend fun optimizeWaypoints(waypoints: List<Waypoint>): OptimizationResult {
        if (waypoints.size < 2) return OptimizationResult(waypoints, null)
        if (waypoints.size == 2) return computeRoute(waypoints)

        val origin = waypoints.first()
        val destination = waypoints.last()
        val intermediates = waypoints.drop(1).dropLast(1)

        // If no intermediates, just compute the route
        if (intermediates.isEmpty()) return computeRoute(waypoints)

        val requestBody = buildRequestBody(origin, destination, intermediates)

        Napier.i("Calling Google Routes API to optimize ${waypoints.size} waypoints")

        try {
            val response = client.post("https://routes.googleapis.com/directions/v2:computeRoutes") {
                header("X-Goog-Api-Key", apiKey)
                header("X-Goog-FieldMask", "routes.optimizedIntermediateWaypointIndex,routes.polyline.encodedPolyline")
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                Napier.e("Routes API error: ${response.status} - ${response.bodyAsText()}")
                return OptimizationResult(waypoints, null)
            }

            val responseText = response.bodyAsText()
            Napier.d("Routes API response: $responseText")

            val result = json.parseToJsonElement(responseText)
            val route = result.jsonObject["routes"]?.jsonArray?.firstOrNull()?.jsonObject

            val optimizedOrder = route?.get("optimizedIntermediateWaypointIndex")
                ?.jsonArray?.map { it.jsonPrimitive.int }

            val encodedPolyline = route?.get("polyline")
                ?.jsonObject?.get("encodedPolyline")
                ?.jsonPrimitive?.content

            if (optimizedOrder == null) {
                Napier.w("No optimization data in response")
                return OptimizationResult(waypoints, encodedPolyline)
            }

            // Reorder waypoints based on optimization result
            val reordered = mutableListOf(origin)
            optimizedOrder.forEach { idx ->
                if (idx < intermediates.size) {
                    reordered.add(intermediates[idx])
                }
            }
            reordered.add(destination)

            // Re-index the waypoints
            val reindexed = reordered.mapIndexed { idx, wp -> wp.copy(index = idx) }
            return OptimizationResult(reindexed, encodedPolyline)

        } catch (e: Exception) {
            Napier.e("Error calling Routes API: ${e.message}")
            throw e
        }
    }

    /**
     * Computes the route polyline for the given waypoints without optimization.
     */
    suspend fun computeRoute(waypoints: List<Waypoint>): OptimizationResult {
        if (waypoints.size < 2) return OptimizationResult(waypoints, null)

        val origin = waypoints.first()
        val destination = waypoints.last()
        val intermediates = waypoints.drop(1).dropLast(1)

        val requestBody = buildJsonObject {
            put("origin", buildWaypointJson(origin))
            put("destination", buildWaypointJson(destination))
            if (intermediates.isNotEmpty()) {
                putJsonArray("intermediates") {
                    intermediates.forEach { add(buildWaypointJson(it)) }
                }
            }
            put("travelMode", "DRIVE")
            put("routingPreference", "TRAFFIC_AWARE")
        }

        try {
            val response = client.post("https://routes.googleapis.com/directions/v2:computeRoutes") {
                header("X-Goog-Api-Key", apiKey)
                header("X-Goog-FieldMask", "routes.polyline.encodedPolyline")
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                Napier.e("Routes API error: ${response.status} - ${response.bodyAsText()}")
                return OptimizationResult(waypoints, null)
            }

            val responseText = response.bodyAsText()
            val result = json.parseToJsonElement(responseText)
            val encodedPolyline = result.jsonObject["routes"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("polyline")
                ?.jsonObject?.get("encodedPolyline")
                ?.jsonPrimitive?.content

            return OptimizationResult(waypoints, encodedPolyline)
        } catch (e: Exception) {
            Napier.e("Error computing route: ${e.message}")
            return OptimizationResult(waypoints, null)
        }
    }

    /**
     * Builds the request body for Google Routes API.
     */
    private fun buildRequestBody(
        origin: Waypoint,
        destination: Waypoint,
        intermediates: List<Waypoint>
    ): JsonObject {
        return buildJsonObject {
            put("origin", buildWaypointJson(origin))
            put("destination", buildWaypointJson(destination))
            putJsonArray("intermediates") {
                intermediates.forEach { wp ->
                    add(buildWaypointJson(wp))
                }
            }
            put("optimizeWaypointOrder", true)
            put("travelMode", "DRIVE")
            put("routingPreference", "TRAFFIC_AWARE")
        }
    }

    /**
     * Builds a waypoint JSON object for the Routes API request.
     */
    private fun buildWaypointJson(waypoint: Waypoint): JsonObject {
        return buildJsonObject {
            put("location", buildJsonObject {
                put("latLng", buildJsonObject {
                    put("latitude", waypoint.latitude)
                    put("longitude", waypoint.longitude)
                })
            })
        }
    }

    /**
     * Closes the HTTP client resources.
     */
    fun close() {
        client.close()
    }
}
