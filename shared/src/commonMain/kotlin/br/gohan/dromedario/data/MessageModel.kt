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
    // Existing
    ADD_WAYPOINT,
    REMOVE_WAYPOINT,

    // Route Management
    OPTIMIZE_ROUTE,      // Client → Server: Request optimization
    FINALIZE_ROUTE,      // Client → Server: Lock waypoints, generate groups
    GROUP_COMPLETED,     // Client → Server: Mark current group done

    // State Sync
    SYNC_STATE,          // Server → Client: Full TripSession sync
    TRIP_STATUS_CHANGED, // Server → Client: Status changed notification

    // Navigation
    EXPORT_GROUP,        // Internal: Trigger Google Maps export
    REORDER_WAYPOINTS    // Client → Server: New waypoint order after optimization
}
