package br.gohan.dromedario.map

import org.w3c.dom.Element
import kotlin.js.Promise

// Typed Kotlin/JS external declarations for the Google Maps JS API surface we use.

// ── Core types ──

external interface LatLngLiteral {
    var lat: Double
    var lng: Double
}

external interface LatLng {
    fun lat(): Double
    fun lng(): Double
}

external interface LatLngBounds {
    fun extend(point: LatLngLiteral): LatLngBounds
}

// ── Map ──

external interface MapOptions {
    var center: LatLngLiteral
    var zoom: Int
}

external interface GoogleMap {
    fun setCenter(center: LatLngLiteral)
    fun setZoom(zoom: Int)
    fun fitBounds(bounds: LatLngBounds, padding: Int)
}

// ── Marker ──

external interface MarkerOptions {
    var position: LatLngLiteral
    var map: GoogleMap
    var label: String
    var title: String
}

external interface IconOptions {
    var url: String
}

external interface Marker {
    fun setMap(map: GoogleMap?)
    fun setIcon(icon: IconOptions?)
}

// ── Polyline ──

external interface PolylineOptions {
    var path: dynamic
    var geodesic: Boolean
    var strokeColor: String
    var strokeOpacity: Double
    var strokeWeight: Int
}

external interface Polyline {
    fun setMap(map: GoogleMap?)
}

// ── Directions ──

external interface DirectionsService {
    fun route(request: DirectionsRequest, callback: (dynamic, String) -> Unit)
}

external interface DirectionsRenderer {
    fun setMap(map: GoogleMap?)
    fun setDirections(result: dynamic)
    fun setOptions(options: dynamic)
}

external interface DirectionsRequest {
    var origin: LatLngLiteral
    var destination: LatLngLiteral
    var waypoints: Array<dynamic>
    var travelMode: String
    var optimizeWaypoints: Boolean
}

// ── Places ──

external interface PlacePrediction {
    fun toPlace(): Place
}

external interface Place {
    val formattedAddress: String?
    val location: LatLng?
    fun fetchFields(options: FetchFieldsOptions): Promise<Place>
}

external interface FetchFieldsOptions {
    var fields: Array<String>
}

// ── Construction helpers ──

fun newGoogleMap(container: Element, options: MapOptions): GoogleMap =
    js("new google.maps.Map(container, options)").unsafeCast<GoogleMap>()

fun newMarker(options: MarkerOptions): Marker =
    js("new google.maps.Marker(options)").unsafeCast<Marker>()

fun newPolyline(options: PolylineOptions): Polyline =
    js("new google.maps.Polyline(options)").unsafeCast<Polyline>()

fun newLatLngBounds(): LatLngBounds =
    js("new google.maps.LatLngBounds()").unsafeCast<LatLngBounds>()

fun newDirectionsService(): DirectionsService =
    js("new google.maps.DirectionsService()").unsafeCast<DirectionsService>()

fun newDirectionsRenderer(): DirectionsRenderer =
    js("new google.maps.DirectionsRenderer()").unsafeCast<DirectionsRenderer>()

fun newPlaceAutocompleteElement(options: dynamic = js("{}")): Element =
    js("new google.maps.places.PlaceAutocompleteElement(options)").unsafeCast<Element>()

fun decodePath(encoded: String): dynamic =
    js("google.maps.geometry.encoding.decodePath(encoded)")

// ── Literal builders ──

fun latLng(lat: Double, lng: Double): LatLngLiteral {
    val obj = js("{}").unsafeCast<LatLngLiteral>()
    obj.lat = lat
    obj.lng = lng
    return obj
}

fun mapOptions(center: LatLngLiteral, zoom: Int): MapOptions {
    val obj = js("{}").unsafeCast<MapOptions>()
    obj.center = center
    obj.zoom = zoom
    return obj
}

fun markerOptions(position: LatLngLiteral, map: GoogleMap, label: String, title: String): MarkerOptions {
    val obj = js("{}").unsafeCast<MarkerOptions>()
    obj.position = position
    obj.map = map
    obj.label = label
    obj.title = title
    return obj
}

fun iconOptions(url: String): IconOptions {
    val obj = js("{}").unsafeCast<IconOptions>()
    obj.url = url
    return obj
}

fun polylineOptions(path: dynamic, color: String = "#4285F4"): PolylineOptions {
    val obj = js("{}").unsafeCast<PolylineOptions>()
    obj.path = path
    obj.geodesic = true
    obj.strokeColor = color
    obj.strokeOpacity = 1.0
    obj.strokeWeight = 4
    return obj
}

fun fetchFieldsOptions(vararg fields: String): FetchFieldsOptions {
    val obj = js("{}").unsafeCast<FetchFieldsOptions>()
    obj.fields = arrayOf(*fields)
    return obj
}
