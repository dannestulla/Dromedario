# WebApp Refactor - Planning Phase

## Context

This is a Kotlin Multiplatform project (Android + Kotlin/JS web + Ktor server) for route planning with Google Maps. The web client lives in `composeApp/src/jsMain/` and uses Compose HTML + Google Maps JavaScript API.

The main problem is `MapController.kt` — a monolithic class that mixes too many concerns. This document describes a refactoring to split it into focused units, adopt idiomatic Kotlin/JS coroutine-Promise interop, and use Koin DI where it adds clarity.

---

## Current State

### File: `composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/MapController.kt`

This single class currently handles ALL of:
1. **Google Maps API loading** — `@JsModule` external declaration, `Reflect.construct` to instantiate the npm `Loader`, importing 4 libraries
2. **Map lifecycle** — creating `google.maps.Map`, centering, zooming
3. **Marker management** — adding, clearing, highlighting markers
4. **Polyline drawing** — from waypoints and from encoded polylines
5. **Places Autocomplete** — creating `PlaceAutocompleteElement`, attaching event listener
6. **Promise utility** — hand-rolled `awaitPromise(dynamic)` using `suspendCoroutine`

The class uses a **companion object factory pattern**: the constructor is `private`, and `MapController.create(containerId)` loads the API first, then constructs. This makes the class confusing because the companion object mixes factory, loader, and utility responsibilities.

### File: `composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/MapScreen.kt`

The `WebApp` composable manages `MapController` creation and autocomplete attachment through multiple `LaunchedEffect` blocks. It tracks `mapController`, `searchInputRef`, and `autocompleteAttached` as mutable state.

### File: `composeApp/src/jsMain/kotlin/br/gohan/dromedario/DiModules.kt`

Koin web module currently registers only `HttpClient`. The shared module (in `shared/src/commonMain/.../DIModules.kt`) registers `ClientSharedViewModel`.

### Key Technical Details

- **npm dependency**: `@googlemaps/js-api-loader` v1.16.8 (declared in `composeApp/build.gradle.kts` jsMain dependencies)
- **API key**: hardcoded in `composeApp/src/jsMain/kotlin/br/gohan/dromedario/JsSecrets.kt` as `const val MAPS_API_KEY`
- **Kotlin/JS IR backend**: `@JsNonModule` is NOT supported. Only `@JsModule` works.
- The npm package exports a `Loader` **class** (named export, not default). Access via `JsApiLoaderModule.asDynamic().Loader`
- `Loader` does NOT have a static `setOptions` method in v1.16.8. Must use `new Loader({apiKey, version})` constructor. Since `js()` can't reference Kotlin variables in IR (name mangling), use `Reflect.construct(LoaderClass, [options])`.
- **PlaceAutocompleteElement** uses `gmp-select` event (NOT the old `gmp-placeselect`). Event provides `event.placePrediction.toPlace()` then `.fetchFields({fields: [...]})`.
- `kotlinx-coroutines-core` v1.10.2 (already a dependency via shared module) provides `Promise<T>.await()` on JS target. Import: `kotlinx.coroutines.await`. Cast dynamic promises: `(dynamicPromise as Promise<dynamic>).await()`.

---

## Target State

### New File Structure

```
composeApp/src/jsMain/kotlin/br/gohan/dromedario/
├── DiModules.kt              (UPDATED - register GoogleMapsLoader)
├── JsSecrets.kt              (NO CHANGE)
├── main.kt                   (NO CHANGE)
├── auth/
│   ├── AuthRepository.kt     (NO CHANGE)
│   └── LoginScreen.kt        (NO CHANGE)
└── map/
    ├── GoogleMapsLoader.kt    (NEW)
    ├── MapController.kt       (SIMPLIFIED)
    ├── PlacesAutocomplete.kt  (NEW)
    ├── MapScreen.kt           (UPDATED)
    └── components/
        └── MapComponents.kt   (NO CHANGE)
```

---

## Step-by-Step Implementation

### Step 1: Create `GoogleMapsLoader.kt`

**File:** `composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/GoogleMapsLoader.kt`

**Responsibility:** Load the Google Maps JavaScript API exactly once.

```kotlin
package br.gohan.dromedario.map

import br.gohan.dromedario.MAPS_API_KEY
import io.github.aakira.napier.Napier
import kotlinx.coroutines.await
import kotlin.js.Promise

@JsModule("@googlemaps/js-api-loader")
external object JsApiLoaderModule

class GoogleMapsLoader {
    private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return

        val LoaderClass = JsApiLoaderModule.asDynamic().Loader

        val options = js("{}")
        options.apiKey = MAPS_API_KEY
        options.version = "weekly"

        val args = js("[]")
        args.push(options)
        val loader = js("Reflect").construct(LoaderClass, args)

        (loader.importLibrary("maps") as Promise<dynamic>).await()
        (loader.importLibrary("marker") as Promise<dynamic>).await()
        (loader.importLibrary("places") as Promise<dynamic>).await()
        (loader.importLibrary("geometry") as Promise<dynamic>).await()

        loaded = true
        Napier.d("GoogleMapsLoader: All libraries loaded")
    }
}
```

