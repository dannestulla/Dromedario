package br.gohan.dromedario.auth

import androidx.compose.runtime.*
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.koin.core.context.GlobalContext

@Serializable
data class LoginRequest(val password: String)

@Serializable
data class LoginResponse(val token: String)

private val json = Json { ignoreUnknownKeys = true }

// Password login screen. Posts to /api/login and returns a JWT token on success.
@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val httpClient: HttpClient = remember { GlobalContext.get().get() }
    val scope = rememberCoroutineScope()

    Div({
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.Center)
            alignItems(AlignItems.Center)
            height(100.vh)
            backgroundColor(Color("#f5f5f5"))
        }
    }) {
        Div({
            style {
                backgroundColor(Color.white)
                padding(32.px)
                borderRadius(8.px)
                property("box-shadow", "0 2px 10px rgba(0,0,0,0.1)")
                width(320.px)
            }
        }) {
            H1({
                style {
                    textAlign("center")
                    marginBottom(24.px)
                    color(Color("#333"))
                }
            }) {
                Text("Dromedario")
            }

            P({
                style {
                    textAlign("center")
                    marginBottom(24.px)
                    color(Color("#666"))
                }
            }) {
                Text("Enter your password to continue")
            }

            // Password Input
            Input(InputType.Password) {
                value(password)
                onInput { event ->
                    password = event.value
                    error = null
                }
                placeholder("Password")
                style {
                    width(100.percent)
                    padding(12.px)
                    marginBottom(16.px)
                    border(1.px, LineStyle.Solid, Color("#ddd"))
                    borderRadius(4.px)
                    fontSize(16.px)
                    property("box-sizing", "border-box")
                }
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
                        textAlign("center")
                    }
                }) {
                    Text(errorMessage)
                }
            }

            // Login Button
            Button({
                onClick {
                    if (password.isBlank()) {
                        error = "Please enter a password"
                        return@onClick
                    }

                    isLoading = true
                    error = null

                    scope.launch {
                        try {
                            val response = httpClient.post("/api/login") {
                                contentType(ContentType.Application.Json)
                                setBody(json.encodeToString(LoginRequest.serializer(), LoginRequest(password)))
                            }

                            if (response.status == HttpStatusCode.OK) {
                                val loginResponse = json.decodeFromString(LoginResponse.serializer(), response.bodyAsText())
                                Napier.d("Login successful, token received")
                                onLoginSuccess(loginResponse.token)
                            } else {
                                error = "Invalid password"
                                Napier.w("Login failed: ${response.status}")
                            }
                        } catch (e: Exception) {
                            error = "Connection error. Please check if the server is running."
                            Napier.e("Login error: ${e.message}")
                        } finally {
                            isLoading = false
                        }
                    }
                }
                if (isLoading) {
                    disabled()
                }
                style {
                    width(100.percent)
                    padding(12.px)
                    backgroundColor(if (isLoading) Color("#6c757d") else Color("#007bff"))
                    color(Color.white)
                    border(0.px, LineStyle.None, Color.transparent)
                    borderRadius(4.px)
                    fontSize(16.px)
                    cursor(if (isLoading) "not-allowed" else "pointer")
                }
            }) {
                Text(if (isLoading) "Logging in..." else "Login")
            }
        }
    }
}
