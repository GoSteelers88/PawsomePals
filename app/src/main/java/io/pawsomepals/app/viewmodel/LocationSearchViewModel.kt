package io.pawsomepals.app.viewmodel

import android.location.Location
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.repository.LocationRepository
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.service.location.LocationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationSearchViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val locationService: LocationService,
    private val locationSearchService: LocationSearchService
) : ViewModel() {
    companion object {
        private const val TAG = "LocationSearchVM"
    }
    private val _snackbarHostState = SnackbarHostState()
    val snackbarHostState: SnackbarHostState = _snackbarHostState


    private val _searchState = MutableStateFlow<SearchState>(SearchState.Initial)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilters = MutableStateFlow(LocationSearchService.LocationFilters())
    val selectedFilters: StateFlow<LocationSearchService.LocationFilters> = _selectedFilters.asStateFlow()


    init {
        // Start with an initial search when location is available
        viewModelScope.launch {
            currentLocation.filterNotNull().collectLatest {
                performSearch()
            }
        }
    }
    fun initializeLocation(location: Location) {
        _currentLocation.value = LatLng(location.latitude, location.longitude)
    }

    fun testPlacesApi() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== Starting Places API Test from ViewModel ===")

                // Test 1: Location Service
                Log.d(TAG, "Testing location service...")
                val location = locationService.getCurrentLocation()
                if (location != null) {
                    Log.d(TAG, "Got current location: lat=${location.latitude}, lng=${location.longitude}")
                    _currentLocation.value = LatLng(location.latitude, location.longitude)
                } else {
                    Log.e(TAG, "Failed to get current location")
                }

                // Test 2: Places API Connection
                Log.d(TAG, "Testing Places API connection...")
                locationSearchService.testPlacesApiBasic()

                // Test 3: Simple Search
                Log.d(TAG, "Testing search...")
                val testFilters = LocationSearchService.LocationFilters(
                    venueTypes = setOf(DogFriendlyLocation.VenueType.DOG_PARK)
                )

                locationSearchService.searchDogFriendlyLocations(
                    radius = 5000.0,  // Using default radius
                    filters = testFilters
                ).collect { result ->
                    when (result) {
                        is LocationSearchService.SearchResult.Success -> {
                            Log.d(TAG, "Search test successful! Found ${result.data.size} locations")
                            result.data.take(3).forEach { place ->
                                Log.d(TAG, "Found place: ${place.name} (${place.placeTypes.joinToString()})")
                            }
                        }
                        is LocationSearchService.SearchResult.Error -> {
                            Log.e(TAG, "Search test failed", result.exception)
                            Log.e(TAG, "Error details: ${result.exception.message}")
                        }
                        LocationSearchService.SearchResult.Loading -> {
                            Log.d(TAG, "Search test loading...")
                        }
                    }
                }

                Log.d(TAG, "=== Places API Test Completed ===")
            } catch (e: Exception) {
                Log.e(TAG, "Places API test failed", e)
                Log.e(TAG, "Stack trace:", e)
            }
        }
    }

    fun initializeLocation() {
        viewModelScope.launch {
            Log.d(TAG, "Initializing location...")
            _searchState.value = SearchState.Loading

            locationService.getCurrentLocation()?.let { location ->
                Log.d(TAG, "Location initialized: lat=${location.latitude}, lng=${location.longitude}")
                _currentLocation.value = LatLng(location.latitude, location.longitude)
                performSearch()
            } ?: run {
                Log.e(TAG, "Failed to initialize location")
                _searchState.value = SearchState.Error("Could not get location")
            }
        }
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
        // Optional: Add debounce for automatic search
        viewModelScope.launch {
            delay(500) // Debounce timeout
            performSearch()
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
                        _searchState.value = SearchState.Error(
                            result.exception.message ?: "Error getting suggestions"
                        )
                    }
                    LocationSearchService.SearchResult.Loading -> {
                        _searchState.value = SearchState.Loading
                    }
                }
            } ?: run {
                // Handle case when location is not available
                _searchState.value =
                    SearchState.Error("Location not available. Please enable location services.")
            }
        }
    }

    fun performSearch() {
        viewModelScope.launch {
            try {
                _searchState.value = SearchState.Loading

                val currentLoc = currentLocation.value
                    ?: throw IllegalStateException("Location not available")

                locationSearchService.searchDogFriendlyLocations(
                    radius = 5000.0, // 5km radius
                    filters = selectedFilters.value
                ).collect { result ->
                    when (result) {
                        is LocationSearchService.SearchResult.Success -> {
                            _searchState.value = SearchState.Results(result.data)
                        }
                        is LocationSearchService.SearchResult.Error -> {
                            _searchState.value = SearchState.Error(
                                result.exception.message ?: "Search failed"
                            )
                        }
                        is LocationSearchService.SearchResult.Loading -> {
                            _searchState.value = SearchState.Loading
                        }
                    }
                }
            } catch (e: Exception) {
                _searchState.value = SearchState.Error(e.message ?: "Search failed")
            }
        }
    }
    fun saveLocation(location: DogFriendlyLocation) {
        viewModelScope.launch {
            try {
                when (val result = locationRepository.saveLocation(location)) {
                    is LocationRepository.LocationResult.Success -> {
                        _snackbarHostState.showSnackbar(
                            message = "${location.name} saved to favorites"
                        )
                    }
                    is LocationRepository.LocationResult.Error -> {
                        _snackbarHostState.showSnackbar(
                            message = "Failed to save location: ${result.exception.message}"
                        )
                    }
                    LocationRepository.LocationResult.Loading -> {
                        // Handle loading state if needed
                    }
                }
            } catch (e: Exception) {
                _snackbarHostState.showSnackbar(
                    message = "Error saving location"
                )
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