package br.gohan.dromedario

import br.gohan.dromedario.data.EventType
import br.gohan.dromedario.data.MessageModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

suspend fun main() {
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
        module(db)
    }.start(wait = true)
}

fun Application.module(db: DatabaseManager) {
    routing {
        webSocket("/ws") {
            Napier.i("🔌 New client connected")
            
            // Sends initial state to client
            launch {
                try {
                    val currentState = db.getCurrentState()
                    val json = Json.encodeToString(currentState)
                    send(Frame.Text(json))
                    Napier.i("📤 Initial state sent: ${currentState.waypoints.size} waypoints")
                } catch (e: Exception) {
                    Napier.e("❌ Error sending initial state: ${e.message}")
                }
            }

            // Listen to database updates
            launch {
                db.collect { routeState ->
                    runCatching {
                        val json = Json.encodeToString(routeState)
                        send(Frame.Text(json))
                        Napier.i("📤 Update sent: ${routeState.waypoints.size} waypoints")
                    }.onFailure {
                        Napier.e("❌ Error sending update: ${it.message}")
                    }
                }
            }

            // Process received messages
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val receivedText = frame.readText()
                        Napier.i("📨 Message received: $receivedText")
                        
                        try {
                            val message = Json.decodeFromString<MessageModel>(receivedText)
                            val processedMessage = processMessage(message)
                            
                            if (processedMessage != null) {
                                when (processedMessage.type) {
                                    EventType.ADD_WAYPOINT -> {
                                        processedMessage.waypoint?.let { waypoint ->
                                            db.addWaypoint(waypoint)
                                            Napier.i("✅ Waypoint added: ${waypoint.address}")
                                        }
                                    }
                                    EventType.REMOVE_WAYPOINT -> {
                                        processedMessage.waypointIndex?.let { index ->
                                            db.removeWaypoint(index)
                                            Napier.i("✅ Waypoint removed at index: $index")
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Napier.e("❌ Error processing message: ${e.message}")
                            // Error response for debugging
                            send(Frame.Text("""{"type": "error", "message": "Error: ${e.message}"}"""))
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.e("❌ WebSocket connection error: ${e.message}")
            } finally {
                Napier.i("🔌 Client disconnected")
            }
        }
    }
}