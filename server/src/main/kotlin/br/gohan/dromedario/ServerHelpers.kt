package br.gohan.dromedario

import java.io.File

private val envVars: Map<String, String> by lazy {
    val envFile = File(".env")
    if (envFile.exists()) {
        envFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key.trim() to value.trim()
            }
    } else {
        println("Warning: .env not found, using environment variables only")
        emptyMap()
    }
}

fun getSecret(key: String, default: String = ""): String {
    // First check environment variables (Docker/production), then .env file (local dev)
    return System.getenv(key)
        ?: envVars[key]
        ?: default
}
