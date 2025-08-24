package br.gohan.dromedario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.time.Duration.Companion.seconds

class MainViewModel(
    private val client: HttpClient
) : ViewModel() {
    private val _outgoingFlow = MutableSharedFlow<String>()
    val outgoingFlow: SharedFlow<String?> = _outgoingFlow

    private val _incomingFlow = MutableStateFlow<List<String>>(emptyList())
    val incomingFlow: StateFlow<List<String>> = _incomingFlow

    fun sendMessage(string: String) {
        viewModelScope.launch {
            _outgoingFlow.emit(string)
        }
    }
    
    fun addWaypoint(waypoint: Waypoint) {
        viewModelScope.launch {
            val message = WebSocketMessage(
                event = AppEvents.ADD_ADDRESS,
                data = Json.encodeToJsonElement(waypoint)
            )
            val jsonString = Json.encodeToString(message)
            Napier.d("Client: enviando addWaypoint: $jsonString")
            _outgoingFlow.emit(jsonString)
        }
    }
    
    fun sendTestMessage() {
        viewModelScope.launch {
            val testWaypoint = Waypoint(
                address = "Rua Teste, 123",
                latitude = -30.0346,
                longitude = -51.2177
            )
            addWaypoint(testWaypoint)
        }
    }

    fun startWebSocket(url: String) {
        viewModelScope.launch {
            try {
                client.webSocket(urlString = url) {
                    launch {
                        outgoingFlow
                            .collect { msg ->
                                Napier.d("Client: enviando mensagem para servidor: $msg")
                                msg?.let { send(Frame.Text(it)) }
                            }
                    }

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            Napier.d("Client: recebendo mensagem do servidor: $text")
                            _incomingFlow.value = _incomingFlow.value + text
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