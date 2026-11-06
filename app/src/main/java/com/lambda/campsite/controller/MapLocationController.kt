/*
 * AI use: Gemini 3.8 Flash (Google DeepMind) helped write this file in November 2026.
 * It helped with the map pin coordinate state, location search with preset parks,
 * drag-to-move offset updates, syntax and KDoc. Raghav Sethi reviewed, tested,
 * and committed it.
 */

 /**
 * Purpose: Manages the campsite location selected through search or the map pin.
 * Feature owner: Raghav Sethi
 * Outstanding issues: None
 */
 
package com.lambda.campsite.controller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A known geographic place or park for location search.
 *
 * @property name human-readable name of the park or region.
 * @property latitude geographic latitude.
 * @property longitude geographic longitude.
 */
data class PlaceLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

/**
 * State for the map location picker screen (US 02.01.02).
 *
 * @property searchQuery text typed into the location search bar.
 * @property latitude current latitude of the pinned location.
 * @property longitude current longitude of the pinned location.
 * @property pinLocationName human-readable label or nearest park name.
 * @property suggestions list of matching places filtered by [searchQuery].
 */
data class MapLocationUiState(
    val searchQuery: String = "",
    val latitude: Double = 53.6000,
    val longitude: Double = -112.8667,
    val pinLocationName: String = "Elk Island National Park, AB",
    val suggestions: List<PlaceLocation> = emptyList(),
)

/**
 * Controller for setting campsite map coordinates (US 02.01.02).
 *
 * Allows owners to search for a park or drag the pin on the map canvas
 * to set exact latitude and longitude coordinates.
 */
class MapLocationController(
    initialLat: Double? = null,
    initialLng: Double? = null,
    initialName: String? = null,
) : ViewModel() {

    private val defaultPlaces = listOf(
        PlaceLocation("Elk Island National Park, AB", 53.6000, -112.8667),
        PlaceLocation("Jasper National Park, AB", 52.8734, -118.0814),
        PlaceLocation("Banff National Park, AB", 51.4968, -115.9281),
        PlaceLocation("Hinton – Grande Cache, AB", 53.4116, -117.5739),
        PlaceLocation("Canmore, AB", 51.0890, -115.3597),
        PlaceLocation("Peter Lougheed Provincial Park, AB", 50.6860, -115.1430),
        PlaceLocation("Waterton Lakes National Park, AB", 49.0520, -113.9100),
    )

    private val _state = MutableStateFlow(
        MapLocationUiState(
            latitude = initialLat ?: 53.6000,
            longitude = initialLng ?: -112.8667,
            pinLocationName = initialName ?: "Elk Island National Park, AB",
            suggestions = defaultPlaces,
        )
    )

    /** The current UI state of the map location screen. */
    val state: StateFlow<MapLocationUiState> = _state.asStateFlow()

    /** Updates the search text and filters suggested destinations. */
    fun onQueryChange(query: String) {
        val matches = if (query.isBlank()) {
            defaultPlaces
        } else {
            defaultPlaces.filter { it.name.contains(query, ignoreCase = true) }
        }
        _state.update { it.copy(searchQuery = query, suggestions = matches) }
    }

    /** Selects a place from search results or direct selection. */
    fun onPlaceSelected(place: PlaceLocation) {
        _state.update {
            it.copy(
                searchQuery = place.name,
                latitude = place.latitude,
                longitude = place.longitude,
                pinLocationName = place.name,
                suggestions = emptyList(),
            )
        }
    }

    /**
     * Adjusts the pin coordinates when dragged across the map canvas.
     *
     * @param deltaLat shift in latitude.
     * @param deltaLng shift in longitude.
     */
    fun onPinDragged(deltaLat: Double, deltaLng: Double) {
        _state.update { current ->
            val newLat = current.latitude + deltaLat
            val newLng = current.longitude + deltaLng
            // Find closest named park if nearby, or format coordinates
            val closest = defaultPlaces.minByOrNull {
                val dLat = it.latitude - newLat
                val dLng = it.longitude - newLng
                dLat * dLat + dLng * dLng
            }
            val name = if (closest != null) {
                val distSq = (closest.latitude - newLat) * (closest.latitude - newLat) +
                        (closest.longitude - newLng) * (closest.longitude - newLng)
                if (distSq < 0.1) closest.name else "%.4f° N, %.4f° W".format(newLat, -newLng)
            } else {
                "%.4f° N, %.4f° W".format(newLat, -newLng)
            }

            current.copy(latitude = newLat, longitude = newLng, pinLocationName = name)
        }
    }

    companion object {
        /** Creates a [MapLocationController] with optional initial coordinates. */
        fun createFactory(lat: Double? = null, lng: Double? = null, name: String? = null): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MapLocationController(lat, lng, name) }
            }
    }
}
