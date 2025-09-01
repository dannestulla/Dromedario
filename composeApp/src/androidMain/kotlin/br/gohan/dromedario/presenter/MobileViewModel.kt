package br.gohan.dromedario.presenter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.gohan.dromedario.data.MobileRepository
import br.gohan.dromedario.domain.geometricCenter
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.ktx.utils.toLatLngList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MobileViewModel(
    private val mobileRepository: MobileRepository,
    private val clientSharedViewModel: ClientSharedViewModel
) : ViewModel() {
    private val _route = MutableStateFlow<List<LatLng>>(listOf())
    val route = _route.asStateFlow()

    private val _cameraPosition = MutableStateFlow<LatLng?>(null)
    val cameraPosition = _cameraPosition.asStateFlow()

    fun getRoutePoints(origin: LatLng, destination: LatLng, waypoints: List<LatLng>) {
        viewModelScope.launch {
            val result = mobileRepository.getRoutePolyline(origin, destination, waypoints)!!
            _route.emit(result.toLatLngList())
            _cameraPosition.emit(result.toLatLngList().geometricCenter())
        }
    }
}