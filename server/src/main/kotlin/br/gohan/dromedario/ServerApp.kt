package br.gohan.dromedario

import br.gohan.dromedario.auth.AuthService
import br.gohan.dromedario.auth.ErrorResponse
import br.gohan.dromedario.auth.LoginRequest
import br.gohan.dromedario.auth.LoginResponse
import br.gohan.dromedario.data.*
import br.gohan.dromedario.routes.RouteOptimizer
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

// Track connected WebSocket sessions
private val sessions = Collections.synchronizedSet(mutableSetOf<WebSocketServerSession>())

// Secrets loaded from secrets.properties
private lateinit var secrets: Properties

fun loadSecrets(): Properties {
    val props = Properties()
    // Try project root first, then current directory
    val locations = listOf(
        File("../secrets.properties"),  // When running from server/
        File("secrets.properties"),      // When running from project root
        File("../../secrets.properties") // Fallback
    )

    for (file in locations) {
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
            println("Loaded secrets from: ${file.absolutePath}")
            break
        }
    }

    if (props.isEmpty) {
        println("Warning: secrets.properties not found, using application.conf defaults")
    }

    return props
}

fun getSecret(key: String, default: String = ""): String {
    return secrets.getProperty(key)?.trim() ?: default
}

suspend fun main() {
    // Load secrets from secrets.properties
    secrets = loadSecrets()

    val db = DatabaseManager()
    db.setup()

    Napier.base(DebugAntilog())

    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0") {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        install(CORS) {
            anyHost()
            allowHeader(HttpHeaders.ContentType)
            allowHeader(HttpHeaders.Authorization)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Options)
            allowNonSimpleContentTypes = true
        }
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        module(db)
    }.start(wait = true)
}

@OptIn(ExperimentalTime::class)
fun Application.module(db: DatabaseManager) {
    // Read secrets from secrets.properties (falls back to application.conf defaults)
    val jwtSecret = getSecret("JWT_SECRET").ifEmpty {
        environment.config.propertyOrNull("jwt.secret")?.getString() ?: "dev-secret-change-in-production"
    }
    val appPassword = getSecret("APP_PASSWORD").ifEmpty {
        environment.config.propertyOrNull("app.password")?.getString() ?: "dromedario123"
    }
    val routesApiKey = getSecret("GOOGLE_ROUTES_API_KEY").ifEmpty {
        environment.config.propertyOrNull("google.routesApiKey")?.getString() ?: ""
    }

    val authService = AuthService(jwtSecret, appPassword)
    val routeOptimizer = if (routesApiKey.isNotEmpty()) RouteOptimizer(routesApiKey) else null

    routing {
        // Login endpoint (no auth required)
        post("/api/login") {
            try {
                val request = call.receive<LoginRequest>()
                if (authService.validatePassword(request.password)) {
                    call.respond(LoginResponse(authService.generateToken()))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid password"))
                }
            } catch (e: Exception) {
                Napier.e("Login error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
            }
        }

        // WebSocket endpoint with auth
        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
            if (!authService.validateToken(token)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }

            Napier.i("New authenticated client connected")
            sessions.add(this)

            // Sends initial state to client
            launch {
                try {
                    val currentState = db.getCurrentState()
                    val json = Json.encodeToString(currentState)
                    send(Frame.Text(json))
                    Napier.i("Initial state sent: ${currentState.waypoints.size} waypoints")
                } catch (e: Exception) {
                    Napier.e("Error sending initial state: ${e.message}")
                }
            }

            // Listen to database updates
            launch {
                db.collect { routeState ->
                    runCatching {
                        val json = Json.encodeToString(routeState)
                        send(Frame.Text(json))
                        Napier.i("Update sent: ${routeState.waypoints.size} waypoints")
                    }.onFailure {
                        Napier.e("Error sending update: ${it.message}")
                    }
                }
            }

            // Process received messages
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val receivedText = frame.readText()
                        Napier.i("Message received: $receivedText")

                        try {
                            val message = Json.decodeFromString<MessageModel>(receivedText)
                            handleMessage(message, db, routeOptimizer)
                        } catch (e: Exception) {
                            Napier.e("Error processing message: ${e.message}")
                            send(Frame.Text("""{"type": "error", "message": "Error: ${e.message}"}"""))
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                Napier.i("Client disconnected normally")
            } catch (e: Exception) {
                Napier.e("WebSocket connection error: ${e.message}")
            } finally {
                sessions.remove(this)
                Napier.i("Client disconnected")
            }
        }

        // Static file hosting for web client (must be last)
        staticResources("/", "web") {
            default("index.html")
        }
    }
}

