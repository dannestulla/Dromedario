package br.gohan.dromedario.map

import br.gohan.dromedario.data.Waypoint
import io.github.aakira.napier.Napier
import kotlinx.browser.document

// Controls a google.maps.Map instance: markers, polylines, bounds. Requires API loaded first.
class MapController(containerId: String) {
    private val map: Map
    private val markers = mutableListOf<Marker>()
    private var polyline: Polyline? = null
    private val geocoder: Geocoder = newGeocoder()

    var onWaypointDragged: ((index: Int, address: String, lat: Double, lng: Double) -> Unit)? = null
    var onMapDoubleClick: ((address: String, lat: Double, lng: Double) -> Unit)? = null

    init {
        val container = document.getElementById(containerId)
            ?: throw IllegalStateException("Map container not found: $containerId")

        map = newMap(
            container, mapOptions(
                center = latLng(-30.05, -51.20),
                zoom = 12
            )
        )

        map.addListener("dblclick") { event ->
            val clickLatLng = event.latLng
            val lat = clickLatLng.lat() as Double
            val lng = clickLatLng.lng() as Double
            reverseGeocode(lat, lng) { address ->
                onMapDoubleClick?.invoke(address, lat, lng)
            }
        }

        Napier.d("MapController: Map initialized")
    }

    fun updateWaypoints(waypoints: List<Waypoint>, encodedPolyline: String? = null) {
        clearMarkers()

        waypoints.forEachIndexed { index, waypoint ->
            addMarker(waypoint, index + 1)
        }

        if (waypoints.size >= 2) {
            if (encodedPolyline != null) {
                drawEncodedPolyline(encodedPolyline)
            } else {
                drawStraightPolyline(waypoints)
            }
        }

        if (waypoints.isNotEmpty()) {
            fitBounds(waypoints)
        }
    }

    fun centerOn(latitude: Double, longitude: Double, zoom: Int = 15) {
        map.setCenter(latLng(latitude, longitude))
        map.setZoom(zoom)
    }

    private fun drawEncodedPolyline(encodedPolyline: String) {
        val path = decodePath(encodedPolyline)
        polyline = newPolyline(polylineOptions(path)).also { it.setMap(map) }
    }

    private fun addMarker(waypoint: Waypoint, label: Int) {
        val markerIndex = markers.size
        val marker = newMarker(
            markerOptions(
                position = latLng(waypoint.latitude, waypoint.longitude),
                map = map,
                label = label.toString(),
                title = waypoint.address,
                draggable = true
            )
        )
        marker.addListener("dragend") {
            val pos = marker.getPosition()
            val lat = pos.lat()
            val lng = pos.lng()
            reverseGeocode(lat, lng) { address ->
                onWaypointDragged?.invoke(markerIndex, address, lat, lng)
            }
        }
        markers.add(marker)
    }

    private fun clearMarkers() {
        markers.forEach { it.setMap(null) }
        markers.clear()
        polyline?.setMap(null)
        polyline = null
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
        if (waypoints.size == 1) {
            map.setZoom(14)
        }
    }

    private fun reverseGeocode(lat: Double, lng: Double, callback: (String) -> Unit) {
        val request = js("{}")
        request.location = latLng(lat, lng)
        geocoder.geocode(request) { results, status ->
            val statusStr = status.toString()
            if (statusStr == "OK" && results != null) {
                val address = results[0]?.formatted_address?.toString()
                if (!address.isNullOrBlank()) {
                    callback(address)
                } else {
                    callback("$lat, $lng")
                }
            } else {
                Napier.e("Reverse geocode failed: $statusStr")
                callback("$lat, $lng")
            }
        }
    }
}
