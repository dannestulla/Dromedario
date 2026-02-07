package br.gohan.dromedario

import br.gohan.dromedario.auth.AuthService
import br.gohan.dromedario.auth.ErrorResponse
import br.gohan.dromedario.auth.GoogleLoginRequest
import br.gohan.dromedario.auth.LoginRequest
import br.gohan.dromedario.auth.LoginResponse
import br.gohan.dromedario.data.MessageModel
import br.gohan.dromedario.routes.RouteOptimizer
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.ExperimentalTime

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
    val googleClientId = getSecret("GOOGLE_CLIENT_ID").ifEmpty { "" }

    val authService = AuthService(jwtSecret, appPassword, googleClientId)
    val routeOptimizer = if (routesApiKey.isNotEmpty()) RouteOptimizer(routesApiKey) else null
    val httpClient = HttpClient(CIO)

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

        // Google SSO login endpoint
        post("/api/auth/google") {
            try {
                val request = call.receive<GoogleLoginRequest>()
                if (authService.verifyGoogleToken(httpClient, request.credential)) {
                    call.respond(LoginResponse(authService.generateToken()))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid Google token"))
                }
            } catch (e: Exception) {
                Napier.e("Google auth error: ${e.message}")
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Authentication failed"))
            }
        }

        // WebSocket endpoint (auth required)
        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
            if (token == null || !authService.validateToken(token)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Authentication required"))
                return@webSocket
            }

            Napier.i("New client connected (authenticated)")
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

        // Serve compiled Kotlin/JS from webpack output (development)
        val webpackDir = File("../composeApp/build/kotlin-webpack/js/developmentExecutable")
        if (webpackDir.exists()) {
            staticFiles("/", webpackDir)
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
