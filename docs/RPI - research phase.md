# Dromedario Route Planner - Research Document

## RPI Methodology

This project follows the **RPI (Research, Plan, Implement)** methodology:

1. **Research Phase** (this document) - Analyze requirements, explore the codebase, make technical decisions, document architecture and constraints
2. **Planning Phase** - Break down work into executable tasks with clear dependencies, code snippets, and verification steps
3. **Implementation Phase** - A new Claude instance reads the planning document and executes the tasks

Each phase produces a document that serves as input for the next phase. This separation allows for human review between phases and enables efficient handoff between Claude instances.

---

> This document is the output of the research phase. Use it as input for the planning phase.

## Project Overview

**Purpose**: Route planner for delivery drivers to plan trips with many stops and export to Google Maps, bypassing the waypoint limits imposed by Google Maps URLs.

**Problem**: Google Maps URL scheme only supports 9 waypoints (desktop/app) or 3 waypoints (mobile browser). Delivery drivers often need 20+ stops.

**Solution**: Auto-split waypoints into groups of max 9. When user finishes navigating one group (detected via geofencing), a notification prompts them to start the next group.

---

## Current Architecture (As-Is)

### Tech Stack
- **Kotlin Multiplatform** (Android, Web/JS, Server)
- **Compose Multiplatform** (UI)
- **Ktor Server** (WebSocket server, port 8080, static file hosting)
- **Ktor Client** (WebSocket client)
- **MongoDB** (persistence via KMongo)
- **Koin** (dependency injection)
- **Google Maps Routes API v2** (route calculation)
- **Google Maps Compose** (Android map rendering)
- **Google Maps JavaScript API** (Web map rendering)

### Module Structure
```
Dromedario/
├── composeApp/
│   ├── src/androidMain/     # Android app (Maps, ViewModels, Geofencing)
│   └── src/jsMain/          # Web app (Kotlin/JS) - changed from wasmJsMain
├── shared/
│   └── src/commonMain/      # Shared models, ViewModels, UI
└── server/                  # Ktor + MongoDB + Static file hosting
```

### Current Data Model
```kotlin
// shared/src/commonMain/kotlin/br/gohan/dromedario/data/RouteStateModel.kt
@Serializable
data class RouteStateModel(
    @SerialName("_id")
    val id: String = "session",
    val waypoints: List<Waypoint> = emptyList(),
    val updatedAt: Long = Clock.System.now().epochSeconds
)

@Serializable
data class Waypoint(
    val index: Int = 0,
    val address: String,
    val latitude: Double,
    val longitude: Double,
)
```

### WebSocket Protocol
```kotlin
// shared/src/commonMain/kotlin/br/gohan/dromedario/data/MessageModel.kt
@Serializable
data class MessageModel(
    val event: EventType,
    val data: JsonElement? = null
)

@Serializable
enum class EventType {
    ADD_WAYPOINT,
    REMOVE_WAYPOINT
}
```

### Existing Shared UI (Reusable)

**CommonScreen.kt** - Already works on both Android and Web:
```kotlin
@Composable
fun CommonScreen(url: String, viewModel: ClientSharedViewModel = koinViewModel()) {
    val incomingMessages by viewModel.incomingFlow.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn {
            items(incomingMessages.waypoints) { waypoint ->
                Card {
                    Row {
                        Text("Address: ${waypoint.address}, Index: ${waypoint.index}")
                        Button(onClick = { viewModel.deleteMessage(waypoint.index) }) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
        Button(onClick = { /* Add waypoint */ }) {
            Text("add waypoint")
        }
    }
}
```

### Key Files Reference