**Key points:**
- It's a `class` (not `object`) so Koin manages the singleton lifecycle
- `ensureLoaded()` is idempotent — safe to call from multiple coroutines
- Uses `(promise as Promise<dynamic>).await()` from `kotlinx.coroutines` — no custom `awaitPromise` needed
- The `@JsModule` external declaration lives here since it's only needed for loading

### Step 2: Create `PlacesAutocomplete.kt`

**File:** `composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/PlacesAutocomplete.kt`

**Responsibility:** Create and attach a Google Places autocomplete element to a DOM container.

```kotlin
package br.gohan.dromedario.map

import io.github.aakira.napier.Napier

fun attachPlacesAutocomplete(
    containerElement: dynamic,
    onPlaceSelected: (address: String, latitude: Double, longitude: Double) -> Unit
) {
    val autocompleteElement = js("new google.maps.places.PlaceAutocompleteElement()")
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
    Napier.d("PlacesAutocomplete: Attached to container")
}
```

**Key points:**
- Top-level function, no class needed
- Does NOT depend on `MapController` or the map instance (it never did)
- Uses `gmp-select` event (current API v3.63+), NOT the deprecated `gmp-placeselect`
- Uses `placePrediction.toPlace()` → `fetchFields()` pattern (new API)

### Step 3: Simplify `MapController.kt`

**File:** `composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/MapController.kt`

**Responsibility:** Control a `google.maps.Map` instance — markers, polylines, bounds.

```kotlin
package br.gohan.dromedario.map

import br.gohan.dromedario.data.Waypoint
import io.github.aakira.napier.Napier
import kotlinx.browser.document

class MapController(containerId: String) {
    private val map: dynamic
    private val markers = mutableListOf<dynamic>()
    private var polyline: dynamic = null

    init {
        val container = document.getElementById(containerId)
            ?: throw IllegalStateException("Map container not found: $containerId")

        val mapOptions = js("{}")
        mapOptions.center = js("({ lat: -30.05, lng: -51.20 })")
        mapOptions.zoom = 12

        map = js("new google.maps.Map(container, mapOptions)")
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

        polyline = js("new google.maps.Polyline(polylineOptions)")
        polyline.setMap(map)
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

        val marker = js("new google.maps.Marker(markerOptions)")
        markers.add(marker)
    }

    private fun clearMarkers() {
        markers.forEach { marker -> marker.setMap(null) }
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

        polyline = js("new google.maps.Polyline(polylineOptions)")
        polyline.setMap(map)
    }

    private fun fitBounds(waypoints: List<Waypoint>) {
        val bounds = js("new google.maps.LatLngBounds()")
        waypoints.forEach { wp ->
            val point = js("{}")
            point.lat = wp.latitude
            point.lng = wp.longitude
            bounds.extend(point)
        }
        map.fitBounds(bounds, 50)
    }
}
```

**What was removed:**
- `@JsModule` external declaration → moved to `GoogleMapsLoader.kt`
- `companion object` with `create()` factory, `loadGoogleMapsIfNeeded()`, `awaitPromise()` → replaced by `GoogleMapsLoader`
- `attachAutocomplete()` and `createPlaceAutocompleteElement()` → moved to `PlacesAutocomplete.kt`
- Private `create*` wrapper functions → inlined as `js("new google.maps.*()")` directly

**What changed:**
- Constructor is now **public** (no more private + companion factory)
- Caller must ensure Google Maps is loaded before constructing

### Step 4: Update `DiModules.kt`

**File:** `composeApp/src/jsMain/kotlin/br/gohan/dromedario/DiModules.kt`

```kotlin
package br.gohan.dromedario

import br.gohan.dromedario.map.GoogleMapsLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val webModule = module {
    single<HttpClient> {
        HttpClient(Js) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    single { GoogleMapsLoader() }
}
```

**Change:** Added `single { GoogleMapsLoader() }`. This registers it as a singleton so all composables share the same instance and the "loaded" flag is consistent.

### Step 5: Update `MapScreen.kt`

**File:** `composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/MapScreen.kt`

