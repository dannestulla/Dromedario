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
val sessions = Collections.synchronizedSet(mutableSetOf<WebSocketServerSession>())

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

        // WebSocket endpoint (auth optional for now)
        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
            if (token != null && !authService.validateToken(token)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }

            Napier.i("New client connected (authenticated: ${token != null})")
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

        // Export web app (wasmJs Canvas-based, for mobile)
        staticResources("/export", "export") {
            default("index.html")
        }

        // Static file hosting for web client (must be last)
        staticResources("/", "web") {
            default("index.html")
        }
    }
}



