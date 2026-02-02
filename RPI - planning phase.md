# Dromedario Route Planner - Implementation Plan

## RPI Methodology

This project follows the **RPI (Research, Plan, Implement)** methodology:

1. **Research Phase** - Analyze requirements, explore the codebase, make technical decisions, document architecture and constraints
2. **Planning Phase** (this document) - Break down work into executable tasks with clear dependencies, code snippets, and verification steps
3. **Implementation Phase** - A new Claude instance reads this document and executes the tasks

---

## Instructions for Implementation Agent

You are reading this document to implement the Dromedario route planner features.

**Before starting:**
1. Read `RPI - research phase.md` for full context on architecture, decisions, and rationale
2. Execute phases in order (Phase 0 → Phase 1 → Phase 2)
3. Within Phase 1, execute workstreams in this recommended order: A (Server) → C (Web) → B (Android)
4. Run verification steps after each task before proceeding
5. Ask the user to test after completing each phase/workstream

**Key files to familiarize yourself with first:**
- `shared/src/commonMain/kotlin/br/gohan/dromedario/data/` - Data models
- `server/src/main/kotlin/br/gohan/dromedario/ServerApp.kt` - Server entry point
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/presenter/` - Android UI/ViewModel
- `composeApp/src/wasmJsMain/` - Web client (will be migrated to jsMain)

---

## Execution Strategy

The implementation is organized into **4 parallel workstreams** that can be worked on simultaneously by different agents. Dependencies between workstreams are minimized and clearly marked.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        PHASE 0: Foundation                               │
│                    (Must complete before other phases)                   │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  Task 0.1: Data Models Migration (shared module)                │    │
│  │  Task 0.2: WebSocket Protocol Update (shared module)            │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                     PHASE 1: Parallel Workstreams                        │
├──────────────────┬──────────────────┬──────────────────┤
│  WORKSTREAM A    │  WORKSTREAM B    │  WORKSTREAM C    │
│  Server          │  Android         │  Web             │
├──────────────────┼──────────────────┼──────────────────┤
│  A1: Auth/JWT    │  B1: Permissions │  C1: JS Migration│
│  A2: New Events  │  B2: Geofencing  │  C2: Maps JS     │
│  A3: Static Host │  B3: Notifs      │  C3: Auth UI     │
│  A4: Route Opt   │  B4: UI Updates  │  C4: UI Updates  │
└──────────────────┴──────────────────┴──────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                        PHASE 2: Integration                              │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │  Task I1: End-to-end testing                                    │    │
│  │  Task I2: Build automation (web client → server resources)      │    │
│  └─────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 0: Foundation (Sequential - Blocking)

These tasks MUST complete before any parallel workstream begins. They establish the shared contracts.

### Task 0.1: Data Models Migration

**File**: `shared/src/commonMain/kotlin/br/gohan/dromedario/data/TripModels.kt` (NEW)

**Description**: Create the new data models that all modules will use.

**Steps**:
1. Create new file `TripModels.kt` in shared module
2. Add `TripSession` data class (replaces `RouteStateModel` as the main model)
3. Add `TripStatus` enum (PLANNING, NAVIGATING, COMPLETED)
4. Add `RouteGroup` data class
5. Add `GroupStatus` enum (PENDING, ACTIVE, COMPLETED)
6. Keep `Waypoint` as-is (already in shared)
7. Keep `RouteStateModel` for backwards compatibility during migration

**Code to write**:
```kotlin
// shared/src/commonMain/kotlin/br/gohan/dromedario/data/TripModels.kt
package br.gohan.dromedario.data

import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TripSession(
    @SerialName("_id")
    val id: String,
    val waypoints: List<Waypoint> = emptyList(),
    val groups: List<RouteGroup> = emptyList(),
    val activeGroupIndex: Int = 0,
    val status: TripStatus = TripStatus.PLANNING,
    val createdAt: Long = Clock.System.now().epochSeconds,
    val updatedAt: Long = Clock.System.now().epochSeconds
)

@Serializable
enum class TripStatus {
    PLANNING,
    NAVIGATING,
    COMPLETED
}

@Serializable
data class RouteGroup(
    val index: Int,
    val waypointStartIndex: Int,
    val waypointEndIndex: Int,  // Exclusive
    val status: GroupStatus = GroupStatus.PENDING
)

@Serializable
enum class GroupStatus {
    PENDING,
    ACTIVE,
    COMPLETED
}
```

**Verification**: Build shared module with `./gradlew :shared:compileKotlinMetadata`

---

### Task 0.2: WebSocket Protocol Update

**File**: `shared/src/commonMain/kotlin/br/gohan/dromedario/data/MessageModel.kt`

**Description**: Add new event types for the updated protocol.

**Steps**:
1. Read existing `MessageModel.kt`
2. Add new event types to `EventType` enum
3. Keep existing events for backwards compatibility

**Code changes**:
```kotlin
@Serializable
enum class EventType {
    // Existing
    ADD_WAYPOINT,
    REMOVE_WAYPOINT,

