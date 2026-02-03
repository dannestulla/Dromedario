package br.gohan.dromedario.map

import br.gohan.dromedario.MAPS_API_KEY
import io.github.aakira.napier.Napier
import kotlinx.coroutines.await
import kotlin.js.Promise

@JsModule("@googlemaps/js-api-loader")
external object JsApiLoaderModule

// Loads the Google Maps JS API via the @googlemaps/js-api-loader npm package. Idempotent.
class GoogleMapsLoader {
    private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return

        val LoaderClass = JsApiLoaderModule.asDynamic().Loader

        val options = js("{}")
        options.apiKey = MAPS_API_KEY
        options.version = "weekly"

        val args = js("[]")
        args.push(options)
        val loader = js("Reflect").construct(LoaderClass, args)

        (loader.importLibrary("maps") as Promise<dynamic>).await()
        (loader.importLibrary("marker") as Promise<dynamic>).await()
        (loader.importLibrary("places") as Promise<dynamic>).await()
        (loader.importLibrary("geometry") as Promise<dynamic>).await()

        loaded = true
        Napier.d("GoogleMapsLoader: All libraries loaded")
    }
}
