# Dromedario - Work in Progress

A route planning app built with Kotlin Multiplatform. Android and web clients connect to a shared Ktor server via WebSockets for real-time route synchronization using Google Maps.

Goal is to be able to plan routes bigger than the google maps route stops limit
## Architecture

```
composeApp/     Compose Multiplatform client (Android + Kotlin/JS web)
  commonMain/   Shared client code
  androidMain/  Android app (Compose + Google Maps SDK)
  jsMain/       Web app (Compose HTML + Google Maps JS API)
server/         Ktor server (WebSockets, JWT auth, Routes API, MongoDB)
shared/         Code shared across all targets (data models, ViewModels)
docs/           Planning and design documents
```

## Tech Stack

- **Kotlin Multiplatform** with Compose Multiplatform
- **Ktor** — server + HTTP clients
- **WebSockets** — real-time sync between clients and server
- **Google Maps** — Android SDK + JavaScript API
- **Koin** — dependency injection
- **MongoDB** (KMongo) — persistence
- **JWT** — authentication

## Prerequisites

- JDK 11+
- Android SDK (for Android target)
- Google Maps API key

## Setup

1. Create `secrets.properties` in the project root:
   ```properties
   MAPS_API_KEY=your_google_maps_api_key
   APP_PASSWORD = any_password
   GOOGLE_ROUTES_API_KEY = your_google_routes_api
   JWT_SECRET = any_jwt_secret
   ```

2. Build and run:
   ```bash
   # Android
   ./gradlew composeApp:installDebug

   # Web (dev server)
   ./gradlew composeApp:jsBrowserDevelopmentRun

   # Server
   ./gradlew server:run
   ```

## License

TBD
