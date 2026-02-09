plugins {
    alias(libs.plugins.kotlinJvm)
    kotlin("plugin.serialization") version "2.2.10"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.serialization.kotlinx.json)
}
