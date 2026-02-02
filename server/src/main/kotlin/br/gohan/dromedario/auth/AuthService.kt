package br.gohan.dromedario.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class AuthService(private val jwtSecret: String, private val appPassword: String) {

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
}
