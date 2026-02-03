package br.gohan.dromedario.map

import br.gohan.dromedario.data.Waypoint
import io.github.aakira.napier.Napier
import kotlinx.browser.document

// Controls a google.maps.Map instance: markers, polylines, bounds. Requires API loaded first.
class MapController(containerId: String) {
    private val map: GoogleMap
    private val markers = mutableListOf<Marker>()
    private var polyline: Polyline? = null
    private val directionsService = newDirectionsService()
    private val directionsRenderer = newDirectionsRenderer()

    init {
        val container = document.getElementById(containerId)
            ?: throw IllegalStateException("Map container not found: $containerId")

        map = newGoogleMap(container, mapOptions(
            center = latLng(-30.05, -51.20),
            zoom = 12
        ))

        val rendererOptions = js("{}")
        rendererOptions.suppressMarkers = true
        rendererOptions.polylineOptions = polylineOptions(js("[]"))
        directionsRenderer.setOptions(rendererOptions)
        directionsRenderer.setMap(map)

        Napier.d("MapController: Map initialized")
    }

    fun updateWaypoints(waypoints: List<Waypoint>) {
        clearMarkers()

        waypoints.forEachIndexed { index, waypoint ->
            addMarker(waypoint, index + 1)
        }

        if (waypoints.size >= 2) {
            drawRoute(waypoints)
        }

        if (waypoints.isNotEmpty()) {
            fitBounds(waypoints)
        }
    }

    fun centerOn(latitude: Double, longitude: Double, zoom: Int = 15) {
        map.setCenter(latLng(latitude, longitude))
        map.setZoom(zoom)
    }

    fun highlightWaypoint(waypointIndex: Int) {
        markers.forEachIndexed { index, marker ->
            if (index == waypointIndex) {
                marker.setIcon(iconOptions("http://maps.google.com/mapfiles/ms/icons/green-dot.png"))
            } else {
                marker.setIcon(null)
            }
        }
    }

    fun drawEncodedPolyline(encodedPolyline: String) {
        val path = decodePath(encodedPolyline)

        polyline?.setMap(null)
        polyline = newPolyline(polylineOptions(path)).also { it.setMap(map) }
    }

    private fun addMarker(waypoint: Waypoint, label: Int) {
        val marker = newMarker(markerOptions(
            position = latLng(waypoint.latitude, waypoint.longitude),
            map = map,
            label = label.toString(),
            title = waypoint.address
        ))
        markers.add(marker)
    }

    private fun clearMarkers() {
        markers.forEach { it.setMap(null) }
        markers.clear()
        polyline?.setMap(null)
        polyline = null
        directionsRenderer.setDirections(js("{ routes: [] }"))
    }

    private fun drawRoute(waypoints: List<Waypoint>) {
        val origin = waypoints.first()
        val destination = waypoints.last()

        val request = js("{}").unsafeCast<DirectionsRequest>()
        request.origin = latLng(origin.latitude, origin.longitude)
        request.destination = latLng(destination.latitude, destination.longitude)
        request.travelMode = "DRIVING"

        if (waypoints.size > 2) {
            val intermediates = waypoints.subList(1, waypoints.size - 1)
            val jsWaypoints = js("[]")
            intermediates.forEach { wp ->
                val jsWp = js("{}")
                jsWp.location = latLng(wp.latitude, wp.longitude)
                jsWp.stopover = true
                jsWaypoints.push(jsWp)
            }
            request.waypoints = jsWaypoints
        }

        directionsService.route(request) { result, status ->
            if (status == "OK") {
                directionsRenderer.setDirections(result)
                Napier.d("MapController: Directions rendered successfully")
            } else {
                Napier.e("MapController: Directions request failed: $status, falling back to straight line")
                drawStraightPolyline(waypoints)
            }
        }
    }

    private fun drawStraightPolyline(waypoints: List<Waypoint>) {
        val path = js("[]")
        waypoints.forEach { wp -> path.push(latLng(wp.latitude, wp.longitude)) }
        polyline = newPolyline(polylineOptions(path)).also { it.setMap(map) }
    }

    private fun fitBounds(waypoints: List<Waypoint>) {
        val bounds = newLatLngBounds()
        waypoints.forEach { wp -> bounds.extend(latLng(wp.latitude, wp.longitude)) }
        map.fitBounds(bounds, 50)
    }
}
