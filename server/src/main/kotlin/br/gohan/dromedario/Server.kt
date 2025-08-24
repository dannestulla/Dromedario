package br.gohan.dromedario

import br.gohan.dromedario.events.handleEvent
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
import kotlinx.serialization.json.decodeFromJsonElement
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
            Napier.i("🔌 Novo cliente conectado")
            
            // Envia todos os dados existentes para o novo cliente
            launch {
                try {
                    val existingRoutes = db.getAllRoutes()
                    existingRoutes.forEach { route ->
                        val json = Json.encodeToString(route)
                        send(Frame.Text(json))
                        Napier.i("📤 Dados existentes enviados: ${route.id}")
                    }
                } catch (e: Exception) {
                    Napier.e("❌ Erro ao enviar dados existentes: ${e.message}")
                }
            }

            // Escuta updates em tempo real do banco
            launch {
                db.collect { routeState ->
                    runCatching {
                        val json = Json.encodeToString(routeState)
                        send(Frame.Text(json))
                        Napier.i("📤 Update enviado para cliente: ${routeState.id}")
                    }.onFailure {
                        Napier.e("❌ Erro ao enviar update: ${it.message}")
                    }
                }
            }

            // Processa mensagens do cliente
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val receivedText = frame.readText()
                        Napier.i("📨 Servidor recebeu: $receivedText")
                        
                        try {
                            val message = Json.decodeFromString<WebSocketMessage>(receivedText)
                            
                            when (message.event) {
                                AppEvents.ADD_ADDRESS -> {
                                    val state = handleEvent(message)
                                    db.addWaypoint(state)
                                    Napier.i("✅ Waypoint adicionado: ${state.waypoints}")
                                }
                                AppEvents.REMOVE_ADDRESS -> {
                                    val removeData = Json.decodeFromJsonElement<br.gohan.dromedario.events.RemoveWaypointData>(message.data)
                                    db.removeWaypoint(removeData.routeId, removeData.waypointIndex)
                                    Napier.i("✅ Waypoint removido do route ${removeData.routeId}, index ${removeData.waypointIndex}")
                                }
                            }
                        } catch (e: Exception) {
                            Napier.e("❌ Erro ao processar mensagem: ${e.message}")
                            // Só para teste - responde qualquer mensagem
                            send(Frame.Text("""{"type": "echo", "message": "Servidor recebeu: $receivedText"}"""))
                        }
                    }
                }
            } catch (e: Exception) {
                Napier.e("❌ Erro na conexão: ${e.message}")
            } finally {
                Napier.i("🔌 Cliente desconectado")
            }
        }
    }
}