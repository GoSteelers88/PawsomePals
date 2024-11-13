package io.pawsomepals.app.ui.screens.playdate.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.repository.LocationRepository
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.service.location.LocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationSearchViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val locationService: LocationService,
    private val locationSearchService: LocationSearchService
) : ViewModel() {

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Initial)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilters = MutableStateFlow(LocationSearchService.LocationFilters())
    val selectedFilters: StateFlow<LocationSearchService.LocationFilters> = _selectedFilters.asStateFlow()



    init {
        getCurrentLocation()
    }

    private fun getCurrentLocation() {
        viewModelScope.launch {
            locationService.getCurrentLocation()?.let { location ->
                _currentLocation.value = LatLng(location.latitude, location.longitude)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 3) {
            performAutoComplete(query)
        }
    }

    fun updateFilters(filters: LocationSearchService.LocationFilters) {
        _selectedFilters.value = filters
        performSearch()
    }

    private fun performAutoComplete(query: String) {
        viewModelScope.launch {
            _currentLocation.value?.let { location ->
                _searchState.value = SearchState.Loading // Set initial loading state

                when (val result = locationSearchService.getAutocompleteResults(query, location)) {
                    is LocationSearchService.SearchResult.Success -> {
                        _searchState.value = SearchState.AutoComplete(result.data)
                    }
                    is LocationSearchService.SearchResult.Error -> {
                        _searchState.value = SearchState.Error(result.exception.message ?: "Error getting suggestions")
                    }
                    LocationSearchService.SearchResult.Loading -> {
                        _searchState.value = SearchState.Loading
                    }
                }
            } ?: run {
                // Handle case when location is not available
                _searchState.value = SearchState.Error("Location not available. Please enable location services.")
            }
        }
    }

    fun performSearch() {
        viewModelScope.launch {
            _searchState.value = SearchState.Loading
            _currentLocation.value?.let { location ->
                locationRepository.searchNearbyLocations(
                    location = location,
                    radius = 5000.0,
                    filters = _selectedFilters.value
                ).collect { result ->
                    _searchState.value = when (result) {
                        is LocationRepository.LocationResult.Success -> {
                            SearchState.Results(result.data)
                        }
                        is LocationRepository.LocationResult.Error -> {
                            SearchState.Error(result.exception.message ?: "Error searching locations")
                        }
                        is LocationRepository.LocationResult.Loading -> {
                            SearchState.Loading
                        }
                    }
                }
            }
        }
    }

    sealed class SearchState {
        object Initial : SearchState()
        object Loading : SearchState()
        data class AutoComplete(val suggestions: List<String>) : SearchState()
        data class Results(val locations: List<DogFriendlyLocation>) : SearchState()
        data class Error(val message: String) : SearchState()
    }
}