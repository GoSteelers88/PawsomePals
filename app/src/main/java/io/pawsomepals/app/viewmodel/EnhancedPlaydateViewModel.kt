package io.pawsomepals.app.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.SchedulingState
import io.pawsomepals.app.data.model.SchedulingStep
import io.pawsomepals.app.data.model.TimeSlot
import io.pawsomepals.app.service.location.EnhancedLocationService
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.location.LocationSuggestionService
import io.pawsomepals.app.utils.LocationMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class EnhancedPlaydateViewModel @Inject constructor(
    private val enhancedLocationService: EnhancedLocationService,
    private val locationService: LocationService,
    private val locationSearchService: LocationSearchService,
    private val locationSuggestionService: LocationSuggestionService,
    private val locationMapper: LocationMapper,


) : ViewModel() {
    sealed class LocationState {
        object Initial : LocationState()
        object Loading : LocationState()
        data class Success(
            val nearbyLocations: List<DogFriendlyLocation>,
            val recommendedLocations: List<DogFriendlyLocation>
        ) : LocationState()
        data class AutocompleteResults(val predictions: List<AutocompletePrediction>) : LocationState()
        data class PlacesFound(val places: List<Place>) : LocationState()
        data class Error(val message: String) : LocationState()
    }


    private val _locationState = MutableStateFlow<LocationState>(LocationState.Initial)
    val locationState = _locationState.asStateFlow()

    private val _schedulingState = MutableStateFlow(SchedulingState())
    val schedulingState = _schedulingState.asStateFlow()

    private val _selectedMatch = MutableStateFlow<Match.MatchWithDetails?>(null)
    val selectedMatch = _selectedMatch.asStateFlow()

    private val _nearbyLocations = MutableStateFlow<List<DogFriendlyLocation>>(emptyList())
    val nearbyLocations = _nearbyLocations.asStateFlow()

    private val _recommendedLocations = MutableStateFlow<List<DogFriendlyLocation>>(emptyList())
    val recommendedLocations = _recommendedLocations.asStateFlow()

    private val _availableTimeSlots = MutableStateFlow<List<TimeSlot>>(emptyList())
    val availableTimeSlots = _availableTimeSlots.asStateFlow()



    fun startScheduling(match: Match.MatchWithDetails) {
        viewModelScope.launch {
            _selectedMatch.value = match
            _schedulingState.value = SchedulingState()
            searchLocationsForPlaydate(match)
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun createPlaydateRequest() {
        viewModelScope.launch {
            val currentState = _schedulingState.value
            val match = _selectedMatch.value

            if (currentState.selectedLocation != null &&
                currentState.selectedDate != null &&
                currentState.selectedTime != null &&
                match != null
            ) {
                try {
                    // Create request directly
                    val timeslot = currentState.selectedDate
                        .atTime(currentState.selectedTime)
                        .toEpochSecond(ZoneOffset.UTC)

                    // Use the location search service to get place details
                    val locationResult = locationSearchService.getLocationDetails(
                        currentState.selectedLocation.placeId
                    )

                    when (locationResult) {
                        is LocationSearchService.SearchResult.Success -> {
                            // Update state with success
                            _schedulingState.value = _schedulingState.value.copy(
                                currentStep = SchedulingStep.COMPLETE
                            )
                            cancelScheduling()
                        }
                        is LocationSearchService.SearchResult.Error -> {
                            _locationState.value = LocationState.Error(
                                locationResult.exception.message ?: "Failed to confirm location"
                            )
                        }
                        else -> {
                            _locationState.value = LocationState.Error("Unexpected error occurred")
                        }
                    }
                } catch (e: Exception) {
                    _locationState.value = LocationState.Error(e.message ?: "Failed to create playdate request")
                }
            }
        }
    }

    private suspend fun searchLocationsForPlaydate(match: Match.MatchWithDetails) {
        _locationState.value = LocationState.Loading
        try {
            // Get location of the other dog only since we can get current dog location from LocationService
            val currentLocation = locationService.getLastKnownLocation()?.let { location ->
                com.google.android.gms.maps.model.LatLng(location.latitude, location.longitude)
            }
            val otherDogLocation = locationService.getDogLocation(match.otherDog.id)

            if (currentLocation != null && otherDogLocation != null) {
                enhancedLocationService.searchLocationsForPlaydate(currentLocation, otherDogLocation)
                    .collect { state ->
                        when (state) {
                            is EnhancedLocationService.LocationSearchState.Success -> {
                                _locationState.value = LocationState.Success(
                                    nearbyLocations = state.nearbyLocations,
                                    recommendedLocations = state.recommendedLocations
                                )
                                _nearbyLocations.value = state.nearbyLocations
                                _recommendedLocations.value = state.recommendedLocations
                            }
                            is EnhancedLocationService.LocationSearchState.Error -> {
                                _locationState.value = LocationState.Error(state.error.message ?: "Error finding locations")
                            }
                            EnhancedLocationService.LocationSearchState.Loading -> {
                                _locationState.value = LocationState.Loading
                            }
                            EnhancedLocationService.LocationSearchState.Initial -> {
                                // Handle initial state if needed
                            }
                        }
                    }
            } else {
                _locationState.value = LocationState.Error("Unable to get user locations")
            }
        } catch (e: Exception) {
            _locationState.value = LocationState.Error(e.message ?: "Error occurred while searching locations")
        }
    }
    fun selectLocation(location: DogFriendlyLocation) {
        _schedulingState.value = _schedulingState.value.copy(
            currentStep = SchedulingStep.DATE,
            selectedLocation = location
        )
        fetchAvailableTimeSlots(location)
    }

    fun selectDate(date: LocalDate) {
        _schedulingState.value = _schedulingState.value.copy(
            currentStep = SchedulingStep.TIME,
            selectedDate = date
        )
        updateAvailableTimeSlots(date)
    }

    fun selectTime(time: LocalTime) {
        _schedulingState.value = _schedulingState.value.copy(
            currentStep = SchedulingStep.REVIEW,
            selectedTime = time
        )
    }

    private fun fetchAvailableTimeSlots(location: DogFriendlyLocation) {
        viewModelScope.launch {
            // Implement time slot fetching logic
            // Consider location opening hours, existing bookings, etc.
            val slots = generateTimeSlots(location)
            _availableTimeSlots.value = slots
        }
    }

    private fun updateAvailableTimeSlots(date: LocalDate) {
        viewModelScope.launch {
            val currentSlots = _availableTimeSlots.value
            // Update availability based on date
            // Consider weather, daylight hours, etc.
        }
    }

    private fun generateTimeSlots(location: DogFriendlyLocation): List<TimeSlot> {
        // Implementation for generating time slots based on location
        return emptyList() // Placeholder
    }

      fun cancelScheduling() {
        _selectedMatch.value = null
        _schedulingState.value = SchedulingState()
        _availableTimeSlots.value = emptyList()
    }
}