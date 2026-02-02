package br.gohan.dromedario.presenter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.gohan.dromedario.geofence.NotificationHelper
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val mobileViewModel: MobileViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Napier.base(DebugAntilog())

        // Handle intent if launched from notification
        handleNotificationIntent(intent)

        setContent {
            MobileApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent?.action == NotificationHelper.ACTION_START_NEXT_GROUP) {
            Napier.d("MainActivity: Received ACTION_START_NEXT_GROUP from notification")
            // Trigger the next group navigation
            mobileViewModel.startNextGroup(this)
        }
    }
}
