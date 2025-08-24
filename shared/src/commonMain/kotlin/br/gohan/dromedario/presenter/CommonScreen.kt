package br.gohan.dromedario.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient

@Composable
fun CommonScreen(url: String, client: HttpClient) {
    val viewModel = remember { ClientViewModel(client) }
    val incomingMessages by viewModel.incomingFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startWebSocket(url)
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LazyColumn {
                items(incomingMessages.waypoints) { waypoint ->
                    Card {
                        Row {
                            Text("Address: ${waypoint.address}, Index: ${waypoint.index}")
                            Button(onClick = {viewModel.deleteMessage(waypoint.index) }) { Text("Delete") }
                        }
                    }
                }
            }
            Button(onClick = {
                viewModel.sendMessage("New Route", incomingMessages.waypoints.maxOfOrNull { it.index } ?: 0)
                Napier.d("WebApp: sending message to server")
            }) {
                Text("add waypoint")
            }
        }
    }
}