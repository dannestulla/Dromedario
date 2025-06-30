package br.gohan.dromedario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged

class MainViewModel(
    private val client: HttpClient
) : ViewModel() {

    // 1. Ajuste no nome da variável privada e pública
    private val _outgoingFlow = MutableStateFlow<String?>(null)
    val outgoingFlow: StateFlow<String?> = _outgoingFlow

    private val _incomingFlow = MutableStateFlow<List<String>>(emptyList())
    val incomingFlow: StateFlow<List<String>> = _incomingFlow

    fun sendMessage(string: String) {
        viewModelScope.launch {
            _outgoingFlow.emit(string)
        }
    }

    fun startWebSocket(url: String) {
        viewModelScope.launch {
            client.webSocket(method = HttpMethod.Get, host = url, path = "/") {
                // 2. Envio: observa o flow de saída
                launch {
                    outgoingFlow
                        .filterNotNull()
                        .distinctUntilChanged()
                        .collect { msg ->
                            send(Frame.Text(msg))
                        }
                }

                // 3. Recebimento: use o canal `incoming` do WebSocketSession
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        _incomingFlow.value = _incomingFlow.value + text
                    }
                }
            }
        }
    }
}