| Component | Path |
|-----------|------|
| Server entry | `server/src/main/kotlin/br/gohan/dromedario/ServerApp.kt` |
| Database manager | `server/src/main/kotlin/br/gohan/dromedario/DatabaseManager.kt` |
| Shared ViewModel | `shared/src/commonMain/kotlin/br/gohan/dromedario/presenter/ClientSharedViewModel.kt` |
| **Shared UI** | `shared/src/commonMain/kotlin/br/gohan/dromedario/presenter/CommonScreen.kt` |
| Android ViewModel | `composeApp/src/androidMain/kotlin/br/gohan/dromedario/presenter/MobileViewModel.kt` |
| Android UI | `composeApp/src/androidMain/kotlin/br/gohan/dromedario/presenter/MobileApp.kt` |
| Web UI | `composeApp/src/wasmJsMain/kotlin/br/gohan/dromedario/WebApp.kt` |
| Web entry | `composeApp/src/wasmJsMain/kotlin/br/gohan/dromedario/main.kt` |
| Route models | `composeApp/src/androidMain/kotlin/br/gohan/dromedario/data/model/RouteModels.kt` |
| Mobile repository | `composeApp/src/androidMain/kotlin/br/gohan/dromedario/data/MobileRepository.kt` |
| Shared DI | `shared/src/commonMain/kotlin/br/gohan/dromedario/DIModules.kt` |
| Android DI | `composeApp/src/androidMain/kotlin/br/gohan/dromedario/di/DiModules.kt` |
| Constants | `shared/src/commonMain/kotlin/br/gohan/dromedario/Constants.kt` |

### What's Working
- [x] WebSocket real-time sync between clients
- [x] MongoDB persistence (single session)
- [x] Add/remove waypoints from web and mobile
- [x] Google Maps Routes API integration (polyline calculation)
- [x] Route display on Android map
- [x] Export to Google Maps via URL intent
- [x] Shared Compose UI (CommonScreen) works on both platforms

---

## Requirements (To-Be)

### Functional Requirements

#### FR1: Route Group Auto-Creation
- When user finalizes route planning, automatically split waypoints into groups of max 9
- Groups created on client side, synced to server
- User does not manually manage groups

#### FR2: Sequential Group Navigation
- Export only the active group to Google Maps
- When user arrives at last stop (geofence triggers), advance to next group
- Track progress: which groups are completed

#### FR3: Background Location Monitoring & Push Notifications
- **Critical**: When route exports to Google Maps, Dromedario goes to background
- User navigates entirely within Google Maps app (Dromedario not visible)
- Dromedario must use **geofencing** to detect arrival at last waypoint of current group
- When user enters geofence radius of last waypoint:
  - Show local push notification: "Route complete! Start next group?"
  - User taps notification → Dromedario exports next group to Google Maps
- Flow repeats until all groups completed

**Technical approach:**
- Android: Use `GeofencingClient` from Google Play Services Location
- Register geofence for last waypoint of active group only
- Geofence radius: ~100-200 meters
- On `GEOFENCE_TRANSITION_ENTER` → `GeofenceBroadcastReceiver` triggered by Android OS
- Show **LOCAL notification** (no Firebase needed!)
- Notification tap → `PendingIntent` opens Dromedario, exports next group

**Why no Firebase?**
- Geofence triggers happen ON THE DEVICE (Android OS monitors location)
- Local notifications are shown via `NotificationManager`
- No server push needed - everything is local
- Works offline, simpler setup

**Components needed:**
1. `GeofenceManagerHelper` - registers/unregisters geofences with `GeofencingClient`
2. `GeofenceBroadcastReceiver` - receives geofence transition events from Android OS
3. `NotificationHelper` - creates and shows local notifications
4. Notification channel setup (required for Android 8+)

#### FR4: Web Map Display
- Display route on web using Google Maps JavaScript API
- Show all waypoints with markers
- Draw polyline for calculated route
- **Auto-draw path**: When user adds a new waypoint, automatically draw path from last waypoint to new one
- Must stay in sync with mobile (WebSocket)

#### FR5: Trip Persistence
- Store all trips permanently in MongoDB (history)
- Each trip has unique ID
- No UI for trip history needed (just storage)

#### FR6: Route Optimization
- "Optimize Route" button in UI (both web and mobile)
- Uses Google Routes API `optimizeWaypointOrder` parameter
- Reorders waypoints for shortest total distance
- User can manually reorder after optimization if needed

### Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Group size | 9 waypoints max | Google Maps URL limit for app/desktop |
| Web target | Kotlin/JS (not WASM) | Easier debugging, better JS interop |
| Web map library | Google Maps JS API | Consistency with mobile |
| Web hosting | Same Ktor server | Simple deployment, no CORS issues |
| Arrival detection | Geofencing (background) | App is in background during Google Maps navigation |
| Trip history UI | None (storage only) | Keep scope minimal |
| Route optimization | Google Routes API | Already integrated, has `optimizeWaypointOrder` |
| Authentication | Simple password + JWT | Single user, keep it simple, upgradable later |

---

## User Flow (Critical to Understand)

```
┌─────────────────────────────────────────────────────────────────┐
│                     PLANNING PHASE                               │
├─────────────────────────────────────────────────────────────────┤
│  1. User opens Dromedario (web or mobile)                       │
│  2. Adds waypoints (addresses for deliveries)                   │
│  3. Route auto-draws on map as waypoints are added              │
│  4. Web ↔ Mobile sync via WebSocket                             │
│  5. User can click "Optimize Route" to reorder waypoints        │
│  6. User clicks "Start Navigation"                              │
│  7. App auto-generates groups (chunks of 9 waypoints)           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                   NAVIGATION PHASE (per group)                   │
├─────────────────────────────────────────────────────────────────┤
│  1. Dromedario exports Group N to Google Maps (URL intent)      │
│  2. Google Maps opens with route                                │
│  3. Dromedario goes to BACKGROUND (not visible!)                │
│  4. User navigates using Google Maps                            │
│  5. Dromedario monitors location via geofencing                 │
│  6. User arrives at last waypoint → geofence triggers           │
│  7. Local notification: "Route complete! Start next group?"     │
│  8. User taps notification → repeat from step 1 with Group N+1  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      COMPLETION                                  │
├─────────────────────────────────────────────────────────────────┤
│  All groups completed → Trip marked as COMPLETED in MongoDB     │
└─────────────────────────────────────────────────────────────────┘
```

**Key insight**: During navigation, user is in Google Maps, NOT in Dromedario. The app must work entirely in background via geofencing and notifications.

---

## Proposed Data Model Changes

### New Models

```kotlin
@Serializable
data class TripSession(
    @SerialName("_id")
    val id: String,                          // UUID
    val waypoints: List<Waypoint>,           // All waypoints (editable during PLANNING)
    val groups: List<RouteGroup> = emptyList(), // Generated when NAVIGATING starts
    val activeGroupIndex: Int = 0,
    val status: TripStatus = TripStatus.PLANNING,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
enum class TripStatus {
    PLANNING,    // User adding/removing waypoints
    NAVIGATING,  // Route finalized, navigating through groups
    COMPLETED    // All groups completed
}

@Serializable
data class RouteGroup(
    val index: Int,
    val waypointStartIndex: Int,  // Index in main waypoints list
    val waypointEndIndex: Int,    // Exclusive end index
    val status: GroupStatus = GroupStatus.PENDING
)

@Serializable
enum class GroupStatus {
    PENDING,
    ACTIVE,
    COMPLETED
}
```

### Updated WebSocket Events

```kotlin
@Serializable
enum class EventType {
    // Existing
    ADD_WAYPOINT,
    REMOVE_WAYPOINT,

    // New
    OPTIMIZE_ROUTE,      // Client → Server: Request route optimization
    FINALIZE_ROUTE,      // Client → Server: Generate groups, start navigation
    GROUP_COMPLETED,     // Client → Server: Mark current group done, advance
    SYNC_STATE,          // Server → Client: Full state sync
    EXPORT_GROUP         // Server → Client: Trigger Google Maps export
}
```

---

## Web Maps Integration Architecture

### Why Kotlin/JS Instead of Kotlin/WASM

| Aspect | Kotlin/WASM | Kotlin/JS |
|--------|-------------|-----------|
| Debugging | Difficult (cryptic stack traces) | Easy (source maps, browser DevTools) |
| JS Interop | Limited (primitives only) | Full (can use npm, dynamic types) |
| Performance | Faster | Slightly slower (but fine for this app) |
| Maturity | Newer, less ecosystem | Mature, well-documented |

**Decision**: Use **Kotlin/JS** for the web client. Easier debugging and JS interop outweigh the minor performance difference.