    // New - Route Management
    OPTIMIZE_ROUTE,      // Client → Server: Request optimization
    FINALIZE_ROUTE,      // Client → Server: Lock waypoints, generate groups
    GROUP_COMPLETED,     // Client → Server: Mark current group done

    // New - State Sync
    SYNC_STATE,          // Server → Client: Full TripSession sync
    TRIP_STATUS_CHANGED, // Server → Client: Status changed notification

    // New - Navigation
    EXPORT_GROUP,        // Internal: Trigger Google Maps export
    REORDER_WAYPOINTS    // Client → Server: New waypoint order after optimization
}
```

**Verification**: Build shared module

---

## Phase 1: Parallel Workstreams

After Phase 0 completes, these 4 workstreams can execute in parallel.

---

## Workstream A: Server

**Agent Assignment**: Server Agent
**Dependencies**: Phase 0 complete

### Task A1: JWT Authentication

**Files to modify**:
- `server/build.gradle.kts` - Add dependencies
- `server/src/main/kotlin/br/gohan/dromedario/ServerApp.kt` - Add auth
- `server/src/main/resources/application.conf` - Add config (NEW if not exists)

**Steps**:

1. **Add dependencies to version catalog and build.gradle.kts**:

First, add to `gradle/libs.versions.toml`:
```toml
[versions]
# ... existing versions
java-jwt = "4.4.0"

[libraries]
# ... existing libraries
ktor-server-auth = { module = "io.ktor:ktor-server-auth", version.ref = "ktor" }
ktor-server-auth-jwt = { module = "io.ktor:ktor-server-auth-jwt", version.ref = "ktor" }
java-jwt = { module = "com.auth0:java-jwt", version.ref = "java-jwt" }
```

Then in `server/build.gradle.kts`:
```kotlin
dependencies {
    // Existing...

    // JWT Authentication
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.java.jwt)
}
```

2. **Create or update `application.conf`**:
```hocon
ktor {
    deployment {
        port = 8080
    }
    application {
        modules = [ br.gohan.dromedario.ServerAppKt.module ]
    }
}

jwt {
    secret = ${?JWT_SECRET}
    secret = "dev-secret-change-in-production"
}

app {
    password = ${?APP_PASSWORD}
    password = "dromedario123"
}
```

3. **Create `AuthModels.kt`**:
```kotlin
// server/src/main/kotlin/br/gohan/dromedario/auth/AuthModels.kt
package br.gohan.dromedario.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val password: String)

@Serializable
data class LoginResponse(val token: String)
```

4. **Create `AuthService.kt`**:
```kotlin
// server/src/main/kotlin/br/gohan/dromedario/auth/AuthService.kt
package br.gohan.dromedario.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class AuthService(private val jwtSecret: String, private val appPassword: String) {

    fun validatePassword(password: String): Boolean = password == appPassword

    fun generateToken(): String = JWT.create()
        .withClaim("app", "dromedario")
        .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) // 30 days
        .sign(Algorithm.HMAC256(jwtSecret))

    fun validateToken(token: String?): Boolean {
        if (token == null) return false
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(jwtSecret)).build()
            val decoded = verifier.verify(token)
            decoded.getClaim("app").asString() == "dromedario"
        } catch (e: Exception) {
            false
        }
    }
}
```

5. **Update `ServerApp.kt`** - Add login endpoint and WebSocket auth:
```kotlin
// Add to module() function:

val jwtSecret = environment.config.property("jwt.secret").getString()
val appPassword = environment.config.property("app.password").getString()
val authService = AuthService(jwtSecret, appPassword)

routing {
    // Login endpoint (no auth required)
    post("/api/login") {
        val request = call.receive<LoginRequest>()
        if (authService.validatePassword(request.password)) {
            call.respond(LoginResponse(authService.generateToken()))
        } else {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid password"))
        }
    }

    // Update existing webSocket block:
    webSocket("/ws") {
        val token = call.request.queryParameters["token"]
        if (!authService.validateToken(token)) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
            return@webSocket
        }
        // ... existing WebSocket logic
    }
}
```

**Verification**:
- Start server
- Test login: `curl -X POST http://localhost:8080/api/login -H "Content-Type: application/json" -d '{"password":"dromedario123"}'`
- Verify token is returned

---

### Task A2: Handle New WebSocket Events

**File**: `server/src/main/kotlin/br/gohan/dromedario/ServerApp.kt`

**Description**: Handle the new event types in WebSocket message processing.

**Steps**:

1. **Add group generation logic**:
```kotlin
// Add function to ServerApp.kt or new file RouteGroupGenerator.kt
fun generateGroups(waypoints: List<Waypoint>, maxGroupSize: Int = 9): List<RouteGroup> {
    if (waypoints.isEmpty()) return emptyList()

    val groups = mutableListOf<RouteGroup>()
    var startIndex = 0
    var groupIndex = 0

    while (startIndex < waypoints.size) {
        val endIndex = minOf(startIndex + maxGroupSize, waypoints.size)
        groups.add(RouteGroup(
            index = groupIndex,
            waypointStartIndex = startIndex,
            waypointEndIndex = endIndex,
            status = if (groupIndex == 0) GroupStatus.ACTIVE else GroupStatus.PENDING
        ))
        startIndex = endIndex
        groupIndex++
    }

    return groups
}
```

