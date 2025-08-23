package br.gohan.dromedario.events

import br.gohan.dromedario.AppEvents
import br.gohan.dromedario.RouteState
import br.gohan.dromedario.WebSocketMessage
import br.gohan.dromedario.Waypoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

fun handleEvent(message: WebSocketMessage): RouteState {
    return when (message.event) {
        AppEvents.ADD_ADDRESS -> {
            try {
                val waypointData = Json.decodeFromJsonElement<Waypoint>(message.data)
                RouteState(waypoints = listOf(waypointData))
            } catch (e: Exception) {
                println("❌ Erro ao decodificar waypoint: ${e.message}")
                RouteState() // Retorna estado vazio em caso de erro
            }
        }
        AppEvents.REMOVE_ADDRESS -> {
            try {
                // Esperamos que data contenha: {"routeId": "id", "waypointIndex": 0}
                val removeData = Json.decodeFromJsonElement<RemoveWaypointData>(message.data)
                // Para remoção, retornamos um estado especial que será processado diferente
                RouteState(id = removeData.routeId)
            } catch (e: Exception) {
                println("❌ Erro ao decodificar dados de remoção: ${e.message}")
                RouteState()
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class RemoveWaypointData(
    val routeId: String,
    val waypointIndex: Int
)