### Challenge
- **Google Maps Compose is Android-only** - cannot use on Web
- Need to integrate Google Maps JavaScript API with Compose UI
- Both Compose and Google Maps render to DOM - they coexist naturally

### Solution: Hybrid DOM Layout

```
┌─────────────────────────────────────────────────────────────────┐
│                        Web Page (DOM)                           │
├─────────────────────────────┬───────────────────────────────────┤
│   Compose JS Container      │    Google Maps JS Container       │
│   (rendered to DOM)         │    (separate <div>)               │
├─────────────────────────────┼───────────────────────────────────┤
│   CommonScreen              │    google.maps.Map                │
│   - Waypoint list           │    - Markers for waypoints        │
│   - Add waypoint button     │    - Polylines for routes         │
│   - Optimize Route button   │    - InfoWindows                  │
│   - Start Navigation button │    - Auto-updates when state      │
│                             │      changes via JS interop       │
└─────────────────────────────┴───────────────────────────────────┘
```

### Kotlin/JS to Google Maps Communication

With Kotlin/JS, communication is natural - Kotlin compiles to JavaScript, so they're in the same runtime.

**Option 1: `dynamic` type (quick)**
```kotlin
val google = js("google")
val map = google.maps.Map(document.getElementById("map"), jsObject {
    center = jsObject { lat = -30.05; lng = -51.20 }
    zoom = 12
})
```

**Option 2: `external` declarations (type-safe)**
```kotlin
external object google {
    object maps {
        class Map(element: Element, options: MapOptions)
        class Marker(options: MarkerOptions)
        class Polyline(options: PolylineOptions)
    }
}
```

**Option 3: Wrapper class (recommended for this project)**
```kotlin
class MapController(containerId: String) {
    private val map: dynamic
    private val markers = mutableListOf<dynamic>()

    init {
        val google = js("google")
        map = google.maps.Map(
            document.getElementById(containerId),
            js("{ center: { lat: -30.05, lng: -51.20 }, zoom: 12 }")
        )
    }

    fun updateWaypoints(waypoints: List<Waypoint>) {
        clearMarkers()
        waypoints.forEach { wp ->
            addMarker(wp.latitude, wp.longitude, wp.address)
        }
        if (waypoints.size >= 2) {
            drawRoute(waypoints)
        }
    }
}
```

**Integration with Compose:**
```kotlin
@Composable
fun WebApp(viewModel: ClientSharedViewModel) {
    val waypoints by viewModel.incomingFlow.collectAsState()
    val mapController = remember { MapController("map-container") }

    LaunchedEffect(waypoints) {
        mapController.updateWaypoints(waypoints.waypoints)
    }

    // Compose UI
    CommonScreen(url = WEB_SOCKET_URL)
}
```

### Data Flow

```
WebSocket → ClientSharedViewModel → StateFlow<RouteStateModel>
                                            ↓
                                    Compose collects state
                                            ↓
                                    LaunchedEffect triggers
                                            ↓
                                    mapController.updateWaypoints()
                                            ↓
                                    Google Maps JS updates markers/polylines
```

### Reusable Components

| Component | Reusable on Web? | Notes |
|-----------|------------------|-------|
| `CommonScreen.kt` | ✅ Yes | Material3, works directly |
| `ClientSharedViewModel` | ✅ Yes | Platform-agnostic |
| `RouteStateModel` | ✅ Yes | Shared data model |
| `Waypoint` | ✅ Yes | Shared data model |
| `MobileRepository` | ⚠️ Partial | Route API calls need shared version |
| `RouteModels` | ✅ Move to shared | Currently in androidMain |
| Polyline decoding | ✅ Built-in | Each platform uses native Maps SDK decoder |

---

## Shared ViewModel Architecture

### Why SharedViewModel Still Makes Sense

The `ClientSharedViewModel` handles logic that is **identical** on both platforms:
- WebSocket connection management
- State holding (`StateFlow<RouteStateModel>`)
- Sending events (ADD_WAYPOINT, REMOVE_WAYPOINT, etc.)
- Receiving state updates from server

