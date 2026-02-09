package br.gohan.dromedario.export

import androidx.compose.runtime.*
import br.gohan.dromedario.data.Waypoint
import br.gohan.dromedario.presenter.ClientSharedViewModel
import kotlinx.browser.window
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.koin.compose.koinInject

private const val MAX_GROUP_SIZE = 9

@Composable
fun ExportApp(token: String, onLogout: () -> Unit, viewModel: ClientSharedViewModel = koinInject()) {
    val routeState by viewModel.incomingFlow.collectAsState()

    LaunchedEffect(token) {
        val host = window.location.host
        val protocol = if (window.location.protocol == "https:") "wss" else "ws"
        viewModel.startWebSocket("$protocol://$host/ws?token=$token")
    }

    Div({
        style {
            fontFamily("-apple-system", "BlinkMacSystemFont", "Segoe UI", "Roboto", "sans-serif")
            backgroundColor(Color("#f5f5f5"))
            color(Color("#333"))
            padding(16.px)
            property("min-height", "100vh")
            property("box-sizing", "border-box")
        }
    }) {
        // Header with logout button
        Div({
            style {
                display(DisplayStyle.Flex)
                justifyContent(JustifyContent.SpaceBetween)
                alignItems(AlignItems.Center)
                marginBottom(4.px)
            }
        }) {
            H1({
                style {
                    fontSize(24.px)
                    property("margin", "0")
                }
            }) {
                Text("Dromedario / Export")
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
                    property("white-space", "nowrap")
                }
            }) {
                Text("Logout")
            }
        }

        Div({
            style {
                height(16.px)
            }
        })

        val waypoints = routeState.waypoints
        if (waypoints.isEmpty()) {
            WaitingState()
        } else {
            val groups = waypoints.chunked(MAX_GROUP_SIZE)
            Div({
                style {
                    fontSize(15.px)
                    color(Color("#555"))
                    marginBottom(12.px)
                }
            }) {
                val groupLabel = if (groups.size > 1) "groups" else "group"
                Text("${waypoints.size} stops total \u00B7 ${groups.size} $groupLabel")
            }

            // GPX Export Button
            Div({
                style {
                    marginBottom(16.px)
                }
            }) {
                Button({
                    onClick { exportGpx(token) }
                    style {
                        width(100.percent)
                        padding(16.px)
                        backgroundColor(Color("#28a745"))
                        color(Color.white)
                        border(0.px, LineStyle.None, Color.transparent)
                        borderRadius(8.px)
                        fontSize(16.px)
                        fontWeight(600)
                        cursor("pointer")
                        display(DisplayStyle.Flex)
                        alignItems(AlignItems.Center)
                        justifyContent(JustifyContent.Center)
                        gap(8.px)
                    }
                }) {
                    Text("Export GPX for Navigation Apps")
                }
                P({
                    style {
                        fontSize(12.px)
                        color(Color("#666"))
                        property("text-align", "center")
                        property("margin", "8px 0 0 0")
                    }
                }) {
                    Text("Works with OsmAnd, Locus Map, Organic Maps, and other GPX-compatible apps")
                }
            }

            Div({
                style {
                    fontSize(13.px)
                    color(Color("#888"))
                    marginBottom(12.px)
                }
            }) {
                Text("Or open in Google Maps (preview only):")
            }

            groups.forEachIndexed { index, group ->
                GroupCard(index + 1, group)
            }
        }
    }
}

@Composable
private fun WaitingState() {
    Div({
        style {
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            alignItems(AlignItems.Center)
            justifyContent(JustifyContent.Center)
            property("min-height", "60vh")
            property("text-align", "center")
            color(Color("#888"))
        }
    }) {
        // Spinner
        Div({
            classes("dromedario-spinner")
        })

        P({
            style {
                property("margin", "8px 0")
                fontSize(16.px)
            }
        }) {
            Text("Connecting to web app...")
        }

        P({
            style {
                property("margin", "8px 0")
                fontSize(14.px)
            }
        }) {
            Text("Add waypoints on the web dashboard to see route groups here")
        }
    }
}

