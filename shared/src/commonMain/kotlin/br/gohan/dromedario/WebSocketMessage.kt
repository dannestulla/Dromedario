package br.gohan.dromedario

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class WebSocketMessage(
    val event: AppEvents,
    val data: JsonElement
)

enum class AppEvents {
    ADD_ADDRESS,
    REMOVE_ADDRESS
}
