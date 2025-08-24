package br.gohan.dromedario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Composable
fun MobileApp(client: HttpClient) {
    val viewModel = remember { MainViewModel(client) }
    val incomingMessages by viewModel.incomingFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startWebSocket("ws://10.0.2.2:8080/ws")
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(incomingMessages.toString())
            Button(onClick = {
                viewModel.sendMessage(
                    Json.encodeToString(WebSocketMessage(
                        event = AppEvents.ADD_ADDRESS,
                        data =  Json.encodeToJsonElement(
                            Waypoint(
                                address = "Avenida 1",
                                latitude = 1.2,
                                longitude = 1.1
                        ))
                    ))
                )
            } ) {
                Text("send message")
            }
        }
    }
}