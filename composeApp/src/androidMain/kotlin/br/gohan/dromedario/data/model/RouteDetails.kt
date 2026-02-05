package br.gohan.dromedario.data.model

import com.google.android.gms.maps.model.LatLng

data class RouteDetails(
    val polyline: String,
    val totalDistanceMeters: Int,
    val totalDuration: String,
    val legs: List<LegDetails>
)

data class LegDetails(
    val distanceMeters: Int,
    val duration: String,
    val staticDuration: String,
    val startLocation: LatLng,
    val endLocation: LatLng,
    val localizedDistance: String? = null,
    val localizedDuration: String? = null
)
