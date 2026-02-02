package br.gohan.dromedario.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import br.gohan.dromedario.data.Waypoint
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.github.aakira.napier.Napier

class GeofenceManagerHelper(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQUEST_CODE,
            intent,
            flags
        )
    }

    fun registerGeofenceForWaypoint(
        waypoint: Waypoint,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val geofence = Geofence.Builder()
            .setRequestId("destination_${waypoint.index}")
            .setCircularRegion(waypoint.latitude, waypoint.longitude, GEOFENCE_RADIUS_METERS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setLoiteringDelay(LOITERING_DELAY_MS)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Napier.e("GeofenceManager: Location permission not granted")
            onFailure(SecurityException("Location permission not granted"))
            return
        }

        geofencingClient.addGeofences(request, geofencePendingIntent)
            .addOnSuccessListener {
                Napier.d("GeofenceManager: Geofence registered for waypoint ${waypoint.index}")
                onSuccess()
            }
            .addOnFailureListener { exception ->
                Napier.e("GeofenceManager: Failed to register geofence: ${exception.message}")
                onFailure(exception)
            }
    }

    fun removeAllGeofences(onComplete: () -> Unit = {}) {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnCompleteListener {
                Napier.d("GeofenceManager: All geofences removed")
                onComplete()
            }
    }

    fun removeGeofenceById(requestId: String, onComplete: () -> Unit = {}) {
        geofencingClient.removeGeofences(listOf(requestId))
            .addOnCompleteListener {
                Napier.d("GeofenceManager: Geofence $requestId removed")
                onComplete()
            }
    }

    companion object {
        const val GEOFENCE_RADIUS_METERS = 150f
        const val LOITERING_DELAY_MS = 5000 // 5 seconds
        private const val GEOFENCE_REQUEST_CODE = 1001
    }
}
