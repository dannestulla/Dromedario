package br.gohan.dromedario.auth

import androidx.compose.runtime.*
import br.gohan.dromedario.GOOGLE_CLIENT_ID
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.koin.core.context.GlobalContext

@Serializable
data class GoogleAuthRequest(val credential: String)

@Serializable
data class LoginResponse(val token: String)

private val json = Json { ignoreUnknownKeys = true }

// Google Sign-In login screen. Renders GSI button, sends credential to /api/auth/google, returns JWT.
@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var gsiReady by remember { mutableStateOf(false) }

    val httpClient: HttpClient = remember { GlobalContext.get().get() }
    val scope = rememberCoroutineScope()

    // Poll for GSI library readiness, then initialize
    LaunchedEffect(Unit) {
        // Wait for GSI library to load (it's async)
        var attempts = 0
        while (!isGsiLoaded() && attempts < 50) {
            kotlinx.coroutines.delay(1000)
            attempts++
        }

        if (isGsiLoaded()) {
            initializeGoogleSignIn(GOOGLE_CLIENT_ID) { credential ->
                isLoading = true
                error = null
                scope.launch {
                    try {
                        val response = httpClient.post("/api/auth/google") {
                            contentType(ContentType.Application.Json)
                            setBody(json.encodeToString(GoogleAuthRequest.serializer(), GoogleAuthRequest(credential)))
                        }
                        if (response.status == HttpStatusCode.OK) {
                            val loginResponse = json.decodeFromString(LoginResponse.serializer(), response.bodyAsText())
                            Napier.d("Google login successful, token received")
                            onLoginSuccess(loginResponse.token)
                        } else {
                            error = "Authentication failed. Please try again."
                            Napier.w("Google login failed: ${response.status}")
                        }
                    } catch (e: Exception) {
                        error = "Connection error. Please check if the server is running."
                        Napier.e("Google login error: ${e.message}")
                    } finally {
                        isLoading = false
                    }
                }
            }
            gsiReady = true
        } else {
            Napier.e("Google Identity Services library failed to load")
            error = "Google Sign-In failed to load. Please refresh the page."
        }
    }

    Div({
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            alignItems(AlignItems.Center)
            height(100.vh)
            width(100.vw)
            backgroundColor(Color("#f5f5f5"))
            position(Position.Fixed)
            property("top", "0")
            property("left", "0")
        }
    }) {
        Div({
            style {
                backgroundColor(Color.white)
                padding(32.px)
                borderRadius(8.px)
                property("box-shadow", "0 2px 10px rgba(0,0,0,0.1)")
                width(320.px)
                textAlign("center")
            }
        }) {
            H1({
                style {
                    marginBottom(8.px)
                    color(Color("#333"))
                }
            }) {
                Text("Dromedario")
            }

            P({
                style {
                    marginBottom(24.px)
                    color(Color("#666"))
                }
            }) {
                Text("Sign in to continue")
            }

            // Error Message
            error?.let { errorMessage ->
                Div({
                    style {
                        color(Color("#dc3545"))
                        marginBottom(16.px)
                        padding(12.px)
                        backgroundColor(Color("#f8d7da"))
                        borderRadius(4.px)
                    }
                }) {
                    Text(errorMessage)
                }
            }

            if (isLoading) {
                Div({
                    style {
                        color(Color("#666"))
                        padding(12.px)
                    }
                }) {
                    Text("Signing in...")
                }
            }

            // Google Sign-In button container
            if (gsiReady && !isLoading) {
                Div({
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.Center)
                    }
                    ref { element ->
                        renderGoogleButton(element)
                        onDispose { }
                    }
                })
            }
        }
    }
}
