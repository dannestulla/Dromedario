package br.gohan.dromedario.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import io.github.aakira.napier.Napier

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Napier.e("GeofenceReceiver: GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorCode = geofencingEvent.errorCode
            Napier.e("GeofenceReceiver: Error code $errorCode")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Napier.d("GeofenceReceiver: User entered geofence")
                val triggeringGeofences = geofencingEvent.triggeringGeofences
                val geofenceIds = triggeringGeofences?.map { it.requestId }?.joinToString(", ")
                Napier.d("GeofenceReceiver: Triggered geofences: $geofenceIds")

                // Show arrival notification
                NotificationHelper(context).showArrivalNotification()
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Napier.d("GeofenceReceiver: User exited geofence")
            }
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                Napier.d("GeofenceReceiver: User is dwelling in geofence")
            }
            else -> {
                Napier.e("GeofenceReceiver: Unknown transition type: $geofenceTransition")
            }
        }
    }
}
