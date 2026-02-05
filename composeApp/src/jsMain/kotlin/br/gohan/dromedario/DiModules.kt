package br.gohan.dromedario

import br.gohan.dromedario.map.MapsLoader
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

// Koin DI module for the web client. Registers HttpClient and MapsLoader as singletons.
val webModule = module {
    single<HttpClient> {
        HttpClient(Js) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    single { MapsLoader() }
}
