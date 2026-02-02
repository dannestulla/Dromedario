package br.gohan.dromedario.auth

import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * Web-specific AuthRepository implementation using browser localStorage.
 */
class AuthRepository {
    companion object {
        private const val TOKEN_KEY = "dromedario_token"
    }

    /**
     * Saves the JWT token to localStorage.
     */
    fun saveToken(token: String) {
        localStorage[TOKEN_KEY] = token
    }

    /**
     * Retrieves the JWT token from localStorage.
     * Returns null if no token is stored.
     */
    fun getToken(): String? {
        return localStorage[TOKEN_KEY]
    }

    /**
     * Clears the stored JWT token.
     */
    fun clearToken() {
        localStorage.removeItem(TOKEN_KEY)
    }

    /**
     * Checks if a token is currently stored.
     */
    fun hasToken(): Boolean {
        return getToken() != null
    }
}
