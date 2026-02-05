# Export Route Groups to Google Maps — Web App (Compose Multiplatform)

## Goal

Add an export screen accessible from mobile browsers. The user opens it on their phone, receives the finalized route from the server, and taps a button per route group to open Google Maps in navigation mode. No app store needed — it's a web page.

The export screen is built with **Compose Multiplatform (Canvas)** using a `wasmJs` target, so the same UI code can be reused for a native Android app in the future.

## Architecture: Two Web Apps, One Server

```
/           → Planning dashboard (existing, jsMain, Compose for Web DOM)
                 Uses Google Maps JS API, Places Autocomplete, etc.
                 Accessed from desktop browser

/export     → Export screen (NEW, wasmJs, Compose Multiplatform Canvas)
                 Simple UI: group buttons → opens Google Maps URL
                 Accessed from mobile phone browser
                 Same compose.material3 code reusable for native Android app
```

Both are served from the same Ktor server. Both connect to the same WebSocket and receive the same state updates.

## Current State

### What already works
- **Web planning app** (`jsMain`): add/remove/reorder/optimize waypoints, finalize route
- **Server** `FINALIZE_ROUTE` handler: splits route into groups of max 9 waypoints, broadcasts `SYNC_STATE` with full `TripSession`
- **Shared data classes**: `TripSession`, `RouteGroup`, `Waypoint`, `EventType` — all in `shared/commonMain`
- **`ClientSharedViewModel`**: WebSocket connection, receives `RouteStateModel` updates

### What's missing
- `ClientSharedViewModel` only parses `RouteStateModel` — doesn't handle `SYNC_STATE` messages containing `TripSession` with groups
- No `wasmJs` target in the project
- No export screen UI
- No way to open a Google Maps URL from shared code (`expect`/`actual`)

## UI Mockups

### Export screen — groups available:
```
┌──────────────────────────────────────┐
│            Dromedario                │
│                                      │
│      Export to Google Maps           │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  Group 1                  ▶   │  │
│  │  5 stops                      │  │
│  │                               │  │
│  │  From: Rua Padre Chagas, 300  │  │
│  │    To: Av. Ipiranga, 6681     │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  Group 2                  ▶   │  │
│  │  4 stops                      │  │
│  │                               │  │
│  │  From: Av. Ipiranga, 7200     │  │
│  │    To: Rua Voluntarios, 55    │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  Group 3                  ▶   │  │
│  │  3 stops                      │  │
│  │                               │  │
│  │  From: Av. Bento Goncalves    │  │
│  │    To: Rua da Republica, 121  │  │
│  └────────────────────────────────┘  │
│                                      │
│         ● Connected                  │
└──────────────────────────────────────┘
```

### Export screen — waiting for route:
```
┌──────────────────────────────────────┐
│            Dromedario                │
│                                      │
│      Export to Google Maps           │
│                                      │
│                                      │
│                                      │
│      Waiting for finalized           │
│      route from web app...           │
│                                      │
│      Plan your route on the          │
│      web dashboard, then tap         │
│      "Finalize Route"                │
│                                      │
│                                      │
│                                      │
│         ● Connected                  │
└──────────────────────────────────────┘
```

## Implementation Plan

### Step 1: Add `wasmJs` target to `composeApp`

**File:** `composeApp/build.gradle.kts`

Add a `wasmJs` target alongside the existing `js` and `android` targets. Create an intermediate `canvasMain` source set shared between `wasmJs` and `android` — this is where the export screen composables live.

**Changes to build.gradle.kts:**
```kotlin
kotlin {
    androidTarget { ... }   // existing
    js(IR) { ... }          // existing — planning dashboard (DOM)

    wasmJs {                // NEW — export screen (Canvas)
        browser {
            commonWebpackConfig {
                outputFileName = "exportApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies { ... }   // existing (compose.runtime, ktor, etc.)

        // NEW: shared source set for Canvas-based Compose UI (Android + wasmJs)
        val canvasMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(compose.material3)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.components.resources)
            }
        }

        androidMain {
            dependsOn(canvasMain)         // Android inherits canvas composables
            dependencies { ... }          // existing android deps
        }

        wasmJsMain {
            dependsOn(canvasMain)         // wasmJs inherits canvas composables
            dependencies {
                implementation(libs.ktor.client.js)  // or ktor-client-wasm if needed
                implementation(libs.koin.compose)
            }
        }

        jsMain.dependencies { ... }      // existing — stays DOM-based
    }
}
```

