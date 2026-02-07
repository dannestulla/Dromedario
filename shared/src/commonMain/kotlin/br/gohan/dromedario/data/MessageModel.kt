package br.gohan.dromedario.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MessageModel(
    val event: EventType,
    val data: JsonElement? = null
)

@Serializable
enum class EventType {
    ADD_WAYPOINT,
    REMOVE_WAYPOINT,
    OPTIMIZE_ROUTE,
    FINALIZE_ROUTE,
    GROUP_COMPLETED,
    SYNC_STATE,
    REORDER_WAYPOINTS,
    CLEAR_ALL
}
