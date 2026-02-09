# Simple server-only Dockerfile
# Pre-requisite: Run `gradlew composeApp:jsBrowserDistribution` locally
# and copy output to server/src/main/resources/web/

FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts gradle.properties build.gradle.kts ./

# Copy only server and shared modules
COPY server ./server
COPY shared ./shared

# Create minimal settings that excludes composeApp
RUN echo 'rootProject.name = "Dromedario"' > settings.gradle.kts && \
    echo 'enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")' >> settings.gradle.kts && \
    echo 'pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }' >> settings.gradle.kts && \
    echo 'dependencyResolutionManagement { repositories { google(); mavenCentral() } }' >> settings.gradle.kts && \
    echo 'include(":server")' >> settings.gradle.kts && \
    echo 'include(":shared")' >> settings.gradle.kts

# Create minimal root build.gradle.kts
RUN echo 'plugins { alias(libs.plugins.kotlinMultiplatform) apply false; alias(libs.plugins.kotlinJvm) apply false }' > build.gradle.kts

# Remove files that use Android-specific dependencies (ViewModel)
RUN rm -f shared/src/commonMain/kotlin/br/gohan/dromedario/presenter/ClientSharedViewModel.kt && \
    rm -f shared/src/commonMain/kotlin/br/gohan/dromedario/DIModules.kt && \
    rm -rf shared/src/androidMain

# Create JVM-only shared build.gradle.kts (skip Android/JS targets)
RUN cat > shared/build.gradle.kts << 'GRADLE_EOF'
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    kotlin("plugin.serialization") version "2.2.10"
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.napier)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
GRADLE_EOF

# Build server JAR
RUN ./gradlew server:shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
