package br.gohan.dromedario

import br.gohan.dromedario.data.EventType
import br.gohan.dromedario.data.RemoveWaypointData
import br.gohan.dromedario.data.MessageModel
import br.gohan.dromedario.data.Waypoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Legacy message processing function.
 * Kept for backwards compatibility during migration.
 * New message handling is done directly in ServerApp.kt handleMessage function.
 */
fun processMessage(message: MessageModel): ProcessedMessage? {
    return when (message.event) {
        EventType.ADD_WAYPOINT -> {
            try {
                val data = message.data ?: throw Exception("Null data for ADD_WAYPOINT")
                val waypointData = Json.decodeFromJsonElement<Waypoint>(data)
                ProcessedMessage(
                    type = EventType.ADD_WAYPOINT,
                    waypoint = waypointData
                )
            } catch (e: Exception) {
                println("Error processing ADD_WAYPOINT: ${e.message}")
                null
            }
        }
        EventType.REMOVE_WAYPOINT -> {
            try {
                val data = message.data ?: throw Exception("Null data for REMOVE_WAYPOINT")
                val removeData = Json.decodeFromJsonElement<RemoveWaypointData>(data)
                ProcessedMessage(
                    type = EventType.REMOVE_WAYPOINT,
                    waypointIndex = removeData.waypointIndex
                )
            } catch (e: Exception) {
                println("Error processing REMOVE_WAYPOINT: ${e.message}")
                null
            }
        }
        // New event types are handled in ServerApp.kt handleMessage function
        else -> null
    }
}

data class ProcessedMessage(
    val type: EventType,
    val waypoint: Waypoint? = null,
    val waypointIndex: Int? = null
)
