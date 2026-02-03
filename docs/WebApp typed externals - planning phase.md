# WebApp Typed Externals — Planning Phase

## Context

After the refactor, `MapController.kt` and `PlacesAutocomplete.kt` use `dynamic` for all Google Maps objects. This means zero compile-time checking — a typo like `map.setZom(12)` compiles fine and fails silently at runtime.

This document proposes adding `external interface` declarations that type the Google Maps API surface we actually use. Construction still uses `js()` (required for global namespace classes in Kotlin/JS IR), but the returned objects are cast to typed interfaces, so all subsequent method calls are checked by the compiler.

### What stays `dynamic`

- **`GoogleMapsLoader.kt`** — Called once at startup. The npm loader interaction is too irregular to benefit from typing.
- **`js()` construction calls** — Kotlin/JS IR can't reliably use `new` on global namespace classes through external declarations. We keep `js("new google.maps.Map(...)")` but cast the result.

### What gets typed

Everything else: map instance, markers, polylines, bounds, places, lat/lng literals, and all option bags.

---

## New File: `GoogleMapsExternals.kt`

**File:** `composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/GoogleMapsExternals.kt`

All external declarations in one file. These are structural interfaces — no runtime cost, just compiler hints.

```kotlin
package br.gohan.dromedario.map

import org.w3c.dom.Element
import kotlin.js.Promise

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
    var path: dynamic          // Array<LatLngLiteral> or decoded path from geometry API
    var geodesic: Boolean
    var strokeColor: String
    var strokeOpacity: Double
    var strokeWeight: Int
}

external interface Polyline {
    fun setMap(map: GoogleMap?)
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

// ── Geometry ──

external interface GeometryEncoding {
    fun decodePath(encodedPath: String): dynamic
}
```

### Helper functions

At the bottom of the same file, factory functions that bridge `js()` construction to typed returns:

```kotlin
// ── Construction helpers ──
// js() handles `new` on global namespace classes; unsafeCast types the result.

fun newGoogleMap(container: Element, options: MapOptions): GoogleMap =
    js("new google.maps.Map(container, options)").unsafeCast<GoogleMap>()

fun newMarker(options: MarkerOptions): Marker =
    js("new google.maps.Marker(options)").unsafeCast<Marker>()

fun newPolyline(options: PolylineOptions): Polyline =
    js("new google.maps.Polyline(options)").unsafeCast<Polyline>()

fun newLatLngBounds(): LatLngBounds =
    js("new google.maps.LatLngBounds()").unsafeCast<LatLngBounds>()

fun newPlaceAutocompleteElement(): Element =
    js("new google.maps.places.PlaceAutocompleteElement()").unsafeCast<Element>()

fun decodePath(encoded: String): dynamic =
    js("google.maps.geometry.encoding.decodePath(encoded)")

// ── Literal builders ──
// Create typed option objects from plain JS objects.

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
```

---

## How `MapController.kt` Changes

Before (dynamic):
```kotlin
private val map: dynamic
private val markers = mutableListOf<dynamic>()
private var polyline: dynamic = null
```

After (typed):
```kotlin
private val map: GoogleMap
private val markers = mutableListOf<Marker>()
private var polyline: Polyline? = null
```

Full updated file:

```kotlin
package br.gohan.dromedario.map

import br.gohan.dromedario.data.Waypoint
import io.github.aakira.napier.Napier
import kotlinx.browser.document

class MapController(containerId: String) {
    private val map: GoogleMap
    private val markers = mutableListOf<Marker>()
    private var polyline: Polyline? = null

    init {
        val container = document.getElementById(containerId)
            ?: throw IllegalStateException("Map container not found: $containerId")

        map = newGoogleMap(container, mapOptions(
            center = latLng(-30.05, -51.20),
            zoom = 12
        ))
        Napier.d("MapController: Map initialized")
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
    }

    private fun drawPolyline(waypoints: List<Waypoint>) {
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
```

### What changed

