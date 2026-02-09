package br.gohan.dromedario

import java.io.File

private val envVars: Map<String, String> by lazy {
    // Check current directory, then parent (for when Gradle runs from server/)
    val possiblePaths = listOf(".env", "../.env")
    val envFile = possiblePaths.map { File(it) }.firstOrNull { it.exists() }

    if (envFile != null) {
        println("Loaded .env from: ${envFile.absolutePath}")
        envFile.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key.trim() to value.trim()
            }
    } else {
        println("Warning: .env not found in ${File(".").absolutePath}, using environment variables only")
        emptyMap()
    }
}

fun getSecret(key: String, default: String = ""): String {
    // First check environment variables (Docker/production), then .env file (local dev)
    return System.getenv(key)
        ?: envVars[key]
        ?: default
}
