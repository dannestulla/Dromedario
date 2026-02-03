package br.gohan.dromedario.map

import io.github.aakira.napier.Napier
import org.w3c.dom.Element

// Attaches a Google Places autocomplete element to a DOM container. Biased to Porto Alegre, BR.
fun attachPlacesAutocomplete(
    containerElement: Element,
    onPlaceSelected: (address: String, latitude: Double, longitude: Double) -> Unit
) {
    val options = js("{}")
    val bias = js("{}")
    bias.center = latLng(-30.05, -51.20)
    bias.radius = 50000
    options.locationBias = bias
    options.includedRegionCodes = js("['BR']")

    val autocompleteElement = newPlaceAutocompleteElement(options)
    containerElement.appendChild(autocompleteElement)

    val callback: (dynamic) -> Unit = { event ->
        val placePrediction: PlacePrediction? = event.placePrediction?.unsafeCast<PlacePrediction>()
        placePrediction?.let { prediction ->
            val place = prediction.toPlace()
            place.fetchFields(fetchFieldsOptions("formattedAddress", "location")).then { resolved ->
                val location: LatLng? = resolved.location
                location?.let {
                    val address = resolved.formattedAddress ?: "Unknown location"
                    onPlaceSelected(address, it.lat(), it.lng())
                }
            }
        }
    }
    autocompleteElement.addEventListener("gmp-select", callback)
    Napier.d("PlacesAutocomplete: Attached to container")
}
