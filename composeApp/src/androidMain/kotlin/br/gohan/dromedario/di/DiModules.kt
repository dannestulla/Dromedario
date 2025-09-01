package br.gohan.dromedario.di

import br.gohan.dromedario.data.MobileRepository
import br.gohan.dromedario.presenter.MobileViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Android-specific module
val mobileModule = module {
    // Use CIO engine for Android
    single<HttpClient> {
        HttpClient(CIO) {
            install(WebSockets)
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
    
    // Android-specific dependencies
    single {
        MobileRepository(get(), ApiKeyProvider.mapsApiKey)
    }
    
    viewModel {
        MobileViewModel(get(), get())
    }
}