Only the **map rendering** is platform-specific.

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    shared/commonMain                             │
├─────────────────────────────────────────────────────────────────┤
│  ClientSharedViewModel                                           │
│    - WebSocket connection                                        │
│    - incomingFlow: StateFlow<RouteStateModel>                   │
│    - sendMessage(ADD_WAYPOINT, data)                            │
│    - deleteMessage(REMOVE_WAYPOINT, index)                      │
│    - optimizeRoute()                                             │
│    - finalizeRoute()                                             │
└─────────────────────────────────────────────────────────────────┘
              ↓                                    ↓
┌─────────────────────────────┐    ┌─────────────────────────────┐
│        androidMain          │    │          jsMain             │
├─────────────────────────────┤    ├─────────────────────────────┤
│  MobileViewModel            │    │  MapController              │
│    - uses shared VM         │    │    - Google Maps JS         │
│    - geofencing             │    │    - markers/polylines      │
│    - local notifications    │    │                             │
│    - Google Maps Compose    │    │  WebApp                     │
│                             │    │    - collects shared VM     │
│  GeofenceManagerHelper            │    │    - updates MapController  │
│  NotificationHelper         │    │                             │
└─────────────────────────────┘    └─────────────────────────────┘
```

### What Goes Where

| Logic | Location | Reason |
|-------|----------|--------|
| WebSocket handling | `shared/commonMain` | Same on both platforms |
| State management | `shared/commonMain` | Same data model |
| Event sending | `shared/commonMain` | Same protocol |
| Map rendering | Platform-specific | Different APIs (Compose vs JS) |
| Geofencing | `androidMain` only | Mobile-only feature |
| Notifications | `androidMain` only | Mobile-only feature |

---

## Web Client Hosting

### Approach: Serve from Same Ktor Server

The web client (compiled JS) is served from the same Ktor server that handles WebSocket connections.

**Benefits:**
- Single deployment (server + web client together)
- No CORS configuration needed (same origin)
- Same URL/port for everything
- Simple for end user - just run one thing

### Server Configuration

```kotlin
// ServerApp.kt
fun Application.module() {
    install(WebSockets)

    routing {
        // WebSocket endpoint
        webSocket("/ws") { /* existing code */ }

        // Serve web client static files
        static("/") {
            resources("web")
            defaultResource("web/index.html")
        }
    }
}
```

### URLs

| Endpoint | Purpose |
|----------|---------|
| `http://localhost:8080/` | Web client (static files) |
| `ws://localhost:8080/ws` | WebSocket connection |
| `http://localhost:8080/api/*` | REST endpoints (login, etc.) |

### Build Process

```bash
# Build web client
./gradlew jsBrowserProductionWebpack

# Output: composeApp/build/dist/js/productionExecutable/
#   ├── index.html
#   ├── composeApp.js
#   └── styles.css

# Copy to server resources (automate in Gradle)
# → server/src/main/resources/web/
```

### Security Considerations

For a personal app with 1-2 users:
- No CDN needed
- No separate scaling concerns
- Minimal attack surface (local network or single server)

If app becomes public/commercial later, can easily split hosting.

---

## Authentication

### Approach: Simple Password + JWT

For a single-user app that just needs to not be "totally open", a simple password with JWT tokens is sufficient.

### Flow

```
┌─────────────────────────────────────────────────────────────────┐
│  1. User opens app (web or mobile)                              │
│     → App checks for stored token                               │
│     → If no token or expired → show login screen                │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  2. User enters password                                        │
│     → POST /api/login { password: "xxx" }                       │
│     → Server validates against configured password              │
│     → Server returns JWT token                                  │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  3. Client stores token                                         │
│     → Android: SharedPreferences / EncryptedSharedPreferences   │
│     → Web: localStorage                                         │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  4. WebSocket connection with token                             │
│     → ws://server/ws?token=xxx                                  │
│     → Server validates token on connect                         │
│     → If invalid → reject connection                            │
└─────────────────────────────────────────────────────────────────┘
```

### Server Implementation

