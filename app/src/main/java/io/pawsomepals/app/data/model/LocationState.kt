package io.pawsomepals.app.data.model

import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place

sealed class LocationState {
    object Initial : LocationState()
    object Loading : LocationState()

    data class Success(
        val nearbyLocations: List<DogFriendlyLocation> = emptyList(),
        val recommendedLocations: List<DogFriendlyLocation> = emptyList(),
        val optimalLocation: DogFriendlyLocation? = null
    ) : LocationState()

    data class SearchResults(
        val predictions: List<AutocompletePrediction> = emptyList(),
        val places: List<Place> = emptyList()
    ) : LocationState()

    data class Error(val message: String) : LocationState()
}