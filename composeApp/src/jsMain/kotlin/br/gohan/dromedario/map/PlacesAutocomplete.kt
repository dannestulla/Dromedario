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
        try {
            val placePrediction: PlacePrediction? = event.placePrediction?.unsafeCast<PlacePrediction>()
            if (placePrediction == null) {
                Napier.e("PlacesAutocomplete: placePrediction is null on event")
            }
            val place = placePrediction?.toPlace()
            place?.fetchFields(fetchFieldsOptions("formattedAddress", "location"))?.then {
                // fetchFields populates the original place object in-place
                val location: LatLng? = place.location
                if (location == null) {
                    Napier.e("PlacesAutocomplete: location is null after fetchFields")
                    return@then null
                }
                val address = place.formattedAddress ?: "Unknown location"
                Napier.d("PlacesAutocomplete: Selected '$address'")
                onPlaceSelected(address, location.lat(), location.lng())
                null
            }
        } catch (e: Throwable) {
            Napier.e("PlacesAutocomplete: Error in gmp-select handler: ${e.message}")
        }
    }
    autocompleteElement.addEventListener("gmp-select", callback)
}