```kotlin
package br.gohan.dromedario.map

import androidx.compose.runtime.*
import br.gohan.dromedario.map.components.ActionButtons
import br.gohan.dromedario.map.components.MyLocationButton
import br.gohan.dromedario.map.components.NavigationStatus
import br.gohan.dromedario.map.components.TripStatus
import br.gohan.dromedario.map.components.WaypointList
import br.gohan.dromedario.presenter.ClientSharedViewModel
import io.github.aakira.napier.Napier
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.koin.compose.koinInject

@Composable
fun WebApp(token: String, viewModel: ClientSharedViewModel = koinInject()) {
    val mapsLoader: GoogleMapsLoader = koinInject()
    val routeState by viewModel.incomingFlow.collectAsState()

    var mapController by remember { mutableStateOf<MapController?>(null) }
    var searchInputRef by remember { mutableStateOf<dynamic>(null) }
    var autocompleteAttached by remember { mutableStateOf(false) }
    val latestWaypoints = rememberUpdatedState(routeState.waypoints)

    // Start WebSocket connection with token
    LaunchedEffect(token) {
        val currentHost = window.location.host
        val wsProtocol = if (window.location.protocol == "https:") "wss" else "ws"
        val wsUrl = "$wsProtocol://$currentHost/ws?token=$token"
        viewModel.startWebSocket(wsUrl)
    }

    // Load Google Maps API and initialize map
    LaunchedEffect(Unit) {
        try {
            mapsLoader.ensureLoaded()
            mapController = MapController("map-container")
        } catch (e: Exception) {
            Napier.e("Failed to initialize map: ${e.message}")
        }
    }

    // Update map when waypoints change
    LaunchedEffect(routeState.waypoints) {
        mapController?.updateWaypoints(routeState.waypoints)
    }

    // Attach Places Autocomplete when search input is ready
    LaunchedEffect(searchInputRef) {
        if (autocompleteAttached) return@LaunchedEffect
        val input = searchInputRef ?: return@LaunchedEffect

        try {
            mapsLoader.ensureLoaded()
            attachPlacesAutocomplete(input) { address, lat, lng ->
                val nextIndex = (latestWaypoints.value.maxOfOrNull { it.index } ?: -1) + 1
                viewModel.sendMessage(address, nextIndex, lat, lng)
            }
            autocompleteAttached = true
        } catch (e: Exception) {
            Napier.e("Failed to attach autocomplete: ${e.message}")
        }
    }

    // UI (unchanged)
    Div({
        style {
            padding(16.px)
            maxWidth(400.px)
            height(100.vh)
            overflowY("auto")
            backgroundColor(Color.white)
        }
    }) {
        H1({
            style {
                marginBottom(16.px)
                color(Color("#333"))
            }
        }) {
            Text("Dromedario Route Planner")
        }

        TripStatus(routeState.waypoints.size)
        WaypointList(routeState.waypoints, onRemove = { index ->
            viewModel.deleteMessage(index)
        })

        Div({
            style { marginBottom(16.px) }
        }) {
            H3 { Text("Add Waypoint") }
            Div({
                style { width(100.percent) }
                ref { element ->
                    searchInputRef = element
                    onDispose { searchInputRef = null }
                }
            })
        }

        ActionButtons(routeState.waypoints)
        MyLocationButton(mapController)
        NavigationStatus()
    }
}
```

**Key changes:**
- Injects `GoogleMapsLoader` via `koinInject()`
- Map init: `mapsLoader.ensureLoaded()` then `MapController("map-container")` — two clear steps
- Autocomplete `LaunchedEffect` depends only on `searchInputRef` (not `mapController`), calls `mapsLoader.ensureLoaded()` + `attachPlacesAutocomplete()`
- Removed unused imports (`suspendCoroutine`, `resume`, `resumeWithException`)

---

## What Gets Deleted

- `MapController.companion object` (entire block: `create()`, `loadGoogleMapsIfNeeded()`, `awaitPromise()`)
- `MapController.attachAutocomplete()` method
- `MapController.createPlaceAutocompleteElement()` method
- `MapController.createMap()`, `createMarker()`, `createPolyline()`, `createLatLngBounds()` private wrappers (inlined as direct `js()` calls)
- `@JsModule` / `external object JsApiLoaderModule` from `MapController.kt` (moved to `GoogleMapsLoader.kt`)

---

## Why These Decisions

| Decision | Rationale |
|---|---|
| `GoogleMapsLoader` as a Koin `single` | Makes the dependency graph visible in `DiModules.kt`. Injected via `koinInject()` in composables. |
| `MapController` NOT in Koin | It's bound to a specific DOM element ID. Koin factories with parameters add complexity without benefit here. |
| `attachPlacesAutocomplete` as top-level function | It never used the `map` instance. It only needs the Places library loaded. Simplest possible API. |
| `Promise<dynamic>.await()` instead of custom `awaitPromise` | `kotlinx-coroutines-core` v1.10.2 (already a dependency) provides this on JS target. Import: `kotlinx.coroutines.await`. Uses `suspendCancellableCoroutine` internally (supports cancellation). |
| Public `MapController` constructor | The companion factory only existed because loading was embedded. With loading extracted, the constructor can be public. Caller ensures `ensureLoaded()` first. |

---

## Verification

After implementing all changes:

1. Run `gradlew composeApp:jsBrowserDevelopmentRun` (or the webpack dev server)
2. Open browser, log in
3. Verify: Google Map renders in the right panel
4. Verify: Places Autocomplete input appears under "Add Waypoint"
5. Verify: Typing an address shows autocomplete suggestions
6. Verify: Selecting a suggestion adds a waypoint (marker on map + entry in list)
7. Verify: Adding 2+ waypoints draws a polyline between them
8. Check browser console for `GoogleMapsLoader: All libraries loaded` and `MapController: Map initialized` log messages
