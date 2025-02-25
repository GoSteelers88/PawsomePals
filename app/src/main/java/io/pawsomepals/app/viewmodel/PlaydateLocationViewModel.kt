package io.pawsomepals.app.viewmodel

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.PlaydateRequest
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.PlaydateRepository
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.service.weather.WeatherInfo
import io.pawsomepals.app.service.weather.WeatherService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

// PlaydateLocationScreen.kt
@HiltViewModel
class PlaydateLocationViewModel @Inject constructor(
    private val locationService: LocationService,
    private val weatherService: WeatherService,
    private val playdateRepository: PlaydateRepository,
    private val dogProfileRepository: DogProfileRepository,
    private val dataManager: DataManager,
    private val auth: FirebaseAuth,
    private val locationSearchService: LocationSearchService,

) : ViewModel() {
    private val _state = MutableStateFlow(PlaydateLocationState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            fetchWeather()
            loadUserDogs()
            searchLocations()
        }
    }

    fun onFilterChange(filters: LocationFilters) {
        _state.update { it.copy(filters = filters) }
        searchLocations()
    }

    @SuppressLint("NewApi")
    fun schedulePlaydate(location: DogFriendlyLocation, dateTime: LocalDateTime, dogId: String) {
        viewModelScope.launch {
            try {
                val request = playdateRepository.createPlaydateRequest(
                    match = state.value.selectedMatch!!,
                    location = location,
                    dateTime = dateTime
                )
                _state.update {
                    it.copy(
                        schedulingState = SchedulingState.Success(request),
                        showSchedulingModal = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        schedulingState = SchedulingState.Error(e.message ?: "Failed to schedule")
                    )
                }
            }
        }
    }

    private suspend fun fetchWeather() {
        val location = locationService.getCurrentLocation() ?: return
        val weather = weatherService.getWeather(location.latitude, location.longitude)
        _state.update { it.copy(weather = weather) }
    }

    private fun searchLocations() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                locationSearchService.searchDogFriendlyLocations(
                    radius = state.value.filters.distance.toDouble(),
                    filters = LocationSearchService.LocationFilters(
                        venueTypes = state.value.filters.venueTypes,
                        requiredAmenities = state.value.filters.amenities,
                        outdoorOnly = state.value.filters.outdoorOnly
                    )
                ).collect { result ->
                    when (result) {
                        is LocationSearchService.SearchResult.Success -> {
                            _state.update {
                                it.copy(
                                    locations = result.data,
                                    isLoading = false
                                )
                            }
                        }

                        is LocationSearchService.SearchResult.Error -> {
                            _state.update {
                                it.copy(
                                    error = result.exception.message,
                                    isLoading = false
                                )
                            }
                        }

                        is LocationSearchService.SearchResult.Loading -> {
                            _state.update { it.copy(isLoading = true) }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    private suspend fun loadUserDogs() {
        try {
            val userId = auth.currentUser?.uid ?: return
            val dogs = dataManager.getUserDogs(userId)
            _state.update { it.copy(userDogs = dogs) }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
        }
    }
    fun toggleFilters() {
        _state.update { it.copy(showFilters = !it.showFilters) }
    }

    fun toggleSchedulingModal() {
        _state.update { it.copy(showSchedulingModal = !it.showSchedulingModal) }
    }

    fun selectLocation(location: DogFriendlyLocation) {
        _state.update { it.copy(selectedLocation = location) }
    }


    data class PlaydateLocationState(
        val locations: List<DogFriendlyLocation> = emptyList(),
        val filters: LocationFilters = LocationFilters(),
        val weather: WeatherInfo? = null,
        val selectedMatch: Match.MatchWithDetails? = null,
        val userDogs: List<Dog> = emptyList(),
        val schedulingState: SchedulingState = SchedulingState.Initial,
        val showSchedulingModal: Boolean = false,
        val showFilters: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val selectedLocation: DogFriendlyLocation? = null  // Add this line

    )

    data class LocationFilters(
        val venueTypes: Set<DogFriendlyLocation.VenueType> = emptySet(),
        val distance: Float = 5f,
        val amenities: Set<DogFriendlyLocation.Amenity> = emptySet(),
        val outdoorOnly: Boolean = false
    )

    sealed class SchedulingState {
        object Initial : SchedulingState()
        object Loading : SchedulingState()
        data class Success(val request: PlaydateRequest) : SchedulingState()
        data class Error(val message: String) : SchedulingState()
    }
}