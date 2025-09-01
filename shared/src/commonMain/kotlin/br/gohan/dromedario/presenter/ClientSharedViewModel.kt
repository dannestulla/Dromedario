package br.gohan.dromedario.presenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.dromedario.data.EventType
import br.gohan.dromedario.data.RemoveWaypointData
import br.gohan.dromedario.data.RouteStateModel
import br.gohan.dromedario.data.Waypoint
import br.gohan.dromedario.data.MessageModel
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

    fun sendMessage(address: String, index: Int) {
        viewModelScope.launch {
            val message = MessageModel(
                event = EventType.ADD_WAYPOINT,
                data = Json.Default.encodeToJsonElement(
                    Waypoint(
                        index = index,
                        address = address,
                        latitude = 1.2,
                        longitude = 1.1
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
                data = Json.Default.encodeToJsonElement(
                    RemoveWaypointData(
                        waypointIndex = index
                    )
                )
            )
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
                                val message = Json.Default.encodeToString(msg)
                                send(Frame.Text(message))
                            }
                    }

                    // Server to client
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            Napier.d("Client: recebendo mensagem do servidor: $text")
                            val incomingObject = Json.Default.decodeFromString<RouteStateModel>(text)
                            _incomingFlow.value = incomingObject
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

    companion object {
        const val TAG = "MainViewModel"
    }
}