package br.gohan.dromedario.map

import br.gohan.dromedario.MAPS_API_KEY
import br.gohan.dromedario.data.Waypoint
import io.github.aakira.napier.Napier
import kotlinx.browser.document
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@JsModule("@googlemaps/js-api-loader")
external object JsApiLoaderModule

class MapController private constructor(
    containerId: String
) {
    private val map: dynamic
    private val markers = mutableListOf<dynamic>()
    private var polyline: dynamic = null

    init {
        val container = document.getElementById(containerId)
            ?: throw IllegalStateException("Map container not found: $containerId")

        val mapOptions = js("{}")
        mapOptions.center = js("({ lat: -30.05, lng: -51.20 })")
        mapOptions.zoom = 12

        map = createMap(container, mapOptions)
        Napier.d("MapController: Map initialized successfully")
    }

    fun updateWaypoints(waypoints: List<Waypoint>) {
        clearMarkers()

        waypoints.forEachIndexed { index, waypoint ->
            addMarker(waypoint, index + 1)
        }

        if (waypoints.size >= 2) {
            drawPolyline(waypoints)
        }

        if (waypoints.isNotEmpty()) {
            fitBounds(waypoints)
        }

        Napier.d("MapController: Updated with ${waypoints.size} waypoints")
    }

    private fun addMarker(waypoint: Waypoint, label: Int) {
        val position = js("{}")
        position.lat = waypoint.latitude
        position.lng = waypoint.longitude

        val markerOptions = js("{}")
        markerOptions.position = position
        markerOptions.map = map
        markerOptions.label = label.toString()
        markerOptions.title = waypoint.address

        val marker = createMarker(markerOptions)
        markers.add(marker)
    }

    private fun clearMarkers() {
        markers.forEach { marker ->
            marker.setMap(null)
        }
        markers.clear()

        polyline?.setMap(null)
        polyline = null
    }

    private fun drawPolyline(waypoints: List<Waypoint>) {
        val path = js("[]")
        waypoints.forEach { wp ->
            val point = js("{}")
            point.lat = wp.latitude
            point.lng = wp.longitude
            path.push(point)
        }

        val polylineOptions = js("{}")
        polylineOptions.path = path
        polylineOptions.geodesic = true
        polylineOptions.strokeColor = "#4285F4"
        polylineOptions.strokeOpacity = 1.0
        polylineOptions.strokeWeight = 4

        polyline = createPolyline(polylineOptions)
        polyline.setMap(map)
    }

    private fun fitBounds(waypoints: List<Waypoint>) {
        val bounds = createLatLngBounds()
        waypoints.forEach { wp ->
            val point = js("{}")
            point.lat = wp.latitude
            point.lng = wp.longitude
            bounds.extend(point)
        }
        map.fitBounds(bounds, 50)
    }

    fun drawEncodedPolyline(encodedPolyline: String) {
        val decodePath = js("google.maps.geometry.encoding.decodePath")
        val path = decodePath(encodedPolyline)

        polyline?.setMap(null)

        val polylineOptions = js("{}")
        polylineOptions.path = path
        polylineOptions.geodesic = true
        polylineOptions.strokeColor = "#4285F4"
        polylineOptions.strokeOpacity = 1.0
        polylineOptions.strokeWeight = 4

        polyline = createPolyline(polylineOptions)
        polyline.setMap(map)
    }

    fun centerOn(latitude: Double, longitude: Double, zoom: Int = 15) {
        val center = js("{}")
        center.lat = latitude
        center.lng = longitude
        map.setCenter(center)
        map.setZoom(zoom)
    }

    fun highlightWaypoint(waypointIndex: Int) {
        markers.forEachIndexed { index, marker ->
            if (index == waypointIndex) {
                val icon = js("{}")
                icon.url = "http://maps.google.com/mapfiles/ms/icons/green-dot.png"
                marker.setIcon(icon)
            } else {
                marker.setIcon(null)
            }
        }
    }

    fun attachAutocomplete(
        containerElement: dynamic,
        onPlaceSelected: (address: String, latitude: Double, longitude: Double) -> Unit
    ) {
        val autocompleteElement = createPlaceAutocompleteElement()
        containerElement.appendChild(autocompleteElement)

        val callback: (dynamic) -> Unit = { event ->
            val placePrediction = event.placePrediction
            if (placePrediction != null && placePrediction != undefined) {
                val place = placePrediction.toPlace()
                val fetchOptions = js("{}")
                fetchOptions.fields = js("['formattedAddress', 'location']")
                place.fetchFields(fetchOptions).then { _: dynamic ->
                    val formattedAddress = place.formattedAddress
                    val location = place.location
                    if (location != null && location != undefined) {
                        val address = if (formattedAddress != null && formattedAddress != undefined) {
                            formattedAddress as String
                        } else {
                            "Unknown location"
                        }
                        val lat = (location.lat()) as Double
                        val lng = (location.lng()) as Double
                        onPlaceSelected(address, lat, lng)
                    }
                }
            }
        }
        autocompleteElement.addEventListener("gmp-select", callback)
        Napier.d("MapController: Places Autocomplete (New) attached")
    }

    private fun createMap(container: dynamic, options: dynamic): dynamic =
        js("new google.maps.Map(container, options)")

    private fun createMarker(options: dynamic): dynamic =
        js("new google.maps.Marker(options)")

    private fun createPolyline(options: dynamic): dynamic =
        js("new google.maps.Polyline(options)")

    private fun createLatLngBounds(): dynamic =
        js("new google.maps.LatLngBounds()")

    private fun createPlaceAutocompleteElement(): dynamic =
        js("new google.maps.places.PlaceAutocompleteElement()")

    companion object {
        private var librariesLoaded = false

        suspend fun create(containerId: String): MapController {
            loadGoogleMapsIfNeeded()
            return MapController(containerId)
        }

        private suspend fun loadGoogleMapsIfNeeded() {
            if (librariesLoaded) return

            // Access the Loader class from the npm module's named export
            val LoaderClass = JsApiLoaderModule.asDynamic().Loader

            // Build constructor options
            val options = js("{}")
            options.apiKey = MAPS_API_KEY
            options.version = "weekly"

            // Instantiate: equivalent to "new Loader({ apiKey, version })"
            // Reflect.construct is needed because LoaderClass is a dynamic reference
            val args = js("[]")
            args.push(options)
            val loader = js("Reflect").construct(LoaderClass, args)

            // Import all required libraries via the loader instance.
            // After each resolves, classes are available in the google.maps namespace.
            awaitPromise(loader.importLibrary("maps"))
            awaitPromise(loader.importLibrary("marker"))
            awaitPromise(loader.importLibrary("places"))
            awaitPromise(loader.importLibrary("geometry"))
            librariesLoaded = true
            Napier.d("MapController: All libraries loaded via @googlemaps/js-api-loader")
        }

        private suspend fun awaitPromise(promise: dynamic): dynamic =
            suspendCoroutine { cont ->
                promise.then(
                    { result: dynamic -> cont.resume(result) },
                    { error: dynamic ->
                        cont.resumeWithException(
                            Exception(error?.message?.toString() ?: "Promise rejected")
                        )
                    }
                )
            }
    }
}
