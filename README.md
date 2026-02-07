# Dromedario

A multi-stop route planning app that overcomes Google Maps' 9-waypoint limit. Plan routes on the web, export to any GPX-compatible navigation app.

![Web Dashboard](readme-screenshot-1.jpg)
![Mobile App](readme-screenshot-2.jpg)

## How It Works

1. **Plan on Web** — Open the web dashboard, search addresses or click the map to add stops. Drag to reorder.
2. **Sync to Mobile** — The Android app receives waypoints in real-time via WebSocket.
3. **Auto-group** — Routes are split into groups of 9 (Google Maps limit). Navigate one group, then continue to the next.
4. **Export** — Download GPX file to use with OsmAnd, Locus Map, or any navigation app that supports multi-stop routes.

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
