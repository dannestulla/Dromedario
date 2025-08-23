package br.gohan.dromedario

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
    private lateinit var sessionCollection: CoroutineCollection<RouteState>
    private val _databaseEvents = MutableSharedFlow<RouteState>()
    val databaseEvents = _databaseEvents.asSharedFlow()

    suspend fun setup() {
        mongoClient = KMongo.createClient("mongodb://localhost:27017").coroutine
        val database = mongoClient.getDatabase("route_planner")
        sessionCollection = database.getCollection<RouteState>("state1")
        sessionCollection.createIndex(Indexes.ascending("updatedAt"))
        println("✅ MongoDB conectado (standalone mode)")
    }

    suspend fun collect(action: suspend (RouteState) -> Unit) {
        databaseEvents.collect(action)
    }

    fun close() {
        mongoClient.close()
    }

    suspend fun addWaypoint(routeState: RouteState) {
        // Se é um novo estado, insere
        val existing = sessionCollection.findOne(RouteState::id eq routeState.id)
        if (existing == null) {
            sessionCollection.insertOne(routeState)
            println("✅ Novo RouteState inserido: ${routeState.id}")
        } else {
            // Se já existe, atualiza
            val updatedState = existing.copy(
                waypoints = existing.waypoints + routeState.waypoints,
                updatedAt = System.currentTimeMillis()
            )
            sessionCollection.updateOne(
                RouteState::id eq routeState.id,
                Updates.combine(
                    Updates.set(RouteState::waypoints.name, updatedState.waypoints),
                    Updates.set(RouteState::updatedAt.name, updatedState.updatedAt)
                )
            )
            println("✅ RouteState atualizado: ${routeState.id}")
            // Emite o estado atualizado manualmente
            _databaseEvents.emit(updatedState)
            return
        }
        
        // Emite o novo estado manualmente
        _databaseEvents.emit(routeState)
    }

    suspend fun removeWaypoint(routeId: String, waypointIndex: Int) {
        val route = sessionCollection.findOne(RouteState::id eq routeId)
        route?.let {
            val updatedWaypoints = it.waypoints.filterIndexed { index, _ -> index != waypointIndex }
            val updatedState = it.copy(
                waypoints = updatedWaypoints,
                updatedAt = System.currentTimeMillis()
            )
            
            sessionCollection.updateOne(
                RouteState::id eq routeId,
                Updates.combine(
                    Updates.set(RouteState::waypoints.name, updatedWaypoints),
                    Updates.set(RouteState::updatedAt.name, updatedState.updatedAt)
                )
            )
            
            println("✅ Waypoint removido do route $routeId, index $waypointIndex")
            // Emite o estado atualizado manualmente
            _databaseEvents.emit(updatedState)
        }
    }

    suspend fun getAllRoutes(): List<RouteState> {
        return sessionCollection.find().toList()
    }
}