@Composable
private fun GroupCard(groupNumber: Int, waypoints: List<Waypoint>) {
    val count = waypoints.size
    val isFull = count >= MAX_GROUP_SIZE
    val pct = ((count.toDouble() / MAX_GROUP_SIZE) * 100).toInt()
    val statusColor = if (isFull) "#2e7d32" else "#ed6c02"
    val firstAddr = waypoints.firstOrNull()?.address ?: ""
    val lastAddr = waypoints.lastOrNull()?.address ?: ""

    Div({
        style {
            backgroundColor(Color.white)
            borderRadius(12.px)
            padding(16.px)
            marginBottom(12.px)
            property("box-shadow", "0 2px 8px rgba(0,0,0,0.1)")
            display(DisplayStyle.Flex)
            alignItems(AlignItems.Center)
            cursor("pointer")
            property("-webkit-tap-highlight-color", "transparent")
            property("transition", "transform 0.1s")
        }
        onClick { openGoogleMaps(waypoints) }
    }) {
        // Card body
        Div({
            style {
                property("flex", "1")
            }
        }) {
            // Header row
            Div({
                style {
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.SpaceBetween)
                    alignItems(AlignItems.Baseline)
                    marginBottom(6.px)
                }
            }) {
                Span({
                    style {
                        fontSize(18.px)
                        fontWeight(700)
                    }
                }) {
                    Text("Group $groupNumber")
                }
                Span({
                    style {
                        fontSize(14.px)
                        fontWeight(600)
                        color(Color(statusColor))
                    }
                }) {
                    Text("$count of $MAX_GROUP_SIZE stops")
                }
            }

            // Progress bar
            Div({
                style {
                    height(6.px)
                    backgroundColor(Color("#e0e0e0"))
                    borderRadius(3.px)
                    marginBottom(10.px)
                    overflow("hidden")
                }
            }) {
                Div({
                    style {
                        height(100.percent)
                        width(pct.percent)
                        borderRadius(3.px)
                        backgroundColor(Color(statusColor))
                        property("transition", "width 0.3s")
                    }
                })
            }

            // Address range
            Div({
                style {
                    fontSize(13.px)
                    color(Color("#555"))
                    property("line-height", "1.4")
                }
            }) {
                Text("From: $firstAddr")
            }
            Div({
                style {
                    fontSize(13.px)
                    color(Color("#555"))
                    property("line-height", "1.4")
                }
            }) {
                Text("To: $lastAddr")
            }
        }

        // Arrow
        Div({
            style {
                fontSize(24.px)
                color(Color("#007bff"))
                paddingLeft(12.px)
            }
        }) {
            Text("\u25B6")
        }
    }
}

private fun openGoogleMaps(waypoints: List<Waypoint>) {
    if (waypoints.size < 2) return
    val origin = waypoints.first()
    val dest = waypoints.last()
    val via = waypoints.drop(1).dropLast(1)

    var url = "https://www.google.com/maps/dir/?api=1" +
            "&origin=${origin.latitude},${origin.longitude}" +
            "&destination=${dest.latitude},${dest.longitude}"

    if (via.isNotEmpty()) {
        url += "&waypoints=" + via.joinToString("|") { "${it.latitude},${it.longitude}" }
    }
    url += "&travelmode=driving&dir_action=navigate"
    window.open(url, "_blank")
}

private fun exportGpx(token: String) {
    // Navigate to server endpoint - this will serve the GPX file with proper headers
    // Android will show "Open with" dialog for apps that handle GPX files
    val host = window.location.host
    val protocol = window.location.protocol
    val gpxUrl = "$protocol//$host/api/gpx?token=$token"

    // Open in same window to trigger Android's file handler
    window.location.href = gpxUrl
}