2. **Update message handler in WebSocket block**:
```kotlin
when (message.event) {
    EventType.ADD_WAYPOINT -> { /* existing */ }
    EventType.REMOVE_WAYPOINT -> { /* existing */ }

    EventType.OPTIMIZE_ROUTE -> {
        // Call Google Routes API with optimizeWaypointOrder
        // Update waypoint order based on response
        // Broadcast SYNC_STATE to all clients
    }

    EventType.FINALIZE_ROUTE -> {
        val trip = databaseManager.getCurrentTrip()
        val groups = generateGroups(trip.waypoints)
        val updatedTrip = trip.copy(
            groups = groups,
            status = TripStatus.NAVIGATING,
            updatedAt = Clock.System.now().epochSeconds
        )
        databaseManager.updateTrip(updatedTrip)
        // Broadcast SYNC_STATE to all clients
        broadcastState(updatedTrip)
    }

    EventType.GROUP_COMPLETED -> {
        val trip = databaseManager.getCurrentTrip()
        val updatedGroups = trip.groups.mapIndexed { idx, group ->
            when {
                idx == trip.activeGroupIndex -> group.copy(status = GroupStatus.COMPLETED)
                idx == trip.activeGroupIndex + 1 -> group.copy(status = GroupStatus.ACTIVE)
                else -> group
            }
        }
        val newActiveIndex = trip.activeGroupIndex + 1
        val newStatus = if (newActiveIndex >= trip.groups.size) TripStatus.COMPLETED else TripStatus.NAVIGATING

        val updatedTrip = trip.copy(
            groups = updatedGroups,
            activeGroupIndex = newActiveIndex,
            status = newStatus,
            updatedAt = Clock.System.now().epochSeconds
        )
        databaseManager.updateTrip(updatedTrip)
        broadcastState(updatedTrip)
    }

    EventType.REORDER_WAYPOINTS -> {
        val newOrder = Json.decodeFromJsonElement<List<Waypoint>>(message.data!!)
        val trip = databaseManager.getCurrentTrip()
        val updatedTrip = trip.copy(
            waypoints = newOrder,
            updatedAt = Clock.System.now().epochSeconds
        )
        databaseManager.updateTrip(updatedTrip)
        broadcastState(updatedTrip)
    }

    else -> { /* ignore unknown events */ }
}
```

3. **Add broadcast helper**:
```kotlin
suspend fun DefaultWebSocketServerSession.broadcastState(trip: TripSession) {
    val syncMessage = MessageModel(
        event = EventType.SYNC_STATE,
        data = Json.encodeToJsonElement(trip)
    )
    // Send to all connected clients
    sessions.forEach { session ->
        session.send(Json.encodeToString(syncMessage))
    }
}
```

**Verification**:
- Connect WebSocket client
- Send FINALIZE_ROUTE event
- Verify groups are generated and broadcast

---

### Task A3: Static File Hosting

**File**: `server/src/main/kotlin/br/gohan/dromedario/ServerApp.kt`

**Description**: Serve the web client from the same server.

**Steps**:

1. **Create directory**: `server/src/main/resources/web/` (will contain built web client)

2. **Create placeholder `index.html`**:
```html
<!-- server/src/main/resources/web/index.html -->
<!DOCTYPE html>
<html>
<head>
    <title>Dromedario</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
</head>
<body>
    <div id="root">Loading...</div>
    <div id="map-container" style="width: 50%; height: 100vh; position: fixed; right: 0; top: 0;"></div>
    <script src="https://maps.googleapis.com/maps/api/js?key=YOUR_API_KEY"></script>
    <script src="composeApp.js"></script>
</body>
</html>
```

3. **Update `ServerApp.kt`** - Add static file serving:
```kotlin
routing {
    // API routes...
    post("/api/login") { /* ... */ }

    // WebSocket...
    webSocket("/ws") { /* ... */ }

    // Static file hosting for web client (must be last)
    static("/") {
        resources("web")
        defaultResource("web/index.html")
    }
}
```

**Verification**:
- Start server
- Navigate to `http://localhost:8080/`
- Verify index.html is served

---

### Task A4: Route Optimization Endpoint

**File**: `server/src/main/kotlin/br/gohan/dromedario/routes/OptimizeRoute.kt` (NEW)

**Description**: Add endpoint to call Google Routes API with optimization.

**Steps**:

1. **Create route optimization handler**:
```kotlin
// server/src/main/kotlin/br/gohan/dromedario/routes/OptimizeRoute.kt
package br.gohan.dromedario.routes

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*

class RouteOptimizer(private val apiKey: String) {
    private val client = HttpClient()

    suspend fun optimizeWaypoints(waypoints: List<Waypoint>): List<Waypoint> {
        if (waypoints.size < 2) return waypoints

        val origin = waypoints.first()
        val destination = waypoints.last()
        val intermediates = waypoints.drop(1).dropLast(1)

        val requestBody = buildJsonObject {
            put("origin", buildJsonObject {
                put("location", buildJsonObject {
                    put("latLng", buildJsonObject {
                        put("latitude", origin.latitude)
                        put("longitude", origin.longitude)
                    })
                })
            })
            put("destination", buildJsonObject {
                put("location", buildJsonObject {
                    put("latLng", buildJsonObject {
                        put("latitude", destination.latitude)
                        put("longitude", destination.longitude)
                    })
                })
            })
            putJsonArray("intermediates") {
                intermediates.forEach { wp ->
                    addJsonObject {
                        put("location", buildJsonObject {
                            put("latLng", buildJsonObject {
                                put("latitude", wp.latitude)
                                put("longitude", wp.longitude)
                            })
                        })
                    }
                }
            }
            put("optimizeWaypointOrder", true)
            put("travelMode", "DRIVE")
        }

        val response = client.post("https://routes.googleapis.com/directions/v2:computeRoutes") {
            header("X-Goog-Api-Key", apiKey)
            header("X-Goog-FieldMask", "routes.optimizedIntermediateWaypointIndex")
            header("Content-Type", "application/json")
            setBody(requestBody.toString())
        }

        val result = Json.parseToJsonElement(response.bodyAsText())
        val optimizedOrder = result.jsonObject["routes"]
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("optimizedIntermediateWaypointIndex")
            ?.jsonArray?.map { it.jsonPrimitive.int }
            ?: return waypoints

        // Reorder waypoints based on optimization
        val reordered = mutableListOf(origin)
        optimizedOrder.forEach { idx ->
            reordered.add(intermediates[idx])
        }
        reordered.add(destination)

        return reordered.mapIndexed { idx, wp -> wp.copy(index = idx) }
    }
}
```

2. **Integrate with WebSocket handler** (in EventType.OPTIMIZE_ROUTE case)

**Verification**:
- Send OPTIMIZE_ROUTE event with waypoints
- Verify optimized order is returned

---

## Workstream B: Android

**Agent Assignment**: Android Agent
**Dependencies**: Phase 0 complete

### Task B1: Permissions Setup

**Files**:
- `composeApp/src/androidMain/AndroidManifest.xml`
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/permissions/PermissionHelper.kt` (NEW)

**Steps**:

1. **Update AndroidManifest.xml**:
```xml
<manifest>
    <!-- Existing permissions -->
    <uses-permission android:name="android.permission.INTERNET" />

    <!-- Location permissions -->
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

    <!-- Notification permission (Android 13+) -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application>
        <!-- Existing content -->

        <!-- Geofence BroadcastReceiver -->
        <receiver
            android:name=".geofence.GeofenceBroadcastReceiver"
            android:exported="false" />
    </application>
</manifest>
```

2. **Create PermissionHelper.kt**:
```kotlin
// composeApp/src/androidMain/kotlin/br/gohan/dromedario/permissions/PermissionHelper.kt
package br.gohan.dromedario.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class PermissionHelper(private val context: Context) {

    fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        } else true

    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

    fun hasAllRequiredPermissions(): Boolean =
        hasFineLocation() && hasBackgroundLocation() && hasNotificationPermission()

    companion object {
        val LOCATION_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val BACKGROUND_LOCATION_PERMISSION = arrayOf(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )

        val NOTIFICATION_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else emptyArray()
    }
}
```

3. **Create PermissionScreen composable** for requesting permissions with explanation

**Verification**:
- Install app
- Verify permission dialog appears
- Grant permissions and verify `hasAllRequiredPermissions()` returns true

---

### Task B2: Geofencing Implementation

**Files**:
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/geofence/GeofenceManagerHelper.kt` (NEW)
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/geofence/GeofenceBroadcastReceiver.kt` (NEW)

**Steps**:

1. **Create GeofenceManagerHelper.kt**:
```kotlin
// composeApp/src/androidMain/kotlin/br/gohan/dromedario/geofence/GeofenceManagerHelper.kt
package br.gohan.dromedario.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import br.gohan.dromedario.data.Waypoint
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceManagerHelper(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun registerGeofenceForWaypoint(waypoint: Waypoint, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        val geofence = Geofence.Builder()
            .setRequestId("destination_${waypoint.index}")
            .setCircularRegion(waypoint.latitude, waypoint.longitude, GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            onFailure(SecurityException("Location permission not granted"))
            return
        }

        geofencingClient.addGeofences(request, geofencePendingIntent)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun removeAllGeofences(onComplete: () -> Unit = {}) {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnCompleteListener { onComplete() }
    }

    companion object {
        const val GEOFENCE_RADIUS_METERS = 150f
    }
}
```

2. **Create GeofenceBroadcastReceiver.kt**:
```kotlin
// composeApp/src/androidMain/kotlin/br/gohan/dromedario/geofence/GeofenceBroadcastReceiver.kt
package br.gohan.dromedario.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            // Log error
            return
        }

        if (geofencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // User arrived at destination
            NotificationHelper(context).showArrivalNotification()
        }
    }
}
```

**Verification**:
- Use Android emulator with location simulation
- Register a geofence
- Simulate moving to that location
- Verify receiver is triggered

---

### Task B3: Local Notifications

**Files**:
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/geofence/NotificationHelper.kt` (NEW)