**New source set directories:**
```
composeApp/src/
├── commonMain/          (existing — runtime, ktor, shared logic)
├── canvasMain/          (NEW — ExportScreen composables, compose.material3)
├── androidMain/         (existing — Android-specific code)
├── wasmJsMain/          (NEW — wasmJs entry point, window.open actual)
└── jsMain/              (existing — planning dashboard, DOM)
```

### Step 2: Handle `SYNC_STATE` in `ClientSharedViewModel`

**File:** `shared/src/commonMain/kotlin/br/gohan/dromedario/presenter/ClientSharedViewModel.kt`

Currently the WebSocket handler only decodes raw `RouteStateModel`. It needs to also detect `SYNC_STATE` messages which wrap a `TripSession` inside a `MessageModel` envelope.

**Changes:**
- Add `_tripSessionFlow: MutableStateFlow<TripSession?>(null)` and expose as `tripSessionFlow`
- In the WebSocket receive loop, try parsing as `MessageModel` first:
  - If `event == SYNC_STATE` → decode `data` as `TripSession`, emit to `_tripSessionFlow`
  - Otherwise → fall back to decoding as `RouteStateModel` into `_incomingFlow` (existing behavior)
- Backward compatible — waypoint updates still work, `SYNC_STATE` is now also captured

### Step 3: Google Maps URL builder + `expect`/`actual` for opening URLs

**File (shared):** `shared/src/commonMain/kotlin/br/gohan/dromedario/presenter/ExportHelper.kt`

Move the Google Maps URI building logic from `MobileViewModel` to shared code so both web and Android can use it.

```kotlin
// shared/commonMain
fun buildGoogleMapsUrl(waypoints: List<Waypoint>): String {
    val origin = waypoints.first()
    val destination = waypoints.last()
    val viaPoints = waypoints.drop(1).dropLast(1)

    return buildString {
        append("https://www.google.com/maps/dir/?api=1")
        append("&origin=${origin.latitude},${origin.longitude}")
        append("&destination=${destination.latitude},${destination.longitude}")
        if (viaPoints.isNotEmpty()) {
            append("&waypoints=")
            append(viaPoints.joinToString("|") { "${it.latitude},${it.longitude}" })
        }
        append("&travelmode=driving")
    }
}

// Platform-specific URL opening
expect fun openExternalUrl(url: String)
```

