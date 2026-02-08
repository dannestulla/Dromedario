import java.util.Properties

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization") version "2.2.10"
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    js(IR) {
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(projects.shared)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.napier)
            implementation(libs.koin.core)
            implementation(libs.androidx.lifecycle.viewmodel)
        }

        androidMain.dependencies {
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.preview)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.koin.compose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.ktor.client.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.ktor.client.cio)
            implementation(libs.maps.compose)
            implementation(libs.maps.compose.utils)
            implementation(libs.maps.compose.widgets)
            implementation(libs.play.services.location)
            implementation(libs.koin.android)
            implementation(libs.koin.compose.viewmodel)
        }

        jsMain {
            kotlin.srcDir(layout.buildDirectory.dir("generated/jsSecrets"))
            dependencies {
                implementation(compose.html.core)
                implementation(libs.ktor.client.js)
                implementation(libs.koin.compose)
                implementation(npm("@googlemaps/js-api-loader", "1.16.8"))
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "br.gohan.dromedario"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            manifestPlaceholders["usesCleartextTraffic"] = "false"
        }
    }

    defaultConfig {
        applicationId = "br.gohan.dromedario"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    secrets {
        propertiesFileName = ".env"
    }
}

// Generate JsSecrets.kt from .env for the JS target
val generateJsSecrets by tasks.registering {
    val secretsFile = rootProject.file(".env")
    val outputDir = layout.buildDirectory.dir("generated/jsSecrets/br/gohan/dromedario")
    inputs.file(secretsFile).optional()
    outputs.dir(outputDir)
    doLast {
        val props = Properties()
        if (secretsFile.exists()) {
            secretsFile.inputStream().use { props.load(it) }
        }
        val mapsKey = props.getProperty("MAPS_API_KEY", "")
        val googleClientId = props.getProperty("GOOGLE_CLIENT_ID", "")?.trim() ?: ""
        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("JsSecrets.kt").writeText(
            """
            |package br.gohan.dromedario
            |
            |const val MAPS_API_KEY = "$mapsKey"
            |const val GOOGLE_CLIENT_ID = "$googleClientId"
            """.trimMargin()
        )
    }
}

tasks.named("compileKotlinJs") {
    dependsOn(generateJsSecrets)
}

dependencies {
    debugImplementation(compose.uiTooling)
}