**Steps**:

1. **Create NotificationHelper.kt**:
```kotlin
// composeApp/src/androidMain/kotlin/br/gohan/dromedario/geofence/NotificationHelper.kt
package br.gohan.dromedario.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import br.gohan.dromedario.MainActivity
import br.gohan.dromedario.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Route Navigation",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for route completion"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showArrivalNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_NEXT_GROUP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Need to create this
            .setContentTitle("Route Complete!")
            .setContentText("Tap to start the next group of destinations")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "dromedario_navigation"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_NEXT_GROUP = "br.gohan.dromedario.ACTION_START_NEXT_GROUP"
    }
}
```

2. **Create notification icon**: `composeApp/src/androidMain/res/drawable/ic_notification.xml`

3. **Handle intent in MainActivity**:
```kotlin
// In MainActivity.kt onCreate() or onNewIntent()
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    if (intent?.action == NotificationHelper.ACTION_START_NEXT_GROUP) {
        // Trigger next group export via ViewModel
        viewModel.startNextGroup()
    }
}
```

**Verification**:
- Manually trigger `showArrivalNotification()`
- Verify notification appears
- Tap notification and verify MainActivity receives intent

---

### Task B4: Android UI Updates

**Files**:
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/presenter/MobileViewModel.kt`
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/presenter/MobileApp.kt`

**Steps**:

1. **Update MobileViewModel** - Add navigation state management:
```kotlin
// Add to MobileViewModel.kt

class MobileViewModel(
    private val repository: MobileRepository,
    private val geofenceManager: GeofenceManagerHelper,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _tripState = MutableStateFlow<TripSession?>(null)
    val tripState: StateFlow<TripSession?> = _tripState.asStateFlow()

    fun finalizeRoute() {
        viewModelScope.launch {
            // Send FINALIZE_ROUTE event
            repository.sendEvent(EventType.FINALIZE_ROUTE)
        }
    }

    fun startNavigation() {
        val trip = _tripState.value ?: return
        val activeGroup = trip.groups.getOrNull(trip.activeGroupIndex) ?: return

        // Register geofence for last waypoint of active group
        val lastWaypointIndex = activeGroup.waypointEndIndex - 1
        val lastWaypoint = trip.waypoints[lastWaypointIndex]

        geofenceManager.removeAllGeofences {
            geofenceManager.registerGeofenceForWaypoint(lastWaypoint)
        }

        // Export to Google Maps
        exportGroupToGoogleMaps(trip, activeGroup)
    }

    fun startNextGroup() {
        viewModelScope.launch {
            // Send GROUP_COMPLETED event
            repository.sendEvent(EventType.GROUP_COMPLETED)

            // After server responds with updated state, start navigation
            // This will be handled by state collection
        }
    }

    fun optimizeRoute() {
        viewModelScope.launch {
            repository.sendEvent(EventType.OPTIMIZE_ROUTE)
        }
    }

    private fun exportGroupToGoogleMaps(context: Context, trip: TripSession, group: RouteGroup) {
        val waypoints = trip.waypoints.subList(group.waypointStartIndex, group.waypointEndIndex)
        val uri = buildGoogleMapsUri(waypoints)

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }

        // Fallback to browser if Google Maps app not installed
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun buildGoogleMapsUri(waypoints: List<Waypoint>): Uri {
        val origin = waypoints.first()
        val destination = waypoints.last()
        val viaPoints = waypoints.drop(1).dropLast(1)

        val waypointsParam = viaPoints.joinToString("|") { "${it.latitude},${it.longitude}" }

        return Uri.parse(
            "https://www.google.com/maps/dir/?api=1" +
            "&origin=${origin.latitude},${origin.longitude}" +
            "&destination=${destination.latitude},${destination.longitude}" +
            "&waypoints=$waypointsParam" +
            "&travelmode=driving"
        )
    }
}
```

2. **Update MobileApp.kt** - Add new UI elements:
```kotlin
// Add buttons for Optimize Route, Finalize Route, Start Navigation
// Show current group progress indicator
// Show trip status (PLANNING, NAVIGATING, COMPLETED)

@Composable
fun NavigationControls(
    tripState: TripSession?,
    onOptimize: () -> Unit,
    onFinalize: () -> Unit,
    onStartNavigation: () -> Unit
) {
    Column {
        when (tripState?.status) {
            TripStatus.PLANNING -> {
                Button(onClick = onOptimize) {
                    Text("Optimize Route")
                }
                Button(
                    onClick = onFinalize,
                    enabled = (tripState.waypoints.size >= 2)
                ) {
                    Text("Finalize Route")
                }
            }
            TripStatus.NAVIGATING -> {
                Text("Group ${tripState.activeGroupIndex + 1} of ${tripState.groups.size}")
                Button(onClick = onStartNavigation) {
                    Text("Start Navigation")
                }
            }
            TripStatus.COMPLETED -> {
                Text("All deliveries completed!")
            }
            null -> {
                Text("Loading...")
            }
        }
    }
}
```

