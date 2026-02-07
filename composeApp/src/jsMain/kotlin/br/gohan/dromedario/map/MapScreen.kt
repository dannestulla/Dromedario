package br.gohan.dromedario.map

import androidx.compose.runtime.*
import br.gohan.dromedario.presenter.ClientSharedViewModel
import io.github.aakira.napier.Napier
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.koin.compose.koinInject

// Main web app screen. Manages map lifecycle, WebSocket connection, and autocomplete wiring.
@Composable
fun WebApp(token: String, onLogout: () -> Unit, viewModel: ClientSharedViewModel = koinInject()) {
    val mapsLoader: MapsLoader = koinInject()
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
            val controller = MapController("map-container")
            controller.onWaypointDragged = { index, address, lat, lng ->
                viewModel.updateWaypoint(index, address, lat, lng)
            }
            controller.onMapDoubleClick = { address, lat, lng ->
                val nextIndex = (latestWaypoints.value.maxOfOrNull { it.index } ?: -1) + 1
                viewModel.sendMessage(address, nextIndex, lat, lng)
            }
            mapController = controller

            try {
                val (lat, lng) = getCurrentPosition()
                controller.centerOn(lat, lng, zoom = 13)
            } catch (e: Exception) {
                Napier.d("Could not get user location, using default center: ${e.message}")
            }
        } catch (e: Exception) {
            Napier.e("Failed to initialize map: ${e.message}")
        }
    }

    // Update map when waypoints/polyline change or map becomes available
    LaunchedEffect(routeState.waypoints, routeState.encodedPolyline, mapController) {
        mapController?.updateWaypoints(routeState.waypoints, routeState.encodedPolyline)
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
        // Header with logout button
        Div({
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.SpaceBetween)
                alignItems(AlignItems.Center)
                marginBottom(16.px)
            }
        }) {
            H1({
                style {
                    color(Color("#333"))
                    property("margin", "0")
                }
            }) {
                Text("Dromedario Route Planner")
            }

            Button({
                onClick { onLogout() }
                style {
                    padding(8.px, 16.px)
                    backgroundColor(Color("#6c757d"))
                    color(Color.white)
                    border(0.px, LineStyle.None, Color.transparent)
                    borderRadius(4.px)
                    fontSize(14.px)
                    cursor("pointer")
                }
            }) {
                Text("Logout")
            }
        }

        TripStatus(routeState.waypoints.size)
        WaypointList(
            waypoints = routeState.waypoints,
            onRemove = { index -> viewModel.deleteMessage(index) },
            onReorder = { fromIndex, toIndex -> viewModel.reorderWaypoints(fromIndex, toIndex) }
        )

        Div({
            style { marginBottom(16.px) }
        }) {
            H3 { Text("Add Waypoint") }
            Div({
                style {
                    width(100.percent)
                    backgroundColor(Color.white)
                }
                ref { element ->
                    searchInputRef = element
                    onDispose { searchInputRef = null }
                }
            })
        }

        ActionButtons(routeState.waypoints) {
            viewModel.clearAllWaypoints()
        }
        MyLocationButton(mapController)
    }
}