```kotlin
// ServerApp.kt
fun Application.module() {
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val appPassword = environment.config.property("app.password").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JWT.require(Algorithm.HMAC256(jwtSecret)).build())
            validate { credential ->
                if (credential.payload.getClaim("app").asString() == "dromedario") {
                    JWTPrincipal(credential.payload)
                } else null
            }
        }
    }

    routing {
        // Login endpoint (no auth required)
        post("/api/login") {
            val request = call.receive<LoginRequest>()
            if (request.password == appPassword) {
                val token = JWT.create()
                    .withClaim("app", "dromedario")
                    .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) // 30 days
                    .sign(Algorithm.HMAC256(jwtSecret))
                call.respond(LoginResponse(token))
            } else {
                call.respond(HttpStatusCode.Unauthorized)
            }
        }

        // WebSocket with token validation
        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
            if (!validateToken(token, jwtSecret)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }
            // ... existing WebSocket logic
        }
    }
}
```

### Data Models

```kotlin
@Serializable
data class LoginRequest(val password: String)

@Serializable
data class LoginResponse(val token: String)
```

### Client Implementation

**Shared (commonMain):**
```kotlin
// AuthRepository.kt
expect class AuthRepository {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
}
```

**Android (androidMain):**
```kotlin
actual class AuthRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    actual fun saveToken(token: String) = prefs.edit().putString("token", token).apply()
    actual fun getToken(): String? = prefs.getString("token", null)
    actual fun clearToken() = prefs.edit().remove("token").apply()
}
```

**Web (jsMain):**
```kotlin
actual class AuthRepository {
    actual fun saveToken(token: String) { localStorage["token"] = token }
    actual fun getToken(): String? = localStorage["token"]
    actual fun clearToken() { localStorage.removeItem("token") }
}
```

### Security Notes

- Password stored as environment variable on server (not in code)
- JWT secret also from environment variable
- Token expires after 30 days (configurable)
- Can upgrade to Google SSO later if needed

---

## Implementation Areas

### 1. Data Model Migration
- Update `RouteStateModel` to `TripSession`
- Add new enums and `RouteGroup`
- Move `RouteModels.kt` to shared module
- Update MongoDB schema

### 2. Server Changes
- Handle new event types (OPTIMIZE_ROUTE, FINALIZE_ROUTE, GROUP_COMPLETED)
- Group generation logic (chunk waypoints by 9)
- Track active group progression
- Route optimization endpoint (proxy to Google Routes API)
- **Authentication**:
  - JWT token generation and validation
  - `/api/login` endpoint
  - WebSocket token validation on connect
- **Static file hosting**:
  - Serve web client from `/`
  - Configure static resources

### 3. Android Changes
- **Geofencing setup**:
  - `GeofencingClient` initialization
  - `GeofenceBroadcastReceiver` to handle transitions
  - Register geofence for last waypoint of active group
  - Remove old geofence when group advances
- **Local notifications**:
  - Notification channel setup
  - "Route complete! Start next group?" notification
  - PendingIntent to handle tap → export next group
- **Permission flow**:
  - Request `ACCESS_FINE_LOCATION` first
  - Then request `ACCESS_BACKGROUND_LOCATION` with explanation
  - Request `POST_NOTIFICATIONS` (Android 13+)
- "Finalize Route" button to trigger group creation
- "Export Current Group" function (opens Google Maps intent)
- "Optimize Route" button
- Update UI to show current group progress
- **Auto-draw route**: When waypoint added, fetch and display route segment

### 4. Web Changes (Kotlin/JS)
- **Migrate from WASM to JS**:
  - Rename `wasmJsMain` to `jsMain`
  - Update Gradle configuration for JS target
  - Update HttpClient engine (Js instead of Wasm)
- **Google Maps JS API integration**:
  - Add script tag to `index.html`
  - Create `MapController` class with JS interop
  - Split layout: Compose UI + Map container
- **Map features**:
  - Display markers for all waypoints
  - Draw polylines between waypoints
  - Auto-update when `RouteStateModel` changes
  - Auto-draw route segment when new waypoint added
- **UI additions**:
  - Login screen
  - "Optimize Route" button
  - "Start Navigation" button (generates groups, syncs to mobile)
  - Show navigation status (read-only during NAVIGATING)
