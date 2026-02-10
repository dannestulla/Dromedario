package br.gohan.dromedario

import br.gohan.dromedario.data.RouteStateModel
import br.gohan.dromedario.data.TripSession
import br.gohan.dromedario.data.TripStatus
import br.gohan.dromedario.data.Waypoint
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.litote.kmongo.coroutine.CoroutineClient
import org.litote.kmongo.coroutine.CoroutineCollection
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.eq
import org.litote.kmongo.reactivestreams.KMongo

class DatabaseManager {
    private lateinit var mongoClient: CoroutineClient
    private lateinit var sessionCollection: CoroutineCollection<RouteStateModel>
    private lateinit var tripSessionCollection: CoroutineCollection<TripSession>
    private val _databaseEvents = MutableSharedFlow<RouteStateModel>()
    val databaseEvents = _databaseEvents.asSharedFlow()

    companion object {
        private const val SESSION_ID = "session" // Fixed ID for the unique session
    }

    suspend fun setup() {
        val mongoUri = getSecret("MONGO_URI", "mongodb://localhost:27017")
        println("Connecting to MongoDB... (URI starts with: ${mongoUri.take(30)}...)")
        mongoClient = KMongo.createClient(mongoUri).coroutine
        val database = mongoClient.getDatabase("route_planner")
        sessionCollection = database.getCollection<RouteStateModel>("sessions")
        tripSessionCollection = database.getCollection<TripSession>("trip_sessions")
        sessionCollection.createIndex(Indexes.ascending("updatedAt"))
        tripSessionCollection.createIndex(Indexes.ascending("updatedAt"))

        // Ensures initial session exists
        val existing = sessionCollection.findOne(RouteStateModel::id eq SESSION_ID)
        if (existing == null) {
            val initialState = RouteStateModel(id = SESSION_ID)
            sessionCollection.insertOne(initialState)
            println("Initial session created")
        }

        println("MongoDB connected (standalone mode)")
    }

    suspend fun collect(action: suspend (RouteStateModel) -> Unit) {
        databaseEvents.collect(action)
    }

    fun close() {
        mongoClient.close()
    }

    suspend fun addWaypoint(waypoint: Waypoint) {
        val currentState = getCurrentState()
        val updatedWaypoints = currentState.waypoints + waypoint.copy(index = currentState.waypoints.size)
        val updatedState = currentState.copy(
            waypoints = updatedWaypoints,
            updatedAt = System.currentTimeMillis()
        )

        sessionCollection.updateOne(
            RouteStateModel::id eq SESSION_ID,
            Updates.combine(
                Updates.set(RouteStateModel::waypoints.name, updatedWaypoints),
                Updates.set(RouteStateModel::updatedAt.name, updatedState.updatedAt)
            )
        )

        println("Waypoint added. Total: ${updatedWaypoints.size}")
        _databaseEvents.emit(updatedState)
    }

    suspend fun removeWaypoint(waypointIndex: Int) {
        val currentState = getCurrentState()

        if (waypointIndex < 0 || waypointIndex >= currentState.waypoints.size) {
            println("Invalid index: $waypointIndex")
            return
        }

        val updatedWaypoints = currentState.waypoints
            .filterIndexed { index, _ -> index != waypointIndex } // Removes the waypoint
            .mapIndexed { newIndex, waypoint -> waypoint.copy(index = newIndex) } // Reindex all waypoints

        val updatedState = currentState.copy(
            waypoints = updatedWaypoints,
            updatedAt = System.currentTimeMillis()
        )

        sessionCollection.updateOne(
            RouteStateModel::id eq SESSION_ID,
            Updates.combine(
                Updates.set(RouteStateModel::waypoints.name, updatedWaypoints),
                Updates.set(RouteStateModel::updatedAt.name, updatedState.updatedAt)
            )
        )

        println("Waypoint removed. Total: ${updatedWaypoints.size}")
        _databaseEvents.emit(updatedState)
    }

    suspend fun getCurrentState(): RouteStateModel {
        return sessionCollection.findOne(RouteStateModel::id eq SESSION_ID)
            ?: RouteStateModel(id = SESSION_ID) // Fallback if doesn't exist
    }

