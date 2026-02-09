# Dromedario Server Dockerfile
#
# Pre-requisite: Build web client locally first:
#   ./gradlew composeApp:jsBrowserDistribution
#   cp composeApp/build/dist/js/productionExecutable/* server/src/main/resources/web/

FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# Copy Gradle files
COPY gradlew gradlew.bat ./
COPY gradle ./gradle
COPY settings.gradle.kts gradle.properties build.gradle.kts ./

# Copy only server and shared-server modules (no Android dependencies)
COPY server ./server
COPY shared-server ./shared-server

# Create minimal settings for server-only build
RUN echo 'rootProject.name = "Dromedario"' > settings.gradle.kts && \
    echo 'enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")' >> settings.gradle.kts && \
    echo 'pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }' >> settings.gradle.kts && \
    echo 'dependencyResolutionManagement { repositories { google(); mavenCentral() } }' >> settings.gradle.kts && \
    echo 'include(":server", ":shared-server")' >> settings.gradle.kts

RUN echo 'plugins { alias(libs.plugins.kotlinJvm) apply false }' > build.gradle.kts

# Build server JAR
RUN chmod +x gradlew && ./gradlew server:shadowJar --no-daemon

# Runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
