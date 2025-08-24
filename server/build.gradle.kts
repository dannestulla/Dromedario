plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    application
    kotlin("plugin.serialization") version "2.2.10"
}

group = "br.gohan.dromedario"
version = "1.0.0"
application {
    mainClass.set("br.gohan.dromedario.ApplicationKt")
    
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kmongo.coroutine)
    implementation(libs.napier)

// se precisar de client no server
    testImplementation(libs.kotlin.testJunit)
}