**Verification**:
- Run app
- Add waypoints
- Tap Optimize Route and verify reordering
- Tap Finalize Route and verify groups are created
- Verify status changes to NAVIGATING

---

## Workstream C: Web (Kotlin/JS)

**Agent Assignment**: Web Agent
**Dependencies**: Phase 0 complete

### Task C1: Migrate from WASM to Kotlin/JS

**Files**:
- `composeApp/build.gradle.kts` - Update JS target config
- Rename `composeApp/src/wasmJsMain` to `composeApp/src/jsMain`
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/main.kt`

**Steps**:

1. **Rename directory**:
```bash
# In composeApp/src/
mv wasmJsMain jsMain
```

2. **Update `composeApp/build.gradle.kts`**:
```kotlin
kotlin {
    // Remove or comment out wasmJs target
    // wasmJs { ... }

    // Add JS target
    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        // Update source set names
        val jsMain by getting {
            dependencies {
                implementation(compose.html.core)
                implementation(compose.runtime)
                implementation(libs.ktor.client.js)  // Add to libs.versions.toml if not present
            }
        }
    }
}
```

3. **Update HttpClient in jsMain**:
```kotlin
// composeApp/src/jsMain/kotlin/br/gohan/dromedario/DiModules.kt
actual fun createHttpClient(): HttpClient = HttpClient(Js) {
    install(ContentNegotiation) {
        json()
    }
    install(WebSockets)
}
```

4. **Verify build**:
```bash
./gradlew jsBrowserDevelopmentWebpack
```

**Verification**:
- Build completes without errors
- Output exists at `composeApp/build/dist/js/developmentExecutable/`

---

### Task C2: Google Maps JavaScript Integration

**Files**:
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/MapController.kt` (NEW)
- `composeApp/src/jsMain/resources/index.html` (update)

**Steps**:

1. **Create MapController.kt**:
```kotlin
// composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/MapController.kt
package br.gohan.dromedario.map

import br.gohan.dromedario.data.Waypoint
import kotlinx.browser.document
import org.w3c.dom.Element

class MapController(containerId: String) {

    private val google = js("google")
    private val map: dynamic
    private val markers = mutableListOf<dynamic>()
    private var polyline: dynamic = null

    init {
        val container = document.getElementById(containerId)
        map = js("new google.maps.Map")(container, js("""({
            center: { lat: -30.05, lng: -51.20 },
            zoom: 12
        })"""))
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

    private fun addMarker(waypoint: Waypoint, label: Int) {
        val marker = js("new google.maps.Marker")( js("""({
            position: { lat: ${waypoint.latitude}, lng: ${waypoint.longitude} },
            map: this.map,
            label: "$label",
            title: "${waypoint.address}"
        })"""))
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
        val path = waypoints.map { wp ->
            js("({ lat: ${wp.latitude}, lng: ${wp.longitude} })")
        }.toTypedArray()

        polyline = js("new google.maps.Polyline")( js("""({
            path: path,
            geodesic: true,
            strokeColor: '#4285F4',
            strokeOpacity: 1.0,
            strokeWeight: 4
        })"""))
        polyline.setMap(map)
    }

    private fun fitBounds(waypoints: List<Waypoint>) {
        val bounds = js("new google.maps.LatLngBounds")(null)
        waypoints.forEach { wp ->
            bounds.extend(js("({ lat: ${wp.latitude}, lng: ${wp.longitude} })"))
        }
        map.fitBounds(bounds)
    }

    fun drawEncodedPolyline(encodedPolyline: String) {
        val decodePath = js("google.maps.geometry.encoding.decodePath")
        val path = decodePath(encodedPolyline)

        polyline?.setMap(null)
        polyline = js("new google.maps.Polyline")( js("""({
            path: path,
            geodesic: true,
            strokeColor: '#4285F4',
            strokeOpacity: 1.0,
            strokeWeight: 4
        })"""))
        polyline.setMap(map)
    }
}
```

2. **Update index.html**:
```html
<!DOCTYPE html>
<html>
<head>
    <title>Dromedario</title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body { margin: 0; display: flex; height: 100vh; }
        #compose-container { width: 400px; overflow-y: auto; padding: 16px; }
        #map-container { flex: 1; height: 100%; }
    </style>
</head>
<body>
    <div id="compose-container"></div>
    <div id="map-container"></div>

    <script src="https://maps.googleapis.com/maps/api/js?key=YOUR_API_KEY&libraries=geometry"></script>
    <script src="composeApp.js"></script>
</body>
</html>
```

**Verification**:
- Load page
- Verify map renders
- Call `mapController.updateWaypoints()` and verify markers appear

---

### Task C3: Web Authentication UI

**Files**:
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/auth/AuthRepository.kt` (NEW)
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/auth/LoginScreen.kt` (NEW)

**Steps**:

