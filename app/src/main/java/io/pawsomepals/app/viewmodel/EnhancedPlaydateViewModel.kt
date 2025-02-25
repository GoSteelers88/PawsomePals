package io.pawsomepals.app.viewmodel

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.LocationState
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.SchedulingState
import io.pawsomepals.app.data.model.SchedulingStep
import io.pawsomepals.app.data.model.TimeSlot
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.data.repository.PlaydateRepository
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.service.location.LocationSearchService
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.utils.toLatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class EnhancedPlaydateViewModel @Inject constructor(
    private val playdateRepository: PlaydateRepository,
    private val locationService: LocationService,
    private val locationSearchService: LocationSearchService,  // Add this

    private val locationMatchingEngine: LocationMatchingEngine,
    private val dogProfileRepository: DogProfileRepository,
    private val matchRepository: MatchRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _availableTimeSlots = MutableStateFlow<List<TimeSlot>>(emptyList())
    val availableTimeSlots = _availableTimeSlots.asStateFlow()

    private val _matchedDogs = MutableStateFlow<List<Match.MatchWithDetails>>(emptyList())
    val matchedDogs: StateFlow<List<Match.MatchWithDetails>> = _matchedDogs.asStateFlow()


    private val _locationState = MutableStateFlow<LocationState>(LocationState.Initial)
    val locationState = _locationState.asStateFlow()

    private val _schedulingState = MutableStateFlow(SchedulingState())
    val schedulingState = _schedulingState.asStateFlow()

    private val _selectedMatch = MutableStateFlow<Match.MatchWithDetails?>(null)
    val selectedMatch = _selectedMatch.asStateFlow()

    private val _userDogs = MutableStateFlow<List<Dog>>(emptyList())
    val userDogs: StateFlow<List<Dog>> = _userDogs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        Log.d("MatchDebug", "ViewModel initialized")
    }

    private suspend fun getOtherDogDetails(match: Match): Dog {
        val currentUserId =
            auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val otherDogId = if (match.dog1Id == currentUserId) match.dog2Id else match.dog1Id
        return dogProfileRepository.getDogById(otherDogId).getOrNull()
            ?: throw IllegalStateException("Could not find other dog")
    }

    private fun formatDistance(distance: Double?): String {
        return distance?.let { "${it.toInt()} km" } ?: "Unknown"
    }

    suspend fun getMatchDetails(matchId: String): StateFlow<Match.MatchWithDetails?>? {
        Log.d("PlaydateDebug", "getMatchDetails called with matchId: $matchId")

        // Handle "new" case - should load all available matches
        if (matchId == "new") {
            Log.d("PlaydateDebug", "New match case detected, loading all matches")
            _schedulingState.value = SchedulingState()
            loadMatchedDogs()
            return _selectedMatch.asStateFlow()  // Return the StateFlow here
        }

        try {
            val matchResult = matchRepository.getMatchById(matchId)
            Log.d("PlaydateDebug", "Match result received: ${matchResult.isSuccess}")

            val match = matchResult.getOrNull()
            Log.d("PlaydateDebug", "Match found: ${match?.id}")

            if (match == null) {
                Log.d("PlaydateDebug", "No match found for id: $matchId")
                return null
            }

            val currentUserId = auth.currentUser?.uid
            Log.d("PlaydateDebug", "Current user id: $currentUserId")

            if (currentUserId == null) {
                Log.d("PlaydateDebug", "No current user found")
                return null
            }

            val otherDogId = if (match.dog1Id == currentUserId) match.dog2Id else match.dog1Id
            Log.d("PlaydateDebug", "Other dog id: $otherDogId")

            val otherDogResult = dogProfileRepository.getDogById(otherDogId)
            Log.d("PlaydateDebug", "Other dog result received: ${otherDogResult.isSuccess}")

            val otherDog = otherDogResult.getOrNull()
            if (otherDog == null) {
                Log.d("PlaydateDebug", "Failed to get other dog details")
                return null
            }

            val matchWithDetails = Match.MatchWithDetails(
                match = match,
                otherDog = otherDog,
                distanceAway = match.locationDistance?.let { "${it.toInt()} km" } ?: "Unknown"
            )

            Log.d("PlaydateDebug", "Created MatchWithDetails successfully")
            _selectedMatch.value = matchWithDetails
            return _selectedMatch.asStateFlow()
        } catch (e: Exception) {
            Log.e("PlaydateDebug", "Error in getMatchDetails", e)
            _schedulingState.value = _schedulingState.value.copy(
                error = "Failed to load match: ${e.message}"
            )
            return null
        }
    }

    fun startScheduling(matchWithDetails: Match.MatchWithDetails) {
        Log.d("MatchDebug", "startScheduling called with match: ${matchWithDetails.match.id}")
        viewModelScope.launch {
            _selectedMatch.value = matchWithDetails
            _schedulingState.value = SchedulingState()
            loadMatchedDogs()  // Should be called here
        }
    }

    private fun loadUserDogs() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                auth.currentUser?.uid?.let { userId ->
                    dogProfileRepository.getDogProfilesByOwner(userId).collect { result ->
                        result.onSuccess { dogs ->
                            _userDogs.value = dogs
                        }.onFailure { error ->
                            _schedulingState.value = _schedulingState.value.copy(
                                error = "Failed to load dogs: ${error.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _schedulingState.value = _schedulingState.value.copy(
                    error = "Failed to load dogs: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectDog(dog: Dog) {
        viewModelScope.launch {
            _schedulingState.value = _schedulingState.value.copy(
                selectedDog = dog,
                currentStep = SchedulingStep.LOCATION
            )

            // Load locations immediately
            loadInitialLocations()

            // Also load match-specific locations if available
            selectedMatch.value?.let { match ->
                searchLocationsForPlaydate(match)
            }
        }
    }
    private fun loadInitialLocations() {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            try {
                val currentLocation = locationService.getCurrentLocation()
                    ?: throw IllegalStateException("Unable to get current location")

                Log.d("LocationDebug", "Loading initial locations near: ${currentLocation.latitude}, ${currentLocation.longitude}")

                // First search for parks
                locationSearchService.searchDogFriendlyLocations(
                    radius = 5.0,
                    filters = LocationSearchService.LocationFilters(
                        venueTypes = setOf(DogFriendlyLocation.VenueType.PUBLIC_PARK)
                    )
                ).collect { result ->
                    when (result) {
                        is LocationSearchService.SearchResult.Success -> {
                            // Filter for dog parks by name/description
                            val dogParks = result.data.filter { location ->
                                location.name.lowercase().contains("dog") ||
                                        location.name.lowercase().contains("k9") ||
                                        location.name.lowercase().contains("k-9") ||
                                        location.name.lowercase().contains("canine") ||
                                        location.placeTypes.any { type ->
                                            type.equals("park", ignoreCase = true)
                                        }
                            }

                            // Search for restaurants and bars
                            locationSearchService.searchDogFriendlyLocations(
                                radius = 2.0,
                                filters = LocationSearchService.LocationFilters(
                                    venueTypes = setOf(
                                        DogFriendlyLocation.VenueType.RESTAURANT,
                                        DogFriendlyLocation.VenueType.BAR,
                                        DogFriendlyLocation.VenueType.BREWERY
                                    )
                                )
                            ).collect { venueResult ->
                                when (venueResult) {
                                    is LocationSearchService.SearchResult.Success -> {
                                        val venues = venueResult.data.filter { venue ->
                                            venue.hasOutdoorSeating ||
                                                    venue.servesDrinks ||
                                                    venue.placeTypes.any { type ->
                                                        type.equals("bar", ignoreCase = true) ||
                                                                type.equals("brewery", ignoreCase = true)
                                                    }
                                        }

                                        val allLocations = dogParks + venues
                                        _locationState.value = LocationState.Success(
                                            nearbyLocations = allLocations,
                                            recommendedLocations = allLocations
                                                .filter { it.rating ?: 0.0 >= 4.0 }
                                                .take(5)
                                        )
                                    }
                                    is LocationSearchService.SearchResult.Error -> {
                                        _locationState.value = LocationState.Success(
                                            nearbyLocations = dogParks,
                                            recommendedLocations = dogParks
                                                .filter { it.rating ?: 0.0 >= 4.0 }
                                                .take(5)
                                        )
                                    }
                                    LocationSearchService.SearchResult.Loading -> {
                                        // Keep current state
                                    }
                                }
                            }
                        }
                        is LocationSearchService.SearchResult.Error -> {
                            _locationState.value = LocationState.Error(
                                result.exception.message ?: "Error loading locations"
                            )
                        }
                        LocationSearchService.SearchResult.Loading -> {
                            _locationState.value = LocationState.Loading
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationDebug", "Error loading initial locations", e)
                _locationState.value = LocationState.Error(e.message ?: "Error loading locations")
            }
        }
    }
    fun cancelScheduling() {
        _selectedMatch.value = null
        _schedulingState.value = SchedulingState()
        _locationState.value = LocationState.Initial
    }

    fun selectLocation(location: DogFriendlyLocation) {
        _schedulingState.value = _schedulingState.value.copy(
            currentStep = SchedulingStep.DATE,
            selectedLocation = location
        )
    }

    fun selectDate(date: LocalDate) {
        _schedulingState.value = _schedulingState.value.copy(
            currentStep = SchedulingStep.TIME,
            selectedDate = date
        )
    }

    fun selectTime(time: LocalTime) {
        _schedulingState.value = _schedulingState.value.copy(
            currentStep = SchedulingStep.REVIEW,
            selectedTime = time
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun createPlaydateRequest() {
        viewModelScope.launch {
            val currentState = _schedulingState.value
            val match = _selectedMatch.value

            if (currentState.isValid() && match != null) {
                try {
                    val dateTime = currentState.selectedDate?.atTime(currentState.selectedTime)
                        ?: throw IllegalStateException("Date/time not selected")

                    val request = playdateRepository.createPlaydateRequest(
                        match = match,
                        location = currentState.selectedLocation!!,
                        dateTime = dateTime
                    )

                    _schedulingState.value = _schedulingState.value.copy(
                        currentStep = SchedulingStep.COMPLETE,
                        request = request
                    )
                } catch (e: Exception) {
                    _schedulingState.value = _schedulingState.value.copy(
                        error = "Failed to create request: ${e.message}"
                    )
                }
            }
        }
    }

    private fun loadMatchedDogs() {
        Log.d("PlaydateDebug", "loadMatchedDogs called")
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentUser = auth.currentUser
                Log.d("PlaydateDebug", "Current user: ${currentUser?.uid}")

                if (currentUser == null) {
                    Log.e("PlaydateDebug", "No authenticated user found")
                    _schedulingState.value = _schedulingState.value.copy(
                        error = "Please sign in to continue"
                    )
                    return@launch
                }

                Log.d("PlaydateDebug", "Starting to fetch matches for user: ${currentUser.uid}")
                matchRepository.getActiveMatches(currentUser.uid).collect { result ->
                    result.onSuccess { matches ->
                        Log.d("PlaydateDebug", "Received ${matches.size} matches from repository")

                        if (matches.isEmpty()) {
                            Log.d("PlaydateDebug", "No active matches found")
                            _matchedDogs.value = emptyList()
                            return@collect
                        }

                        val validMatches = matches.mapNotNull { match ->
                            try {
                                val otherDog = getOtherDogDetails(match)
                                Log.d("PlaydateDebug", "Got details for dog in match ${match.id}")
                                Match.MatchWithDetails(
                                    match = match,
                                    otherDog = otherDog,
                                    distanceAway = formatDistance(match.locationDistance)
                                )
                            } catch (e: Exception) {
                                Log.e(
                                    "PlaydateDebug",
                                    "Failed to get details for match ${match.id}",
                                    e
                                )
                                null
                            }
                        }

                        Log.d(
                            "PlaydateDebug",
                            "Final valid matches for display: ${validMatches.size}"
                        )
                        _matchedDogs.value = validMatches
                    }.onFailure { error ->
                        Log.e("PlaydateDebug", "Error loading matches", error)
                        _schedulingState.value = _schedulingState.value.copy(
                            error = "Failed to load matches: ${error.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaydateDebug", "Error in loadMatchedDogs", e)
                _schedulingState.value = _schedulingState.value.copy(
                    error = "Failed to load matches: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchLocations(query: String) {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            try {
                val currentLocation = locationService.getCurrentLocation()
                    ?: throw IllegalStateException("Unable to get current location")

                locationSearchService.searchNearbyLocations(
                    location = currentLocation.toLatLng(),
                    radius = 10.0 // 10km radius
                ).collect { result ->
                    when (result) {
                        is LocationSearchService.SearchResult.Success -> {
                            _locationState.value = LocationState.Success(
                                nearbyLocations = result.data,
                                recommendedLocations = result.data.filter {
                                    it.rating ?: 0.0 >= 4.0
                                }.take(5)
                            )
                        }
                        is LocationSearchService.SearchResult.Error -> {
                            _locationState.value = LocationState.Error(
                                result.exception.message ?: "Error loading locations"
                            )
                        }
                        LocationSearchService.SearchResult.Loading -> {
                            _locationState.value = LocationState.Loading
                        }
                    }
                }
            } catch (e: Exception) {
                _locationState.value = LocationState.Error(e.message ?: "Error loading locations")
            }
        }
    }

    private suspend fun searchLocationsForPlaydate(match: Match.MatchWithDetails) {
        _locationState.value = LocationState.Loading
        try {
            val currentDog = _schedulingState.value.selectedDog
                ?: throw IllegalStateException("No dog selected")

            // Get suggested locations
            val locationsResult = matchRepository.getSuggestedPlaydateLocations(match.match)

            locationsResult.onSuccess { locations ->
                val optimal = locationMatchingEngine.findOptimalMeetingPoint(
                    currentDog,
                    match.otherDog,
                    locations
                )

                _locationState.value = LocationState.Success(
                    nearbyLocations = locations,
                    recommendedLocations = locations.filter {
                        it.rating ?: 0.0 >= 4.0 && it != optimal
                    }.take(5),
                    optimalLocation = optimal
                )
            }.onFailure { error ->
                _locationState.value = LocationState.Error(
                    error.message ?: "Error finding locations"
                )
            }
        } catch (e: Exception) {
            _locationState.value = LocationState.Error(e.message ?: "Error finding locations")
        }
    }

    fun updateLocationFilters(filters: LocationSearchService.LocationFilters) {
        viewModelScope.launch {
            _locationState.value = LocationState.Loading
            try {
                val currentLocation = locationService.getCurrentLocation()
                    ?: throw IllegalStateException("Unable to get current location")

                locationSearchService.searchDogFriendlyLocations(
                    radius = 10.0,
                    filters = filters
                ).collect { result ->
                    when (result) {
                        is LocationSearchService.SearchResult.Success -> {
                            _locationState.value = LocationState.Success(
                                nearbyLocations = result.data,
                                recommendedLocations = result.data.filter {
                                    it.rating ?: 0.0 >= 4.0
                                }.take(5)
                            )
                        }
                        is LocationSearchService.SearchResult.Error -> {
                            _locationState.value = LocationState.Error(
                                result.exception.message ?: "Error loading locations"
                            )
                        }
                        LocationSearchService.SearchResult.Loading -> {
                            _locationState.value = LocationState.Loading
                        }
                    }
                }
            } catch (e: Exception) {
                _locationState.value = LocationState.Error(e.message ?: "Error loading locations")
            }
        }
    }
}