- **Authentication**:
  - Login screen with password input
  - Store token in localStorage
  - Send token with WebSocket connection
- **Polyline decoding**: Use built-in `google.maps.geometry.encoding.decodePath()`

### 5. Shared Changes
- Update `ClientSharedViewModel` with new events
- Add group-related state management
- Move route models to shared module
- Add route optimization request/response models

---

## API Limits Reference

| API | Limit | Notes |
|-----|-------|-------|
| Google Maps URLs (mobile browser) | 3 waypoints | Not used |
| Google Maps URLs (app/desktop) | 9 waypoints | **Our limit for groups** |
| Routes API (backend) | 25 waypoints | For polyline calculation |
| Routes API | 3000 req/min | Unlikely to hit |

---

## Geofencing Implementation Details

### How It Works (No Firebase Required)

```
┌──────────────────────────────────────────────────────────────────┐
│  STEP 1: Register Geofence                                       │
│  GeofenceManagerHelper.registerGeofence(lastWaypoint)                  │
│    → GeofencingClient.addGeofences(request, pendingIntent)       │
│    → Android OS now monitors this location                       │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  STEP 2: App Goes to Background                                  │
│  openInGoogleMaps() → Google Maps takes foreground               │
│  Dromedario process may be killed, BUT:                          │
│    → Geofence stays registered with Android OS                   │
│    → OS monitors location, not our app                           │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  STEP 3: User Arrives at Destination                             │
│  Android OS detects user entered geofence radius                 │
│    → OS wakes up our BroadcastReceiver                           │
│    → GeofenceBroadcastReceiver.onReceive() called                │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  STEP 4: Show Local Notification                                 │
│  NotificationManager.notify(notification)                        │
│    → "Route Complete! Tap to start next group"                   │
│    → No internet, no Firebase, completely local                  │
└──────────────────────────────────────────────────────────────────┘
                              ↓
┌──────────────────────────────────────────────────────────────────┐
│  STEP 5: User Taps Notification                                  │
│  PendingIntent fires → MainActivity launched                     │
│    → intent.action == "ACTION_START_NEXT_GROUP"                  │
│    → App advances to next group, exports to Google Maps          │
│    → Register NEW geofence for new last waypoint                 │
└──────────────────────────────────────────────────────────────────┘
```

### Android Components Structure

```
composeApp/src/androidMain/kotlin/br/gohan/dromedario/
├── geofence/
│   ├── GeofenceManagerHelper.kt          # Register/unregister geofences
│   ├── GeofenceBroadcastReceiver.kt # Handles GEOFENCE_TRANSITION_ENTER
│   └── NotificationHelper.kt        # Creates local notifications
├── presenter/
│   └── MobileViewModel.kt          # Orchestrates the flow
└── AndroidManifest.xml             # Receiver + permissions
```

### Key Code Snippets

**GeofenceManagerHelper.kt:**
```kotlin
class GeofenceManagerHelper(private val context: Context) {
    private val client = LocationServices.getGeofencingClient(context)

    fun registerLastWaypoint(waypoint: Waypoint) {
        val geofence = Geofence.Builder()
            .setRequestId("destination_${waypoint.index}")
            .setCircularRegion(waypoint.latitude, waypoint.longitude, 150f)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()
        // ... add to client
    }

    fun removeAllGeofences() {
        client.removeGeofences(pendingIntent)
    }
}
```

**GeofenceBroadcastReceiver.kt:**
```kotlin
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            NotificationHelper(context).showArrivalNotification()
        }
    }
}
```

**NotificationHelper.kt:**
```kotlin
class NotificationHelper(private val context: Context) {
    fun showArrivalNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_START_NEXT_GROUP"
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, FLAGS)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Route Complete!")
            .setContentText("Tap to start next group")
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
```

---

## Dependencies to Add

### Server
```kotlin
// JWT Authentication
implementation("io.ktor:ktor-server-auth:$ktor_version")
implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
implementation("com.auth0:java-jwt:4.4.0")

// Static file serving (already included in ktor-server-core)
```

### Android
```kotlin
// Geofencing (part of Play Services Location)
implementation("com.google.android.gms:play-services-location:21.0.1")
```

