package br.gohan.dromedario

import androidx.compose.runtime.*
import br.gohan.dromedario.auth.AuthRepository
import br.gohan.dromedario.auth.LoginScreen
import br.gohan.dromedario.export.ExportApp
import br.gohan.dromedario.map.WebApp
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.browser.window
import org.jetbrains.compose.web.renderComposable
import org.koin.core.context.startKoin

// App entry point. Initializes Koin DI, routes by URL path, and renders the appropriate screen.
fun main() {
    Napier.base(DebugAntilog())

    startKoin {
        modules(webModule, sharedModule)
    }

    val isExportPage = window.location.pathname.startsWith("/export")

    renderComposable(rootElementId = "compose-container") {
        val authRepository = remember { AuthRepository() }
        val token = remember { authRepository.getToken() }
        var isAuthenticated by remember { mutableStateOf(token != null) }
        var currentToken by remember { mutableStateOf(token) }

        val onLogout: () -> Unit = {
            authRepository.clearToken()
            currentToken = null
            isAuthenticated = false
        }

        key(isAuthenticated) {
            if (isAuthenticated && currentToken != null) {
                if (isExportPage) {
                    ExportApp(token = currentToken!!, onLogout = onLogout)
                } else {
                    WebApp(token = currentToken!!, onLogout = onLogout)
                }
            } else {
                LoginScreen { newToken ->
                    authRepository.saveToken(newToken)
                    currentToken = newToken
                    isAuthenticated = true
                }
            }
        }
    }
}
