package br.gohan.dromedario

import androidx.compose.runtime.*
import br.gohan.dromedario.auth.AuthRepository
import br.gohan.dromedario.auth.LoginScreen
import br.gohan.dromedario.map.WebApp
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.jetbrains.compose.web.renderComposable
import org.koin.core.context.startKoin

// App entry point. Initializes Koin DI, checks auth token, and renders login or map screen.
fun main() {
    Napier.base(DebugAntilog())

    // Initialize Koin
    startKoin {
        modules(webModule, sharedModule)
    }

    val authRepository = AuthRepository()
    val token = authRepository.getToken()

    renderComposable(rootElementId = "compose-container") {
        var isAuthenticated by remember { mutableStateOf(token != null) }
        var currentToken by remember { mutableStateOf(token) }

        if (isAuthenticated && currentToken != null) {
            WebApp(currentToken!!)
        } else {
            LoginScreen { newToken ->
                authRepository.saveToken(newToken)
                currentToken = newToken
                isAuthenticated = true
            }
        }
    }
}
