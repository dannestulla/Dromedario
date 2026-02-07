package br.gohan.dromedario.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val password: String)

@Serializable
data class LoginResponse(val token: String)

@Serializable
data class GoogleLoginRequest(val credential: String)

@Serializable
data class ErrorResponse(val error: String)