1. **Create AuthRepository (JS implementation)**:
```kotlin
// composeApp/src/jsMain/kotlin/br/gohan/dromedario/auth/AuthRepository.kt
package br.gohan.dromedario.auth

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

actual class AuthRepository {
    actual fun saveToken(token: String) {
        localStorage["dromedario_token"] = token
    }

    actual fun getToken(): String? = localStorage["dromedario_token"]

    actual fun clearToken() {
        localStorage.removeItem("dromedario_token")
    }
}
```

2. **Create shared AuthRepository expect declaration**:
```kotlin
// shared/src/commonMain/kotlin/br/gohan/dromedario/auth/AuthRepository.kt
package br.gohan.dromedario.auth

expect class AuthRepository {
    fun saveToken(token: String)
    fun getToken(): String?
    fun clearToken()
}
```

3. **Create LoginScreen composable**:
```kotlin
// composeApp/src/jsMain/kotlin/br/gohan/dromedario/auth/LoginScreen.kt
package br.gohan.dromedario.auth

import androidx.compose.runtime.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.attributes.*

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Div({ style { padding(16.px) } }) {
        H1 { Text("Dromedario") }

        Input(InputType.Password) {
            value(password)
            onInput { password = it.value }
            placeholder("Enter password")
        }

        Button({
            onClick {
                isLoading = true
                // Call login API
                // On success: onLoginSuccess(token)
                // On failure: error = "Invalid password"
            }
            disabled(isLoading)
        }) {
            Text(if (isLoading) "Logging in..." else "Login")
        }

        error?.let { Div({ style { color(Color.red) } }) { Text(it) } }
    }
}
```

4. **Update main.kt to check auth**:
```kotlin
// composeApp/src/jsMain/kotlin/br/gohan/dromedario/main.kt
fun main() {
    val authRepository = AuthRepository()
    val token = authRepository.getToken()

    renderComposable(rootElementId = "compose-container") {
        var isAuthenticated by remember { mutableStateOf(token != null) }

        if (isAuthenticated) {
            WebApp(token!!)
        } else {
            LoginScreen { newToken ->
                authRepository.saveToken(newToken)
                isAuthenticated = true
            }
        }
    }
}
```

**Verification**:
- Load page without token
- Verify login screen appears
- Enter correct password
- Verify app loads and token is stored

---

### Task C4: Web UI Updates

**Files**:
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/WebApp.kt`

**Steps**:

1. **Update WebApp to integrate MapController and state**:
```kotlin
// composeApp/src/jsMain/kotlin/br/gohan/dromedario/WebApp.kt
package br.gohan.dromedario

import androidx.compose.runtime.*
import br.gohan.dromedario.map.MapController
import br.gohan.dromedario.presenter.ClientSharedViewModel
import org.jetbrains.compose.web.dom.*

@Composable
fun WebApp(token: String) {
    val viewModel = remember { ClientSharedViewModel(token) }
    val tripState by viewModel.tripState.collectAsState()

    val mapController = remember { MapController("map-container") }

    // Update map when waypoints change
    LaunchedEffect(tripState?.waypoints) {
        tripState?.waypoints?.let { waypoints ->
            mapController.updateWaypoints(waypoints)
        }
    }

    Div({ id("controls") }) {
        // Waypoint list
        tripState?.waypoints?.forEach { waypoint ->
            Div({ classes("waypoint-card") }) {
                Text("${waypoint.index + 1}. ${waypoint.address}")
                Button({ onClick { viewModel.deleteWaypoint(waypoint.index) } }) {
                    Text("Remove")
                }
            }
        }

        // Controls based on status
        when (tripState?.status) {
            TripStatus.PLANNING -> {
                Button({ onClick { viewModel.optimizeRoute() } }) {
                    Text("Optimize Route")
                }
                Button({
                    onClick { viewModel.finalizeRoute() }
                    disabled((tripState?.waypoints?.size ?: 0) < 2)
                }) {
                    Text("Finalize Route")
                }
            }
            TripStatus.NAVIGATING -> {
                Div {
                    Text("Navigating: Group ${(tripState?.activeGroupIndex ?: 0) + 1} of ${tripState?.groups?.size ?: 0}")
                }
                Text("Use mobile app to continue navigation")
            }
            TripStatus.COMPLETED -> {
                Text("All deliveries completed!")
            }
            null -> Text("Loading...")
        }
    }
}
```

**Verification**:
- Load app
- Add waypoints
- Verify they appear in list and on map
- Test Optimize and Finalize buttons

---


## Phase 2: Integration

After all Phase 1 workstreams complete.

### Task I1: End-to-End Testing

**Description**: Verify all components work together.

**Test scenarios**:

1. **Authentication flow**:
   - Start server
   - Open web client
   - Login with password
   - Verify token stored
   - Verify WebSocket connects with token

2. **Waypoint sync**:
   - Add waypoint from web
   - Verify appears on Android
   - Add waypoint from Android
   - Verify appears on web
   - Verify map updates on both

3. **Route optimization**:
   - Add 5+ waypoints
   - Click Optimize
   - Verify order changes
   - Verify both clients updated

4. **Navigation flow**:
   - Finalize route with 15 waypoints
   - Verify 2 groups created (9 + 6)
   - Start navigation on Android
   - Verify Google Maps opens
   - Simulate geofence trigger
   - Verify notification appears
   - Tap notification
   - Verify next group exports

5. **Persistence**:
   - Create trip
   - Restart server
   - Verify trip restored from MongoDB

---

### Task I2: Build Automation

**Files**:
- `build.gradle.kts` (root)
- `server/build.gradle.kts`

**Description**: Automate copying web client to server resources.

**Steps**:

1. **Add Gradle task to copy web build**:
```kotlin
// server/build.gradle.kts

