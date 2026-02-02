package br.gohan.dromedario.map.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import br.gohan.dromedario.data.Waypoint
import br.gohan.dromedario.map.MapController
import io.github.aakira.napier.Napier
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.Color
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.LineStyle
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.border
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.cursor
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


@Composable
fun TripStatus(waypointCount: Int) {
    Div({
        style {
            padding(12.px)
            marginBottom(16.px)
            backgroundColor(Color("#f5f5f5"))
            borderRadius(8.px)
        }
    }) {
        Text("Status: $waypointCount waypoints")
    }
}

@Composable
fun WaypointList(waypoints: List<Waypoint>, onRemove: (Int) -> Unit) {
    Div({
        style {
            marginBottom(16.px)
        }
    }) {
        H3 { Text("Waypoints") }

        if (waypoints.isEmpty()) {
            P({
                style { color(Color("#666")) }
            }) {
                Text("No waypoints added yet. Search for an address below to add one.")
            }
        } else {
            waypoints.forEach { waypoint ->
                WaypointItem(waypoint, onRemove)
            }
        }
    }
}

@Composable
fun WaypointItem(waypoint: Waypoint, onRemove: (Int) -> Unit) {
    Div({
        style {
            display(DisplayStyle.Flex)
            justifyContent(JustifyContent.SpaceBetween)
            alignItems(AlignItems.Center)
            padding(12.px)
            marginBottom(8.px)
            backgroundColor(Color("#fff"))
            border(1.px, LineStyle.Solid, Color("#ddd"))
            borderRadius(4.px)
        }
    }) {
        Span {
            Text("${waypoint.index + 1}. ${waypoint.address}")
        }
        Button({
            onClick { onRemove(waypoint.index) }
            style {
                backgroundColor(Color("#dc3545"))
                color(Color.white)
                border(0.px, LineStyle.None, Color.transparent)
                padding(6.px, 12.px)
                borderRadius(4.px)
                cursor("pointer")
            }
        }) {
            Text("Remove")
        }
    }
}

@Composable
fun ActionButtons(waypoints: List<Waypoint>) {
    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            gap(8.px)
        }
    }) {
        Button({
            onClick {
                Napier.d("Optimize route requested")
                // TODO: Send OPTIMIZE_ROUTE event
            }
            style {
                backgroundColor(Color("#17a2b8"))
                color(Color.white)
                border(0.px, LineStyle.None, Color.transparent)
                padding(12.px, 24.px)
                borderRadius(4.px)
                cursor("pointer")
                fontSize(16.px)
            }
        }) {
            Text("Optimize Route")
        }

        val isEnabled = waypoints.size >= 2
        Button({
            onClick {
                Napier.d("Finalize route requested")
                // TODO: Send FINALIZE_ROUTE event
            }
            style {
                backgroundColor(if (isEnabled) Color("#007bff") else Color("#ccc"))
                color(Color.white)
                border(0.px, LineStyle.None, Color.transparent)
                padding(12.px, 24.px)
                borderRadius(4.px)
                cursor(if (isEnabled) "pointer" else "not-allowed")
                fontSize(16.px)
            }
        }) {
            Text("Finalize Route")
        }
    }
}

@Composable
fun MyLocationButton(mapController: MapController?) {
    val scope = rememberCoroutineScope()

    Button({
        onClick {
            scope.launch {
                try {
                    val currentPosition = getCurrentPosition()
                    mapController?.centerOn(currentPosition.first, currentPosition.second)
                } catch (e: Exception) {
                    Napier.e("Failed to get location: ${e.message}")
                }
            }
        }
        style {
            backgroundColor(Color("#6c757d"))
            color(Color.white)
            border(0.px, LineStyle.None, Color.transparent)
            padding(12.px, 24.px)
            borderRadius(4.px)
            cursor("pointer")
            fontSize(16.px)
            marginTop(8.px)
            width(100.percent)
        }
    }) {
        Text("Center on My Location")
    }
}

@Composable
fun NavigationStatus() {
    Div({
        style {
            marginTop(24.px)
            padding(16.px)
            backgroundColor(Color("#e7f3ff"))
            borderRadius(8.px)
        }
    }) {
        P({
            style {
                margin(0.px)
                color(Color("#666"))
            }
        }) {
            Text("Web client is for route planning. Use the mobile app for navigation with geofencing.")
        }
    }
}

private suspend fun getCurrentPosition(): Pair<Double, Double> =
    suspendCoroutine { cont ->
        val nav = window.navigator.asDynamic()
        if (nav.geolocation != null && nav.geolocation != undefined) {
            nav.geolocation.getCurrentPosition(
                { pos: dynamic ->
                    val lat = pos.coords.latitude as Double
                    val lng = pos.coords.longitude as Double
                    cont.resume(Pair(lat, lng))
                },
                { err: dynamic ->
                    cont.resumeWithException(Exception("Geolocation error: ${err.message}"))
                }
            )
        } else {
            cont.resumeWithException(Exception("Geolocation not supported"))
        }
    }