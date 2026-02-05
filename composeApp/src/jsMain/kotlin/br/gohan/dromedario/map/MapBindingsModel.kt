package br.gohan.dromedario.map

import kotlin.js.Promise

// Kotlin/JS bindings for the Google Maps JS API surface we use.

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

external interface Map {
    fun setCenter(center: LatLngLiteral)
    fun setZoom(zoom: Int)
    fun fitBounds(bounds: LatLngBounds, padding: Int)
    fun addListener(event: String, handler: (dynamic) -> Unit)
}

// ── Marker ──

external interface MarkerOptions {
    var position: LatLngLiteral
    var map: Map
    var label: String
    var title: String
    var draggable: Boolean
}

external interface IconOptions {
    var url: String
}

external interface Marker {
    fun setMap(map: Map?)
    fun setIcon(icon: IconOptions?)
    fun addListener(event: String, handler: () -> Unit)
    fun getPosition(): LatLng
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
    fun setMap(map: Map?)
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

// ── Geocoder ──

external interface Geocoder {
    fun geocode(request: dynamic, callback: (dynamic, dynamic) -> Unit)
}

external interface GeocoderResult {
    val formatted_address: String
}