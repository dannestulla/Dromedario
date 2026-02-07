package br.gohan.dromedario.auth

import org.w3c.dom.Element

// Kotlin/JS interop bindings for Google Identity Services (GSI) library.
// The GSI library is loaded via <script src="https://accounts.google.com/gsi/client">.

fun initializeGoogleSignIn(clientId: String, callback: (credential: String) -> Unit) {
    val google = js("google")
    val config = js("{}")
    config.client_id = clientId
    config.callback = { response: dynamic ->
        val credential = response.credential as? String
        if (credential != null) {
            callback(credential)
        }
    }
    google.accounts.id.initialize(config)
}

fun renderGoogleButton(container: Element) {
    val google = js("google")
    val options = js("{}")
    options.theme = "outline"
    options.size = "large"
    options.width = "320"
    google.accounts.id.renderButton(container, options)
}

fun isGsiLoaded(): Boolean {
    val idDefined = js("typeof google !== 'undefined' && google.accounts && google.accounts.id ? true : false") as Boolean
    return idDefined
}