@OptIn(ExperimentalTime::class)
private suspend fun handleMessage(
    message: MessageModel,
    db: DatabaseManager,
    routeOptimizer: RouteOptimizer?
) {
    when (message.event) {
        EventType.ADD_WAYPOINT -> {
            val data = message.data
            if (data == null) {
                Napier.e("Null data for ADD_WAYPOINT")
                return
            }
            val waypoint = Json.decodeFromJsonElement<Waypoint>(data)
            db.addWaypoint(waypoint)
            Napier.i("Waypoint added: ${waypoint.address}")
        }

        EventType.REMOVE_WAYPOINT -> {
            val data = message.data
            if (data == null) {
                Napier.e("Null data for REMOVE_WAYPOINT")
                return
            }
            val removeData = Json.decodeFromJsonElement<RemoveWaypointData>(data)
            db.removeWaypoint(removeData.waypointIndex)
            Napier.i("Waypoint removed at index: ${removeData.waypointIndex}")
        }

        EventType.OPTIMIZE_ROUTE -> {
            if (routeOptimizer == null) {
                Napier.w("Route optimizer not configured - Google Routes API key missing")
                return
            }
            val currentState = db.getCurrentState()
            if (currentState.waypoints.size < 2) {
                Napier.w("Not enough waypoints to optimize")
                return
            }
            try {
                val optimizedWaypoints = routeOptimizer.optimizeWaypoints(currentState.waypoints)
                db.updateWaypoints(optimizedWaypoints)
                Napier.i("Route optimized - waypoints reordered")
            } catch (e: Exception) {
                Napier.e("Error optimizing route: ${e.message}")
            }
        }

        EventType.FINALIZE_ROUTE -> {
            val currentState = db.getCurrentState()
            if (currentState.waypoints.isEmpty()) {
                Napier.w("No waypoints to finalize")
                return
            }
            val groups = generateGroups(currentState.waypoints)
            val updatedTrip = TripSession(
                id = currentState.id,
                waypoints = currentState.waypoints,
                groups = groups,
                activeGroupIndex = 0,
                status = TripStatus.NAVIGATING,
                createdAt = Clock.System.now().epochSeconds,
                updatedAt = Clock.System.now().epochSeconds
            )
            db.updateTripSession(updatedTrip)
            broadcastSyncState(updatedTrip)
            Napier.i("Route finalized - ${groups.size} groups created")
        }

        EventType.GROUP_COMPLETED -> {
            val trip = db.getCurrentTripSession()
            if (trip == null) {
                Napier.w("No active trip session")
                return
            }
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
            db.updateTripSession(updatedTrip)
            broadcastSyncState(updatedTrip)
            Napier.i("Group ${trip.activeGroupIndex} completed, advancing to group $newActiveIndex")
        }

        EventType.REORDER_WAYPOINTS -> {
            val data = message.data
            if (data == null) {
                Napier.e("Null data for REORDER_WAYPOINTS")
                return
            }
            val newOrder = Json.decodeFromJsonElement<List<Waypoint>>(data)
            db.updateWaypoints(newOrder)
            Napier.i("Waypoints reordered")
        }

        else -> {
            Napier.w("Unhandled event type: ${message.event}")
        }
    }
}

/**
 * Generates route groups from waypoints, with max 9 waypoints per group.
 * This is to comply with Google Maps URL limit.
 */
fun generateGroups(waypoints: List<Waypoint>, maxGroupSize: Int = 9): List<RouteGroup> {
    if (waypoints.isEmpty()) return emptyList()

    val groups = mutableListOf<RouteGroup>()
    var startIndex = 0
    var groupIndex = 0

    while (startIndex < waypoints.size) {
        val endIndex = minOf(startIndex + maxGroupSize, waypoints.size)
        groups.add(
            RouteGroup(
                index = groupIndex,
                waypointStartIndex = startIndex,
                waypointEndIndex = endIndex,
                status = if (groupIndex == 0) GroupStatus.ACTIVE else GroupStatus.PENDING
            )
        )
        startIndex = endIndex
        groupIndex++
    }

    return groups
}

/**
 * Broadcasts a SYNC_STATE message to all connected clients.
 */
private suspend fun broadcastSyncState(trip: TripSession) {
    val syncMessage = MessageModel(
        event = EventType.SYNC_STATE,
        data = Json.encodeToJsonElement(trip)
    )
    val json = Json.encodeToString(syncMessage)

    sessions.forEach { session ->
        try {
            session.send(Frame.Text(json))
        } catch (e: Exception) {
            Napier.e("Error broadcasting to session: ${e.message}")
        }
    }
}