val copyWebClient by tasks.registering(Copy::class) {
    dependsOn(":composeApp:jsBrowserProductionWebpack")
    from("${project(":composeApp").buildDir}/dist/js/productionExecutable")
    into("$buildDir/resources/main/web")
}

tasks.named("processResources") {
    dependsOn(copyWebClient)
}
```

2. **Create build script** `build-all.sh`:
```bash
#!/bin/bash
set -e

echo "Building web client..."
./gradlew :composeApp:jsBrowserProductionWebpack

echo "Building server with web client..."
./gradlew :server:build

echo "Build complete!"
echo "Run with: java -jar server/build/libs/server.jar"
```

**Verification**:
- Run `./build-all.sh`
- Start server
- Navigate to `http://localhost:8080/`
- Verify web client loads

---

## Dependency Graph

```
Phase 0
├── Task 0.1: Data Models ──────┬─────────────────────────────────┐
└── Task 0.2: WebSocket Events ─┴───┬───────────────┬─────────────┤
                                    │               │             │
Phase 1                             ▼               ▼             ▼
├── Workstream A (Server)      A1 → A2 → A3 → A4                  │
├── Workstream B (Android)     B1 → B2 → B3 → B4                  │
└── Workstream C (Web)         C1 → C2 → C3 → C4                  │
                                    │               │             │
Phase 2                             └───────────────┴─────────────┤
├── Task I1: E2E Testing ◄────────────────────────────────────────┤
└── Task I2: Build Automation ◄───────────────────────────────────┘
```

---

## Agent Assignments Summary

| Workstream | Agent Name | Tasks | Can Parallel With |
|------------|------------|-------|-------------------|
| Foundation | Any | 0.1, 0.2 | None (blocking) |
| A: Server | Server Agent | A1, A2, A3, A4 | B, C |
| B: Android | Android Agent | B1, B2, B3, B4 | A, C |
| C: Web | Web Agent | C1, C2, C3, C4 | A, B |
| Integration | Any | I1, I2 | None (after Phase 1) |

---

## Files Changed Summary

### New Files
- `shared/src/commonMain/kotlin/br/gohan/dromedario/data/TripModels.kt`
- `shared/src/commonMain/kotlin/br/gohan/dromedario/auth/AuthRepository.kt`
- `server/src/main/kotlin/br/gohan/dromedario/auth/AuthService.kt`
- `server/src/main/kotlin/br/gohan/dromedario/auth/AuthModels.kt`
- `server/src/main/kotlin/br/gohan/dromedario/routes/OptimizeRoute.kt`
- `server/src/main/resources/application.conf`
- `server/src/main/resources/web/index.html`
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/permissions/PermissionHelper.kt`
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/geofence/GeofenceManagerHelper.kt`
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/geofence/GeofenceBroadcastReceiver.kt`
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/geofence/NotificationHelper.kt`
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/map/MapController.kt`
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/auth/AuthRepository.kt`
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/auth/LoginScreen.kt`

### Modified Files
- `shared/src/commonMain/kotlin/br/gohan/dromedario/data/MessageModel.kt`
- `shared/src/commonMain/kotlin/br/gohan/dromedario/presenter/ClientSharedViewModel.kt`
- `server/build.gradle.kts`
- `server/src/main/kotlin/br/gohan/dromedario/ServerApp.kt`
- `server/src/main/kotlin/br/gohan/dromedario/DatabaseManager.kt`
- `composeApp/build.gradle.kts`
- `composeApp/src/androidMain/AndroidManifest.xml`
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/presenter/MobileViewModel.kt`
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/presenter/MobileApp.kt`
- `composeApp/src/androidMain/kotlin/br/gohan/dromedario/di/DiModules.kt`
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/main.kt` (renamed from wasmJsMain)
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/WebApp.kt`
- `composeApp/src/jsMain/kotlin/br/gohan/dromedario/DiModules.kt`

---

## Success Criteria (from Research Document)

- [ ] User must authenticate with password to access app (web and mobile)
- [ ] User can add 20+ waypoints via web or mobile
- [ ] Route auto-draws on map as waypoints are added
- [ ] "Optimize Route" reorders waypoints for shortest distance
- [ ] Clicking "Start Navigation" splits into groups of 9
- [ ] First group exports to Google Maps
- [ ] Geofence triggers when user arrives at last waypoint
- [ ] Notification appears prompting for next group
- [ ] Responding to notification exports next group
- [ ] All trips persist in MongoDB
- [ ] Web and mobile stay synchronized throughout
- [ ] Web client served from same server as API

---

*Planning Phase Complete: 2026-01-27*
*Ready for Implementation*
