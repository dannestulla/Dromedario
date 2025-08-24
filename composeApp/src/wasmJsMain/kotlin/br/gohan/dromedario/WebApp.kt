package br.gohan.dromedario

import androidx.compose.runtime.Composable
import br.gohan.dromedario.presenter.CommonScreen
import io.ktor.client.HttpClient

@Composable
fun WebApp(client: HttpClient) {

    val url = "ws://127.0.0.1:8080/ws"
    CommonScreen(url, client)
}