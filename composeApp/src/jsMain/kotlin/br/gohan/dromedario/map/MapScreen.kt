package br.gohan.dromedario.map

import androidx.compose.runtime.*
import br.gohan.dromedario.map.components.ActionButtons
import br.gohan.dromedario.map.components.MyLocationButton
import br.gohan.dromedario.map.components.NavigationStatus
import br.gohan.dromedario.map.components.TripStatus
import br.gohan.dromedario.map.components.WaypointList
import br.gohan.dromedario.presenter.ClientSharedViewModel
import io.github.aakira.napier.Napier
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.koin.compose.koinInject

// Main web app screen. Manages map lifecycle, WebSocket connection, and autocomplete wiring.
@Composable
fun WebApp(token: String, viewModel: ClientSharedViewModel = koinInject()) {
    val mapsLoader: GoogleMapsLoader = koinInject()
    val routeState by viewModel.incomingFlow.collectAsState()

    var mapController by remember { mutableStateOf<MapController?>(null) }
    var searchInputRef by remember { mutableStateOf<dynamic>(null) }
    var autocompleteAttached by remember { mutableStateOf(false) }
    val latestWaypoints = rememberUpdatedState(routeState.waypoints)

    // Start WebSocket connection with token
    LaunchedEffect(token) {
        val currentHost = window.location.host
        val wsProtocol = if (window.location.protocol == "https:") "wss" else "ws"
        val wsUrl = "$wsProtocol://$currentHost/ws?token=$token"
        viewModel.startWebSocket(wsUrl)
    }

    // Load Google Maps API and initialize map
    LaunchedEffect(Unit) {
        try {
            mapsLoader.ensureLoaded()
            mapController = MapController("map-container")
        } catch (e: Exception) {
            Napier.e("Failed to initialize map: ${e.message}")
        }
    }

    // Update map when waypoints change
    LaunchedEffect(routeState.waypoints) {
        mapController?.updateWaypoints(routeState.waypoints)
    }

    // Attach Places Autocomplete when search input is ready
    LaunchedEffect(searchInputRef) {
        if (autocompleteAttached) return@LaunchedEffect
        val input = searchInputRef ?: return@LaunchedEffect

        try {
            mapsLoader.ensureLoaded()
            attachPlacesAutocomplete(input) { address, lat, lng ->
                val nextIndex = (latestWaypoints.value.maxOfOrNull { it.index } ?: -1) + 1
                viewModel.sendMessage(address, nextIndex, lat, lng)
            }
            autocompleteAttached = true
        } catch (e: Exception) {
            Napier.e("Failed to attach autocomplete: ${e.message}")
        }
    }

    // UI
    Div({
        style {
            padding(16.px)
            maxWidth(400.px)
            height(100.vh)
            overflowY("auto")
            backgroundColor(Color.white)
        }
    }) {
        H1({
            style {
                marginBottom(16.px)
                color(Color("#333"))
            }
        }) {
            Text("Dromedario Route Planner")
        }

        TripStatus(routeState.waypoints.size)
        WaypointList(routeState.waypoints, onRemove = { index ->
            viewModel.deleteMessage(index)
        })

        Div({
            style { marginBottom(16.px) }
        }) {
            H3 { Text("Add Waypoint") }
            Div({
                style { width(100.percent) }
                ref { element ->
                    searchInputRef = element
                    onDispose { searchInputRef = null }
                }
            })
        }

        ActionButtons(routeState.waypoints)
        MyLocationButton(mapController)
        NavigationStatus()
    }
}
