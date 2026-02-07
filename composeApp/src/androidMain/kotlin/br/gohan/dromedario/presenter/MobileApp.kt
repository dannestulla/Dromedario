package br.gohan.dromedario.presenter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.gohan.dromedario.data.TripSession
import br.gohan.dromedario.data.TripStatus
import br.gohan.dromedario.domain.PermissionHelper
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MobileApp() {
    val url = "ws://10.0.2.2:8080/ws"
    val permissionHelper: PermissionHelper = koinInject()
    var hasPermissions by remember { mutableStateOf(permissionHelper.hasLocationPermissions()) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissionHelper.hasLocationPermissions()
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            locationPermissionLauncher.launch(PermissionHelper.LOCATION_PERMISSIONS)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MapWithRoute(modifier = Modifier.fillMaxHeight(0.5f))
        NavigationControlsSection()
        CommonScreen(url)
    }
}

@Composable
fun NavigationControlsSection(
    viewModel: MobileViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val tripState by viewModel.tripState.collectAsStateWithLifecycle()
    val isNavigating by viewModel.isNavigating.collectAsStateWithLifecycle()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NavigationControls(
                tripState = tripState,
                isNavigating = isNavigating,
                onOptimize = { viewModel.optimizeRoute() },
                onFinalize = { viewModel.finalizeRoute() },
                onStartNavigation = { viewModel.startNavigation(context) },
                onCancelNavigation = { viewModel.cancelNavigation() },
                onExportGpx = { viewModel.exportToGpx(context) }
            )
        }
    }
}

@Composable
fun NavigationControls(
    tripState: TripSession?,
    isNavigating: Boolean,
    onOptimize: () -> Unit,
    onFinalize: () -> Unit,
    onStartNavigation: () -> Unit,
    onCancelNavigation: () -> Unit,
    onExportGpx: () -> Unit
) {
    when (tripState?.status) {
        TripStatus.PLANNING -> {
            PlanningControls(
                waypointCount = tripState.waypoints.size,
                onOptimize = onOptimize,
                onFinalize = onFinalize,
                onExportGpx = onExportGpx
            )
        }
        TripStatus.NAVIGATING -> {
            NavigatingControls(
                tripState = tripState,
                isNavigating = isNavigating,
                onStartNavigation = onStartNavigation,
                onCancelNavigation = onCancelNavigation
            )
        }
        TripStatus.COMPLETED -> {
            CompletedView()
        }
        null -> {
            Text(
                text = "Loading trip state...",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun PlanningControls(
    waypointCount: Int,
    onOptimize: () -> Unit,
    onFinalize: () -> Unit,
    onExportGpx: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Planning Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$waypointCount waypoints added",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOptimize,
                enabled = waypointCount >= 3
            ) {
                Text("Optimize Route")
            }
            Button(
                onClick = onFinalize,
                enabled = waypointCount >= 2,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Finalize Route")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onExportGpx,
            enabled = waypointCount >= 2,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF28A745)
            )
        ) {
            Text("Export GPX for Navigation Apps")
        }
        if (waypointCount < 2) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Add at least 2 waypoints to export",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NavigatingControls(
    tripState: TripSession,
    isNavigating: Boolean,
    onStartNavigation: () -> Unit,
    onCancelNavigation: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Navigation Mode",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Progress indicator
        val progress = (tripState.activeGroupIndex.toFloat()) / tripState.groups.size.toFloat()
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Text(
            text = "Group ${tripState.activeGroupIndex + 1} of ${tripState.groups.size}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )

        val activeGroup = tripState.groups.getOrNull(tripState.activeGroupIndex)
        if (activeGroup != null) {
            val waypointsInGroup = activeGroup.waypointEndIndex - activeGroup.waypointStartIndex
            Text(
                text = "$waypointsInGroup stops in this group",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isNavigating) {
                Button(
                    onClick = onStartNavigation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Start Navigation")
                }
            } else {
                Button(
                    onClick = onCancelNavigation,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancel")
                }
            }
        }

        if (isNavigating) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Navigation active - arrive at last stop to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompletedView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "All Deliveries Completed!",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Great job! All route groups have been completed.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun MapWithRoute(
    modifier: Modifier = Modifier,
    viewModel: MobileViewModel = koinViewModel()
) {
    val origin = LatLng(-30.057507887593573, -51.20465090482286)
    val destination = LatLng(-30.04280619709763, -51.19002159469383)

    val route by viewModel.route.collectAsStateWithLifecycle()
    val camera by viewModel.cameraPosition.collectAsStateWithLifecycle()
    val tripState by viewModel.tripState.collectAsStateWithLifecycle()

    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(origin, destination) {
        viewModel.getRoutePoints(origin, destination, listOf())
    }

    LaunchedEffect(camera) {
        camera?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 13f)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState
    ) {
        // Show markers for all waypoints
        tripState?.waypoints?.forEachIndexed { index, waypoint ->
            Marker(
                state = remember(waypoint) {
                    MarkerState(position = LatLng(waypoint.latitude, waypoint.longitude))
                },
                title = "Stop ${index + 1}",
                snippet = waypoint.address
            )
        }

        // Fallback markers if no trip state
        if (tripState == null) {
            Marker(state = remember { MarkerState(position = origin) })
            Marker(state = remember { MarkerState(position = destination) })
        }

        // Route Polyline
        if (route.isNotEmpty()) {
            Polyline(
                points = route,
                color = Color.Blue,
                width = 8f
            )
        }
    }
}