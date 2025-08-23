package br.gohan.dromedario

import android.content.ContentValues.TAG
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.time.delay
import kotlin.time.Duration.Companion.seconds

class MainViewModel(
    private val client: HttpClient
) : ViewModel() {

    // 1. Ajuste no nome da variável privada e pública
    private val _outgoingFlow = MutableSharedFlow<String>()
    val outgoingFlow: SharedFlow<String?> = _outgoingFlow

    private val _incomingFlow = MutableStateFlow<List<String>>(emptyList())
    val incomingFlow: StateFlow<List<String>> = _incomingFlow

    fun sendMessage(string: String) {
        viewModelScope.launch {
            _outgoingFlow.emit(string)
        }
    }

    fun startWebSocket(url: String) {
        viewModelScope.launch {
            try {
                client.webSocket(urlString = url) {
                    launch {
                        outgoingFlow
                            .filterNotNull()
                            .collect { msg ->
                                send(Frame.Text(msg))
                            }
                    }

                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            _incomingFlow.value = _incomingFlow.value + text
                        }
                    }
                }
            } catch (err : Exception) {
                Log.e(TAG, "startWebSocket $err")
                delay(3.seconds)
                startWebSocket(url)
            }
        }
    }

    companion object {
        const val TAG = "MainViewModel"
    }
}
