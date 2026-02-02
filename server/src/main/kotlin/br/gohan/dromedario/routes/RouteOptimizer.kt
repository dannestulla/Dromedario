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

    /**
     * Optimizes the order of waypoints using Google Routes API.
     * Origin and destination remain fixed, only intermediate waypoints are reordered.
     *
     * @param waypoints List of waypoints to optimize (minimum 2 required)
     * @return Optimized list of waypoints with updated indices
     */
    suspend fun optimizeWaypoints(waypoints: List<Waypoint>): List<Waypoint> {
        if (waypoints.size < 2) return waypoints
        if (waypoints.size == 2) return waypoints // No optimization needed for 2 points

        val origin = waypoints.first()
        val destination = waypoints.last()
        val intermediates = waypoints.drop(1).dropLast(1)

        // If no intermediates, nothing to optimize
        if (intermediates.isEmpty()) return waypoints

        val requestBody = buildRequestBody(origin, destination, intermediates)

        Napier.i("Calling Google Routes API to optimize ${waypoints.size} waypoints")

        try {
            val response = client.post("https://routes.googleapis.com/directions/v2:computeRoutes") {
                header("X-Goog-Api-Key", apiKey)
                header("X-Goog-FieldMask", "routes.optimizedIntermediateWaypointIndex")
                contentType(ContentType.Application.Json)
                setBody(requestBody.toString())
            }

            if (!response.status.isSuccess()) {
                Napier.e("Routes API error: ${response.status} - ${response.bodyAsText()}")
                return waypoints
            }

            val responseText = response.bodyAsText()
            Napier.d("Routes API response: $responseText")

            val result = json.parseToJsonElement(responseText)
            val optimizedOrder = result.jsonObject["routes"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("optimizedIntermediateWaypointIndex")
                ?.jsonArray?.map { it.jsonPrimitive.int }

            if (optimizedOrder == null) {
                Napier.w("No optimization data in response")
                return waypoints
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
            return reordered.mapIndexed { idx, wp -> wp.copy(index = idx) }

        } catch (e: Exception) {
            Napier.e("Error calling Routes API: ${e.message}")
            throw e
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
