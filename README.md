# Dromedario

A multi-stop route planning app that overcomes Google Maps' 9-waypoint limit. Plan routes on the web, export to any GPX-compatible navigation app.

![Web Dashboard](readme-screenshot-1.jpg)
![Mobile App](readme-screenshot-2.jpg)

## How It Works

1. **Plan on Web** — Open the web dashboard, search addresses or click the map to add stops. Drag to reorder.
2. **Sync to Mobile** — Open the export page on your phone to see the route in real-time via WebSocket.
3. **Auto-group** — Routes are split into groups of 9 (Google Maps limit). Navigate one group, then continue to the next.
4. **Export GPX** — Tap "Export GPX" on mobile to download and open directly in OsmAnd, Locus Map, Organic Maps, or any navigation app that supports GPX routes.

## Tech Stack

- **Kotlin Multiplatform** — Shared code between server, web, and Android
- **Compose Multiplatform** — UI for web (JS) and Android
- **Ktor** — Server framework with WebSocket support
- **MongoDB Atlas** — Cloud database for route persistence
- **Docker** — Containerized deployment

## Local Development

1. Create `.env` file in the project root:

```properties
MAPS_API_KEY=your_google_maps_api_key
GOOGLE_CLIENT_ID=your_google_oauth_client_id
GOOGLE_ROUTES_API_KEY=your_routes_api_key
JWT_SECRET=your_jwt_secret
APP_PASSWORD=your_app_password
MONGO_URI=mongodb+srv://user:pass@cluster.mongodb.net/route_planner
```

2. Run locally:

```bash
# Server
./gradlew server:run

# Web client (dev mode with hot reload)
./gradlew composeApp:jsBrowserDevelopmentRun

# Android app
./gradlew composeApp:installDebug
```

3. Open http://localhost:8080

## Docker Deployment

### Local Docker with ngrok (for mobile testing)

```bash
# Build web client and copy to server resources
./gradlew composeApp:jsBrowserDistribution
cp composeApp/build/dist/js/productionExecutable/* server/src/main/resources/web/

# Build and run with Docker
docker-compose build
docker-compose up -d

# Expose locally via ngrok (for testing on mobile)
ngrok http 8080
```

### Production (Render + MongoDB Atlas)

1. Create free MongoDB Atlas cluster and get connection string
2. Push to GitHub
3. Deploy to Render:
   - Connect your repository
   - Render auto-detects the Dockerfile
   - Set environment variables in Render dashboard:
     - `MONGO_URI`
     - `JWT_SECRET`
     - `MAPS_API_KEY`
     - `GOOGLE_CLIENT_ID`
     - `GOOGLE_ROUTES_API_KEY`
     - `APP_PASSWORD`

## Project Structure

```
├── composeApp/          # Kotlin Multiplatform UI (Android + Web JS)
│   ├── androidMain/     # Android-specific code
│   ├── jsMain/          # Web client (Compose for Web)
│   └── commonMain/      # Shared UI logic
├── server/              # Ktor server
│   └── resources/web/   # Static web files (production build)
├── shared/              # Shared business logic (server + clients)
├── Dockerfile           # Production container
└── docker-compose.yml   # Local Docker setup
```

## License

MIT