**Actual implementations:**
- `wasmJsMain`: `kotlinx.browser.window.open(url, "_blank")`
- `androidMain`: stays as `Intent.ACTION_VIEW` (for future native app)
- `jsMain`: not needed (planning app doesn't use this)

Note: `shared` module needs `wasmJs` added to its targets too (it already has `js(IR)` and `jvm`).

### Step 4: Export screen composables

**File:** `composeApp/src/canvasMain/kotlin/br/gohan/dromedario/presenter/ExportScreen.kt`

This is the shared UI code — works on both `wasmJs` (web) and `androidMain` (future native).

```kotlin
@Composable
fun ExportScreen(viewModel: ClientSharedViewModel) {
    val tripSession by viewModel.tripSessionFlow.collectAsState()

    Column {
        Text("Dromedario", style = MaterialTheme.typography.headlineMedium)
        Text("Export to Google Maps", style = MaterialTheme.typography.titleLarge)

        if (tripSession == null || tripSession.groups.isEmpty()) {
            // Waiting state
            Text("Waiting for finalized route from web app...")
        } else {
            // Group buttons
            LazyColumn {
                items(tripSession.groups) { group ->
                    RouteGroupCard(tripSession, group)
                }
            }
        }
    }
}

@Composable
fun RouteGroupCard(trip: TripSession, group: RouteGroup) {
    val stopCount = group.waypointEndIndex - group.waypointStartIndex
    val firstAddress = trip.waypoints[group.waypointStartIndex].address
    val lastAddress = trip.waypoints[group.waypointEndIndex - 1].address
    val groupWaypoints = trip.waypoints.subList(group.waypointStartIndex, group.waypointEndIndex)

    Card(onClick = {
        val url = buildGoogleMapsUrl(groupWaypoints)
        openExternalUrl(url)
    }) {
        Text("Group ${group.index + 1}")        // title
        Text("$stopCount stops")                 // subtitle
        Text("From: $firstAddress")              // start address
        Text("  To: $lastAddress")               // end address
    }
}
```

### Step 5: wasmJs entry point + HTML page

**File:** `composeApp/src/wasmJsMain/kotlin/br/gohan/dromedario/Main.kt`

```kotlin
fun main() {
    Napier.base(DebugAntilog())
    startKoin { modules(wasmModule, sharedModule) }

    CanvasBasedWindow(canvasElementId = "exportCanvas") {
        val viewModel: ClientSharedViewModel = koinInject()

        LaunchedEffect(Unit) {
            val host = window.location.host
            val protocol = if (window.location.protocol == "https:") "wss" else "ws"
            viewModel.startWebSocket("$protocol://$host/ws")
        }

        MaterialTheme {
            ExportScreen(viewModel)
        }
    }
}
```

**File:** `composeApp/src/wasmJsMain/resources/export.html`

Minimal HTML shell for the Canvas-based Compose app:
```html
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Dromedario - Export</title>
</head>
<body>
    <canvas id="exportCanvas" style="width:100%;height:100vh;"></canvas>
    <script src="exportApp.js"></script>
</body>
</html>
```

### Step 6: Serve export page from Ktor server

**File:** `server/src/main/kotlin/br/gohan/dromedario/ServerApp.kt`

Add a static route to serve the wasmJs output at `/export`:

```kotlin
routing {
    // ... existing routes ...

    // Export web app (wasmJs Canvas-based)
    staticResources("/export", "export") {
        default("export.html")
    }

    // Planning web app (existing, must be last)
    staticResources("/", "web") {
        default("index.html")
    }
}
```

The wasmJs build output needs to be copied to `server/src/main/resources/export/` as part of the build process (same pattern as the existing planning app).

### Step 7: WebSocket without auth (for now)

For now, the export screen connects to `/ws` without a token. This requires a small change to the server's WebSocket handler to allow unauthenticated connections temporarily.

**File:** `server/src/main/kotlin/br/gohan/dromedario/ServerApp.kt`

Option A: Skip token validation if no token is provided (simplest for now)
Option B: Add a separate `/ws/export` endpoint with no auth

Auth will be added later as a separate task.

## Data Flow

```
1. User finalizes route on planning dashboard (desktop browser)
2. Planning app → Server: FINALIZE_ROUTE event
3. Server generates groups (max 9 waypoints each), creates TripSession
4. Server → All clients: SYNC_STATE { TripSession }
5. Export app (phone browser) receives SYNC_STATE via WebSocket
6. ClientSharedViewModel parses TripSession, emits to tripSessionFlow
7. ExportScreen renders one card per group with addresses
8. User taps "Group 1" card
9. buildGoogleMapsUrl() constructs:
   google.com/maps/dir/?api=1&origin=...&destination=...&waypoints=...&travelmode=driving
10. window.open(url) opens Google Maps on the phone
    → Android: prompts to open in Google Maps app
    → iOS: opens in browser, user can tap to open Maps
```

## Google Maps URL Format

```
https://www.google.com/maps/dir/?api=1
  &origin={lat},{lng}
  &destination={lat},{lng}
  &waypoints={lat},{lng}|{lat},{lng}|...
  &travelmode=driving
```

- First waypoint in group → `origin`
- Last waypoint in group → `destination`
- Everything in between → `waypoints` (pipe-separated)
- Max 9 per group (Google Maps limit, enforced by server's `generateGroups`)

## What stays unchanged
- Server: `FINALIZE_ROUTE` handler, `generateGroups`, `broadcastSyncState` — all work
- Planning web app (`jsMain`): route planning, finalize button — all works
- Shared data classes: `TripSession`, `RouteGroup`, `Waypoint`, `EventType` — all defined
- Planning web app login flow — stays as-is

## Source Set Diagram

```
                    commonMain
                   (compose.runtime, ktor, viewmodel, data classes)
                  /          |            \
            canvasMain     jsMain        (shared/commonMain)
    (compose.material3)  (compose.html.core)
          /       \          |
   androidMain   wasmJsMain  jsMain
   (maps SDK,    (window.open, (Google Maps JS,
    intents)      export entry) DOM rendering)
```

## Implementation Order

1. **Step 1** — Add `wasmJs` target + `canvasMain` source set to build config
2. **Step 2** — Handle `SYNC_STATE` in `ClientSharedViewModel`
3. **Step 3** — `buildGoogleMapsUrl` in shared + `expect`/`actual` `openExternalUrl`
4. **Step 4** — `ExportScreen` composables in `canvasMain`
5. **Step 5** — wasmJs entry point (`Main.kt` + `export.html`)
6. **Step 6** — Serve from Ktor at `/export`
7. **Step 7** — Allow WebSocket without auth (temporary)

## Future additions (not in this plan)
- **Read-only route map**: add a map to the export screen showing groups as colored polylines. The data (`TripSession` with waypoints) is already available — this is a presentation-layer addition.
- **Auth**: login screen before WebSocket connection
- **Native Android app**: reuse `canvasMain` composables, add `Intent.ACTION_VIEW` actual for `openExternalUrl`
