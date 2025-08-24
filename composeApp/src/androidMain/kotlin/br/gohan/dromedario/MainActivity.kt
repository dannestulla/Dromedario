package br.gohan.dromedario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Napier.base(DebugAntilog())
        val client = HttpClient(CIO) {
            install(WebSockets)
        }
        setContent {
            MobileApp(client)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {

}