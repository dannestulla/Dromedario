# Dromedario

A multi-stop route planning app that overcomes Google Maps' 9-waypoint limit. Plan routes on the web, export to any GPX-compatible navigation app.

## Features

- Unlimited waypoints with real-time sync
- Google Maps integration with Places Autocomplete
- GPX export for OsmAnd, Locus Map, Organic Maps
- Route optimization via Google Routes API
- Google Sign-In authentication

## Tech Stack

Kotlin Multiplatform, Compose, Ktor, MongoDB, WebSockets, Koin

## Setup

1. Create `secrets.properties`:

```properties
MAPS_API_KEY=your_google_maps_api_key
GOOGLE_CLIENT_ID=your_google_oauth_client_id
GOOGLE_ROUTES_API_KEY=your_routes_api_key
JWT_SECRET=your_jwt_secret
```

2. Run:

```bash
# Server
./gradlew server:run

# Web (dev)
./gradlew composeApp:jsBrowserDevelopmentRun

# Android
./gradlew composeApp:installDebug
```

3. Open http://localhost:8081

## Deployment (Render + MongoDB Atlas)

1. Create free MongoDB Atlas cluster
2. Deploy to Render with:
   - **Build**: `./gradlew server:shadowJar`
   - **Start**: `java -jar server/build/libs/server-all.jar`
3. Set environment variables: `MONGODB_URI`, `JWT_SECRET`, `MAPS_API_KEY`, `GOOGLE_CLIENT_ID`

## License

MIT
