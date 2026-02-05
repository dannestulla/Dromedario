package br.gohan.dromedario.presenter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.dromedario.data.EventType
import br.gohan.dromedario.data.GroupStatus
import br.gohan.dromedario.data.MessageModel
import br.gohan.dromedario.data.MobileRepository
import br.gohan.dromedario.data.RouteGroup
import br.gohan.dromedario.data.TripSession
import br.gohan.dromedario.data.TripStatus
import br.gohan.dromedario.data.Waypoint
import br.gohan.dromedario.domain.geometricCenter
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.ktx.utils.toLatLngList
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MobileViewModel(
    private val mobileRepository: MobileRepository,
    private val clientSharedViewModel: ClientSharedViewModel
) : ViewModel() {
    private val _route = MutableStateFlow<List<LatLng>>(listOf())
    val route = _route.asStateFlow()

    private val _cameraPosition = MutableStateFlow<LatLng?>(null)
    val cameraPosition = _cameraPosition.asStateFlow()

    private val _tripState = MutableStateFlow<TripSession?>(null)
    val tripState = _tripState.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating = _isNavigating.asStateFlow()

    init {
        viewModelScope.launch {
            clientSharedViewModel.incomingFlow.collect { routeState ->
                val current = _tripState.value
                val tripSession = TripSession(
                    id = routeState.id,
                    waypoints = routeState.waypoints,
                    groups = current?.groups ?: emptyList(),
                    activeGroupIndex = current?.activeGroupIndex ?: 0,
                    status = current?.status ?: TripStatus.PLANNING,
                    updatedAt = routeState.updatedAt
                )
                _tripState.emit(tripSession)
            }
        }
    }

    fun getRoutePoints(origin: LatLng, destination: LatLng, waypoints: List<LatLng>) {
        viewModelScope.launch {
            val result = mobileRepository.getRoutePolyline(origin, destination, waypoints)
            if (result != null) {
                _route.emit(result.toLatLngList())
                _cameraPosition.emit(result.toLatLngList().geometricCenter())
            }
        }
    }

    fun updateTripState(tripSession: TripSession) {
        viewModelScope.launch {
            _tripState.emit(tripSession)
            Napier.d("MobileViewModel: Trip state updated - status: ${tripSession.status}, groups: ${tripSession.groups.size}")
        }
    }

    fun optimizeRoute() {
        viewModelScope.launch {
            Napier.d("MobileViewModel: Requesting route optimization")
            val message = MessageModel(event = EventType.OPTIMIZE_ROUTE)
            clientSharedViewModel.sendEvent(message)
        }
    }

    fun finalizeRoute() {
        viewModelScope.launch {
            Napier.d("MobileViewModel: Finalizing route")
            val message = MessageModel(event = EventType.FINALIZE_ROUTE)
            clientSharedViewModel.sendEvent(message)
        }
    }

    fun startNavigation(context: Context) {
        val trip = _tripState.value ?: run {
            Napier.e("MobileViewModel: Cannot start navigation - no trip state")
            return
        }

        val activeGroup = trip.groups.getOrNull(trip.activeGroupIndex) ?: run {
            Napier.e("MobileViewModel: Cannot start navigation - no active group")
            return
        }

        Napier.d("MobileViewModel: Starting navigation for group ${activeGroup.index}")
        exportGroupToGoogleMaps(context, trip, activeGroup)

        viewModelScope.launch {
            _isNavigating.emit(true)
        }
    }

    fun startNextGroup() {
        viewModelScope.launch {
            Napier.d("MobileViewModel: Starting next group")
            val message = MessageModel(event = EventType.GROUP_COMPLETED)
            clientSharedViewModel.sendEvent(message)
        }
    }

    fun cancelNavigation() {
        viewModelScope.launch {
            _isNavigating.emit(false)
        }
    }

    private fun exportGroupToGoogleMaps(context: Context, trip: TripSession, group: RouteGroup) {
        val groupWaypoints = trip.waypoints.subList(group.waypointStartIndex, group.waypointEndIndex)

        if (groupWaypoints.size < 2) {
            Napier.e("MobileViewModel: Cannot export group - need at least 2 waypoints")
            return
        }

        val uri = buildGoogleMapsUri(groupWaypoints)
        Napier.d("MobileViewModel: Opening Google Maps with URI: $uri")

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }

        // Check if Google Maps app is installed, fallback to browser
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // Fallback to browser
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    private fun buildGoogleMapsUri(waypoints: List<Waypoint>): Uri {
        val origin = waypoints.first()
        val destination = waypoints.last()
        val viaPoints = waypoints.drop(1).dropLast(1)

        val uriBuilder = StringBuilder("https://www.google.com/maps/dir/?api=1")
        uriBuilder.append("&origin=${origin.latitude},${origin.longitude}")
        uriBuilder.append("&destination=${destination.latitude},${destination.longitude}")

        if (viaPoints.isNotEmpty()) {
            val waypointsParam = viaPoints.joinToString("|") { "${it.latitude},${it.longitude}" }
            uriBuilder.append("&waypoints=$waypointsParam")
        }

        uriBuilder.append("&travelmode=driving")

        return Uri.parse(uriBuilder.toString())
    }

    fun getActiveGroupWaypoints(): List<Waypoint> {
        val trip = _tripState.value ?: return emptyList()
        val activeGroup = trip.groups.getOrNull(trip.activeGroupIndex) ?: return emptyList()
        return trip.waypoints.subList(activeGroup.waypointStartIndex, activeGroup.waypointEndIndex)
    }

    fun getGroupProgress(): Pair<Int, Int> {
        val trip = _tripState.value ?: return Pair(0, 0)
        return Pair(trip.activeGroupIndex + 1, trip.groups.size)
    }

    fun getCompletedGroupsCount(): Int {
        val trip = _tripState.value ?: return 0
        return trip.groups.count { it.status == GroupStatus.COMPLETED }
    }
}