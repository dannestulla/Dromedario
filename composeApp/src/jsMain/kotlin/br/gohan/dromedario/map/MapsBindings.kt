package br.gohan.dromedario.map

import org.w3c.dom.Element
import kotlin.js.Promise

// ── Construction helpers ──

fun newMap(container: Element, options: MapOptions): Map =
    js("new google.maps.Map(container, options)").unsafeCast<Map>()

fun newMarker(options: MarkerOptions): Marker =
    js("new google.maps.Marker(options)").unsafeCast<Marker>()

fun newPolyline(options: PolylineOptions): Polyline =
    js("new google.maps.Polyline(options)").unsafeCast<Polyline>()

fun newLatLngBounds(): LatLngBounds =
    js("new google.maps.LatLngBounds()").unsafeCast<LatLngBounds>()

fun newGeocoder(): Geocoder =
    js("new google.maps.Geocoder()").unsafeCast<Geocoder>()

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

fun markerOptions(
    position: LatLngLiteral,
    map: Map,
    label: String,
    title: String,
    draggable: Boolean = false
): MarkerOptions {
    val obj = js("{}").unsafeCast<MarkerOptions>()
    obj.position = position
    obj.map = map
    obj.label = label
    obj.title = title
    obj.draggable = draggable
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
