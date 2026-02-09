package br.gohan.dromedario.data

/**
 * Generates GPX (GPS Exchange Format) files from waypoints.
 * GPX is a standard format supported by navigation apps like OsmAnd, Locus Map, etc.
 */
object GpxGenerator {

    /**
     * Generates a GPX route file from a list of waypoints.
     * Uses <rte> (route) element which navigation apps interpret as turn-by-turn directions.
     */
    fun generateRoute(waypoints: List<Waypoint>, routeName: String = "Dromedario Route"): String {
        val routePoints = waypoints.joinToString("\n") { waypoint ->
            """    <rtept lat="${waypoint.latitude}" lon="${waypoint.longitude}">
      <name>${escapeXml(waypoint.address)}</name>
      <desc>Stop ${waypoint.index + 1}</desc>
    </rtept>"""
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Dromedario"
     xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
     xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
  <metadata>
    <name>${escapeXml(routeName)}</name>
    <desc>Route with ${waypoints.size} stops</desc>
  </metadata>
  <rte>
    <name>${escapeXml(routeName)}</name>
$routePoints
  </rte>
</gpx>"""
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
