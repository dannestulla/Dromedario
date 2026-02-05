package br.gohan.dromedario

import java.io.File
import java.util.Properties

// Secrets loaded from secrets.properties
lateinit var secrets: Properties

fun loadSecrets(): Properties {
    val props = Properties()
    // Try project root first, then current directory
    val locations = listOf(
        File("../secrets.properties"),  // When running from server/
        File("secrets.properties"),      // When running from project root
        File("../../secrets.properties") // Fallback
    )

    for (file in locations) {
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
            println("Loaded secrets from: ${file.absolutePath}")
            break
        }
    }

    if (props.isEmpty) {
        println("Warning: secrets.properties not found, using application.conf defaults")
    }

    return props
}

fun getSecret(key: String, default: String = ""): String {
    return secrets.getProperty(key)?.trim() ?: default
}
