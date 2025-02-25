// LocationPickerViewModel.kt
package io.pawsomepals.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.model.LocationState
import io.pawsomepals.app.service.location.GoogleMapsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    private val googleMapsService: GoogleMapsService
) : ViewModel() {

    private val _locationState = MutableStateFlow<LocationState>(LocationState.Initial)
    val locationState: StateFlow<LocationState> = _locationState

    private var currentLocation: LatLng? = null
    private var currentQuery: String = ""

    fun searchLocations(query: String) {
        Log.d("LocationPicker", "Starting location search with query: '$query'")
        currentQuery = query
        viewModelScope.launch {
            try {
                _locationState.value = LocationState.Loading

                val result = googleMapsService.getAutocompleteResults(
                    query = query,
                    location = currentLocation ?: DEFAULT_LOCATION,
                    radius = SEARCH_RADIUS
                )

                when (result) {
                    is GoogleMapsService.PlacesResult.Success -> {
                        _locationState.value = LocationState.SearchResults(
                            predictions = result.data,
                            places = emptyList()
                        )
                    }
                    is GoogleMapsService.PlacesResult.Error -> {
                        _locationState.value = LocationState.Error(
                            result.exception.message ?: "Error searching locations"
                        )
                    }
                }
            } catch (e: Exception) {
                _locationState.value = LocationState.Error(e.message ?: "Error searching locations")
            }
        }
    }

    fun getPlaceDetails(placeId: String) {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            try {
                val result = googleMapsService.getPlaceDetails(placeId)
                when (result) {
                    is GoogleMapsService.PlacesResult.Success -> {
                        _locationState.value = LocationState.SearchResults(
                            predictions = emptyList(),
                            places = listOf(result.data)
                        )
                    }
                    is GoogleMapsService.PlacesResult.Error -> {
                        _locationState.value = LocationState.Error(
                            result.exception.message ?: "Error getting place details"
                        )
                    }
                }
            } catch (e: Exception) {
                _locationState.value = LocationState.Error(e.message ?: "Error getting place details")
            }
        }
    }

    fun selectPlace(place: Place) {
        currentLocation = place.latLng
    }

    fun retry() {
        if (currentQuery.isNotEmpty()) {
            searchLocations(currentQuery)
        }
    }

    companion object {
        private val DEFAULT_LOCATION = LatLng(40.7128, -74.0060) // New York
        private const val SEARCH_RADIUS = 5000.0 // 5km
    }
}