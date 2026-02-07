package br.gohan.dromedario.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Date

class AuthService(
    private val jwtSecret: String,
    private val appPassword: String,
    private val googleClientId: String = ""
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun validatePassword(password: String): Boolean = password == appPassword

    fun generateToken(): String = JWT.create()
        .withClaim("app", "dromedario")
        .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)) // 30 days
        .sign(Algorithm.HMAC256(jwtSecret))

    fun validateToken(token: String?): Boolean {
        if (token == null) return false
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(jwtSecret)).build()
            val decoded = verifier.verify(token)
            decoded.getClaim("app").asString() == "dromedario"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun verifyGoogleToken(httpClient: HttpClient, idToken: String): Boolean {
        return try {
            val response = httpClient.get("https://oauth2.googleapis.com/tokeninfo") {
                parameter("id_token", idToken)
            }
            if (response.status.value != 200) return false

            val body = json.parseToJsonElement(response.bodyAsText()).jsonObject
            val aud = body["aud"]?.jsonPrimitive?.content ?: return false
            aud == googleClientId
        } catch (e: Exception) {
            false
        }
    }
}