| Before | After |
|---|---|
| `private val map: dynamic` | `private val map: GoogleMap` |
| `mutableListOf<dynamic>()` | `mutableListOf<Marker>()` |
| `var polyline: dynamic = null` | `var polyline: Polyline? = null` |
| `js("new google.maps.Map(container, mapOptions)")` | `newGoogleMap(container, mapOptions(...))` |
| `js("new google.maps.Marker(markerOptions)")` | `newMarker(markerOptions(...))` |
| `val center = js("{}"); center.lat = ...; center.lng = ...` | `latLng(latitude, longitude)` |
| `polyline?.setMap(null)` on `dynamic` (no compile check) | `polyline?.setMap(null)` on `Polyline?` (compile-checked) |

---

## How `PlacesAutocomplete.kt` Changes

```kotlin
package br.gohan.dromedario.map

import io.github.aakira.napier.Napier
import org.w3c.dom.Element

fun attachPlacesAutocomplete(
    containerElement: Element,
    onPlaceSelected: (address: String, latitude: Double, longitude: Double) -> Unit
) {
    val autocompleteElement = newPlaceAutocompleteElement()
    containerElement.appendChild(autocompleteElement)

    val callback: (dynamic) -> Unit = { event ->
        val placePrediction: PlacePrediction? = event.placePrediction?.unsafeCast<PlacePrediction>()
        placePrediction?.let { prediction ->
            val place = prediction.toPlace()
            place.fetchFields(fetchFieldsOptions("formattedAddress", "location")).then { resolved ->
                val location: LatLng? = resolved.location
                location?.let {
                    val address = resolved.formattedAddress ?: "Unknown location"
                    onPlaceSelected(address, it.lat(), it.lng())
                }
            }
        }
    }
    autocompleteElement.addEventListener("gmp-select", callback)
    Napier.d("PlacesAutocomplete: Attached to container")
}
```

### What changed

| Before | After |
|---|---|
| `containerElement: dynamic` | `containerElement: Element` |
| `event.placePrediction` unchecked | `event.placePrediction?.unsafeCast<PlacePrediction>()` |
| `place.formattedAddress` on dynamic | `resolved.formattedAddress` on typed `Place` |
| `location.lat()` on dynamic | `it.lat()` on typed `LatLng` |
| Nested null checks with `!= undefined` | Kotlin-native `?.let {}` and `?:` |

---

## What This Does NOT Change

- **`GoogleMapsLoader.kt`** — Stays fully dynamic. The npm loader interop is one-time setup code.
- **`MapScreen.kt`** — No changes needed. It only touches `MapController` and `attachPlacesAutocomplete` through their public API, which stays the same.
- **`DiModules.kt`** — No changes needed.

---

## Risk: `js()` and Name Mangling in IR

Kotlin/JS IR may mangle local variable names, which can break `js()` calls that reference them. For example:

```kotlin
fun newGoogleMap(container: Element, options: MapOptions): GoogleMap =
    js("new google.maps.Map(container, options)").unsafeCast<GoogleMap>()
```

If IR renames `container` to `container_0`, this breaks at runtime. Two mitigations:

1. **Test immediately** — If `compileKotlinJs` succeeds but the map doesn't render, this is the likely cause.
2. **Fallback pattern** — If mangling hits, switch to dynamic intermediate:
   ```kotlin
   fun newGoogleMap(container: Element, options: MapOptions): GoogleMap {
       val c: dynamic = container
       val o: dynamic = options
       return js("new google.maps.Map(c, o)").unsafeCast<GoogleMap>()
   }
   ```
   Assigning to `dynamic` locals tends to prevent mangling since the compiler treats them as JS-native.

This is the main thing to watch during verification.

---

## Summary of Changes

| File | Action |
|---|---|
| `map/GoogleMapsExternals.kt` | **NEW** — All external interfaces + construction helpers |
| `map/MapController.kt` | **UPDATE** — Replace `dynamic` fields with typed interfaces, use helper functions |
| `map/PlacesAutocomplete.kt` | **UPDATE** — Type the parameter and cast event data |
| `map/GoogleMapsLoader.kt` | No change |
| `map/MapScreen.kt` | No change |
| `DiModules.kt` | No change |

## Verification

Same as the refactor plan:

1. `gradlew composeApp:compileKotlinJs` — must pass
2. Run dev server, log in, verify map renders
3. Verify autocomplete works (type address, select, marker appears)
4. Check browser console for errors (especially `undefined is not a function` which signals a mangling issue)
