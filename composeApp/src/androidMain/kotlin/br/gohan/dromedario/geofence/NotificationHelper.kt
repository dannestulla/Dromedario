package br.gohan.dromedario.geofence

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import br.gohan.dromedario.R
import br.gohan.dromedario.presenter.MainActivity
import io.github.aakira.napier.Napier

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for route completion and navigation"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
            Napier.d("NotificationHelper: Notification channel created")
        }
    }

    fun showArrivalNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_NEXT_GROUP
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Route Complete!")
            .setContentText("Tap to start the next group of destinations")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Napier.d("NotificationHelper: Arrival notification shown")
    }

    fun showProgressNotification(currentGroup: Int, totalGroups: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Navigating Route")
            .setContentText("Group $currentGroup of $totalGroups in progress")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()

        notificationManager.notify(PROGRESS_NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun cancelProgressNotification() {
        notificationManager.cancel(PROGRESS_NOTIFICATION_ID)
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    companion object {
        const val CHANNEL_ID = "dromedario_navigation"
        const val CHANNEL_NAME = "Route Navigation"
        const val NOTIFICATION_ID = 1001
        const val PROGRESS_NOTIFICATION_ID = 1002
        const val ACTION_START_NEXT_GROUP = "br.gohan.dromedario.ACTION_START_NEXT_GROUP"
    }
}
