package br.gohan.dromedario.presenter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MobileApp() {
    val url = "ws://10.0.2.2:8080/ws"

    Column(modifier = Modifier.fillMaxSize()) {
        MapWithRoute()
        CommonScreen(url)
    }
}

@Composable
fun MapWithRoute(
    viewModel : MobileViewModel = koinViewModel()
) {
    val origin = LatLng(-30.057507887593573, -51.20465090482286)
    val destination = LatLng(-30.04280619709763, -51.19002159469383)

    val route by viewModel.route.collectAsStateWithLifecycle()
    val camera by viewModel.cameraPosition.collectAsStateWithLifecycle()

    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(origin, destination) {
        viewModel.getRoutePoints(origin, destination, listOf())
    }

    LaunchedEffect(camera) {
        camera?.let {
            cameraPositionState.position =  CameraPosition.fromLatLngZoom(it, 13f)
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxHeight(0.6f),
        cameraPositionState = cameraPositionState
    ) {
        // Markers
        Marker(state = remember {  MarkerState(position = origin)})
        Marker(state =remember {  MarkerState(position = destination)})

        // Rota como Polyline
        if (route.isNotEmpty()) {
            Polyline(
                points = route,
                color = Color.Blue,
                width = 8f
            )
        }
    }
}