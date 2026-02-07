package br.gohan.dromedario.presenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.dromedario.data.EventType
import br.gohan.dromedario.data.MessageModel
import br.gohan.dromedario.data.RemoveWaypointData
import br.gohan.dromedario.data.RouteStateModel
import br.gohan.dromedario.data.Waypoint
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Duration.Companion.seconds

class ClientSharedViewModel(
    private val client: HttpClient
) : ViewModel() {
    private val _outgoingFlow = MutableSharedFlow<MessageModel>()
    val outgoingFlow: SharedFlow<MessageModel?> = _outgoingFlow

    private val _incomingFlow = MutableStateFlow(RouteStateModel())
    val incomingFlow: StateFlow<RouteStateModel> = _incomingFlow

    fun sendMessage(address: String, index: Int, latitude: Double = 0.0, longitude: Double = 0.0) {
        viewModelScope.launch {
            val message = MessageModel(
                event = EventType.ADD_WAYPOINT,
                data = Json.encodeToJsonElement(
                    Waypoint(
                        index = index,
                        address = address,
                        latitude = latitude,
                        longitude = longitude
                    )
                )
            )
            _outgoingFlow.emit(message)
        }
    }

    fun deleteMessage(index: Int) {
        viewModelScope.launch {
            val message = MessageModel(
                event = EventType.REMOVE_WAYPOINT,
                data = Json.encodeToJsonElement(
                    RemoveWaypointData(
                        waypointIndex = index
                    )
                )
            )
            _outgoingFlow.emit(message)
        }
    }

    fun updateWaypoint(index: Int, address: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val current = _incomingFlow.value.waypoints.toMutableList()
            if (index !in current.indices) return@launch
            current[index] = current[index].copy(
                address = address,
                latitude = latitude,
                longitude = longitude
            )
            val message = MessageModel(
                event = EventType.REORDER_WAYPOINTS,
                data = Json.encodeToJsonElement(current)
            )
            _outgoingFlow.emit(message)
        }
    }

    fun reorderWaypoints(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val current = _incomingFlow.value.waypoints.toMutableList()
            if (fromIndex !in current.indices || toIndex !in current.indices) return@launch
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            val reindexed = current.mapIndexed { i, wp -> wp.copy(index = i) }
            val message = MessageModel(
                event = EventType.REORDER_WAYPOINTS,
                data = Json.encodeToJsonElement(reindexed)
            )
            _outgoingFlow.emit(message)
        }
    }

    fun sendEvent(message: MessageModel) {
        viewModelScope.launch {
            Napier.d("ClientSharedViewModel: Sending event ${message.event}")
            _outgoingFlow.emit(message)
        }
    }

    fun clearAllWaypoints() {
        viewModelScope.launch {
            val message = MessageModel(event = EventType.CLEAR_ALL)
            _outgoingFlow.emit(message)
        }
    }

    fun startWebSocket(url: String) {
        viewModelScope.launch {
            try {
                client.webSocket(urlString = url) {
                    launch {
                        // Client to server
                        outgoingFlow
                            .collect { msg ->
                                Napier.d("Client: enviando mensagem para servidor: $msg")
                                val message = Json.encodeToString(msg)
                                send(Frame.Text(message))
                            }
                    }

                    // Server to client
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            Napier.d("Client: recebendo mensagem do servidor: $text")
                            handleIncomingMessage(text)
                        }
                    }
                }
            } catch (err : Exception) {
                Napier.e("Client: Erro WebSocket: ${err.message}")
                delay(3.seconds)
                startWebSocket(url)
            }
        }
    }

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private fun handleIncomingMessage(text: String) {
        try {
            val routeState = jsonParser.decodeFromString<RouteStateModel>(text)
            _incomingFlow.value = routeState
        } catch (e: Exception) {
            Napier.e("Client: Failed to parse incoming message: ${e.message}")
        }
    }

    companion object {
        const val TAG = "MainViewModel"
    }
}