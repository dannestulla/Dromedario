package br.gohan.dromedario.domain

import com.google.android.gms.maps.model.LatLng

fun List<LatLng>.geometricCenter(): LatLng? {
    if (isEmpty()) return null

    val totalLat = sumOf { it.latitude }
    val totalLng = sumOf { it.longitude }

    return LatLng(
        totalLat / size,
        totalLng / size
    )
}