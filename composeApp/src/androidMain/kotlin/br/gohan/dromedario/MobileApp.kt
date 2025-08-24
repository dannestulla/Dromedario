package br.gohan.dromedario

import androidx.compose.runtime.Composable
import br.gohan.dromedario.presenter.CommonScreen
import io.ktor.client.HttpClient

@Composable
fun MobileApp(client: HttpClient) {

    val url = "ws://10.0.2.2:8080/ws"
    CommonScreen(url, client)
}