    suspend fun updateWaypoints(waypoints: List<Waypoint>) {
        val reindexedWaypoints = waypoints.mapIndexed { index, waypoint ->
            waypoint.copy(index = index)
        }
        val updatedState = getCurrentState().copy(
            waypoints = reindexedWaypoints,
            updatedAt = System.currentTimeMillis()
        )

        sessionCollection.updateOne(
            RouteStateModel::id eq SESSION_ID,
            Updates.combine(
                Updates.set(RouteStateModel::waypoints.name, reindexedWaypoints),
                Updates.set(RouteStateModel::updatedAt.name, updatedState.updatedAt)
            )
        )

        println("Waypoints updated. Total: ${reindexedWaypoints.size}")
        _databaseEvents.emit(updatedState)
    }

    suspend fun updateWaypointsWithPolyline(waypoints: List<Waypoint>, encodedPolyline: String?) {
        val reindexedWaypoints = waypoints.mapIndexed { index, waypoint ->
            waypoint.copy(index = index)
        }
        val updatedState = getCurrentState().copy(
            waypoints = reindexedWaypoints,
            encodedPolyline = encodedPolyline,
            updatedAt = System.currentTimeMillis()
        )

        sessionCollection.updateOne(
            RouteStateModel::id eq SESSION_ID,
            Updates.combine(
                Updates.set(RouteStateModel::waypoints.name, reindexedWaypoints),
                Updates.set(RouteStateModel::encodedPolyline.name, encodedPolyline),
                Updates.set(RouteStateModel::updatedAt.name, updatedState.updatedAt)
            )
        )

        println("Waypoints updated with polyline. Total: ${reindexedWaypoints.size}")
        _databaseEvents.emit(updatedState)
    }

    suspend fun updateEncodedPolyline(encodedPolyline: String?) {
        val updatedState = getCurrentState().copy(
            encodedPolyline = encodedPolyline,
            updatedAt = System.currentTimeMillis()
        )

        sessionCollection.updateOne(
            RouteStateModel::id eq SESSION_ID,
            Updates.combine(
                Updates.set(RouteStateModel::encodedPolyline.name, encodedPolyline),
                Updates.set(RouteStateModel::updatedAt.name, updatedState.updatedAt)
            )
        )

        println("Encoded polyline updated")
        _databaseEvents.emit(updatedState)
    }

    suspend fun clearAllWaypoints() {
        val clearedState = RouteStateModel(
            id = SESSION_ID,
            waypoints = emptyList(),
            updatedAt = System.currentTimeMillis()
        )

        sessionCollection.updateOne(
            RouteStateModel::id eq SESSION_ID,
            Updates.combine(
                Updates.set(RouteStateModel::waypoints.name, emptyList<Waypoint>()),
                Updates.set(RouteStateModel::updatedAt.name, clearedState.updatedAt)
            )
        )

        println("All waypoints cleared")
        _databaseEvents.emit(clearedState)
    }

    suspend fun getAllWaypoints(): List<RouteStateModel> {
        val currentState = getCurrentState()
        return listOf(currentState)
    }

    // TripSession methods for the new navigation flow

    suspend fun getCurrentTripSession(): TripSession? {
        return tripSessionCollection.findOne(TripSession::id eq SESSION_ID)
    }

    suspend fun updateTripSession(trip: TripSession) {
        val existing = tripSessionCollection.findOne(TripSession::id eq trip.id)
        if (existing == null) {
            tripSessionCollection.insertOne(trip)
            println("Trip session created: ${trip.id}")
        } else {
            tripSessionCollection.updateOne(
                TripSession::id eq trip.id,
                Updates.combine(
                    Updates.set(TripSession::waypoints.name, trip.waypoints),
                    Updates.set(TripSession::groups.name, trip.groups),
                    Updates.set(TripSession::activeGroupIndex.name, trip.activeGroupIndex),
                    Updates.set(TripSession::status.name, trip.status),
                    Updates.set(TripSession::updatedAt.name, trip.updatedAt)
                )
            )
            println("Trip session updated: ${trip.id}")
        }
    }

}