### Android Manifest Permissions
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
```

**Note**: `ACCESS_BACKGROUND_LOCATION` requires special user permission flow on Android 10+ and Play Store justification.

### Web (Kotlin/JS)
```html
<!-- In index.html -->
<script src="https://maps.googleapis.com/maps/api/js?key=YOUR_API_KEY"></script>
```

### Gradle Changes for Kotlin/JS
```kotlin
// composeApp/build.gradle.kts
kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }
}
```

---

## Risk Considerations

1. **Background Location Permission**: Android 10+ requires `ACCESS_BACKGROUND_LOCATION` which:
   - Requires separate permission request (after foreground location granted)
   - Play Store requires justification for why app needs background location
   - Some users deny background location for privacy reasons
   - **Mitigation**: Clear explanation to user why it's needed ("detect when you arrive to start next route")

2. **Geofencing Reliability**: Geofences may not trigger immediately due to:
   - Battery optimization (Doze mode)
   - Device-specific behavior (some OEMs aggressive with background killing)
   - **Mitigation**: Use `setNotificationResponsiveness()` for faster detection, consider foreground service as backup

3. **Offline Handling**: If user loses connection mid-navigation, ensure state is preserved and syncs on reconnect.

4. **Route Optimization API Cost**: `optimizeWaypointOrder` may have different billing than standard Routes API calls.

5. **JWT Token Security**:
   - Tokens stored in localStorage (web) are vulnerable to XSS
   - **Mitigation**: For a personal single-user app, this is acceptable. For production, use HttpOnly cookies.

6. **Password in Environment Variable**:
   - Server password must be configured via environment variable
   - **Mitigation**: Document deployment process clearly

---

## Out of Scope (Future Enhancements)

- [ ] Address autocomplete (Places API)
- [ ] ETA per stop
- [ ] Trip history UI
- [ ] Multi-user support
- [ ] iOS app
- [ ] Manual waypoint reordering (drag and drop)

---

## Success Criteria

1. User must authenticate with password to access app (web and mobile)
2. User can add 20+ waypoints via web or mobile
3. Route auto-draws on map as waypoints are added
4. "Optimize Route" reorders waypoints for shortest distance
5. Clicking "Start Navigation" splits into groups of 9
6. First group exports to Google Maps
7. Geofence triggers when user arrives at last waypoint
8. Notification appears prompting for next group
9. Responding to notification exports next group
10. All trips persist in MongoDB
11. Web and mobile stay synchronized throughout
12. Web client served from same server as API

---

## Technical References

**Kotlin/JS:**
- [Kotlin/JS Overview](https://kotlinlang.org/docs/js-overview.html)
- [Kotlin/JS Interop](https://kotlinlang.org/docs/js-interop.html)
- [Calling JavaScript from Kotlin](https://kotlinlang.org/docs/js-interop.html#external-modifier)

**Google Maps:**
- [Maps JavaScript API](https://developers.google.com/maps/documentation/javascript)
- [Routes API - Optimize Waypoints](https://developers.google.com/maps/documentation/routes/compute_route_directions#optimize-waypoint-order)

**Android Geofencing:**
- [Create and Monitor Geofences](https://developer.android.com/develop/sensors-and-location/location/geofencing)

**Ktor:**
- [Serving Static Content](https://ktor.io/docs/serving-static-content.html)
- [JWT Authentication](https://ktor.io/docs/jwt.html)

---

## Research Phase Summary

**Completed**: 2026-01-27

**Key Decisions Made:**
- Kotlin/JS for web (not WASM) - easier debugging and JS interop
- Google Maps JS API for web maps via MapController wrapper
- SharedViewModel in commonMain for WebSocket/state management
- Web client hosted from same Ktor server
- Simple password + JWT for authentication
- Geofencing + local notifications for arrival detection (no Firebase)
- Auto-split waypoints into groups of 9 for Google Maps URL limit

**Ready for Planning Phase:**
- All requirements documented (FR1-FR6)
- Architecture decisions finalized
- Data models proposed
- Implementation areas identified
- Dependencies listed
- Risks assessed

*Next: Planning Phase - Create implementation tasks*
