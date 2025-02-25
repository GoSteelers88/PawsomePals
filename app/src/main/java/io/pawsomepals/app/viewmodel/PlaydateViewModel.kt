package io.pawsomepals.app.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.managers.CalendarManager
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.DogFriendlyLocation
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.MatchType
import io.pawsomepals.app.data.model.Playdate
import io.pawsomepals.app.data.model.PlaydateRequest
import io.pawsomepals.app.data.model.PlaydateStatus
import io.pawsomepals.app.data.model.PlaydateTab
import io.pawsomepals.app.data.model.PlaydateWithDetails
import io.pawsomepals.app.data.model.RequestStatus
import io.pawsomepals.app.data.model.TimeSlot
import io.pawsomepals.app.data.model.TimeSlotUi
import io.pawsomepals.app.data.model.TimeslotEntity
import io.pawsomepals.app.data.model.UserAvailability
import io.pawsomepals.app.data.repository.AuthRepository
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.ITimeSlotRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.data.repository.PlaydateRepository
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.notification.NotificationManager
import io.pawsomepals.app.service.CalendarService
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.service.location.LocationSuggestionService
import io.pawsomepals.app.utils.TimeFormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import java.time.DayOfWeek as JavaDayOfWeek


@RequiresApi(Build.VERSION_CODES.O)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")


private fun Playdate.toCalendarEvent(): CalendarManager.CalendarEvent {
    return CalendarManager.CalendarEvent(
        id = this.id,
        title = "Dog Playdate",
        description = "Scheduled playdate",
        startTime = this.scheduledTime,
        endTime = this.scheduledTime + (60 * 60 * 1000), // Add 1 hour
        location = this.location,
        participants = listOf(this.dog1Id, this.dog2Id),
        status = this.status,
        reminders = emptyList()
    )
}
private fun CalendarManager.CalendarEvent.toPlaydate(): Playdate {
    return Playdate(
        id = this.id,
        scheduledTime = this.startTime,
        status = this.status,
        // Set other necessary fields based on your Playdate model
        dog1Id = this.participants.firstOrNull() ?: "",
        dog2Id = this.participants.getOrNull(1) ?: "",
        createdBy = this.participants.firstOrNull() ?: ""
    )
}

    data class PlaydateStats(
val icon: ImageVector,
val label: String,
val value: String,
val iconTint: androidx.compose.ui.graphics.Color
)

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class PlaydateViewModel @Inject constructor(
    private val playdateRepository: PlaydateRepository,
    private val userRepository: UserRepository,
    private val notificationManager: NotificationManager,
    private val locationSuggestionService: LocationSuggestionService,
    private val calendarService: CalendarService,
    private val timeSlotRepository: ITimeSlotRepository,  // Changed to interface
    private val authRepository: AuthRepository,
    private val workManager: WorkManager,
    private val auth: FirebaseAuth,
    private val dogProfileRepository: DogProfileRepository,
    private val matchRepository: MatchRepository,
    private val locationMatchingEngine: LocationMatchingEngine  // Add this dependency
// Add this
// Add this

) : ViewModel() {
    // In PlaydateViewModel.kt
    data class PlaydateUiStat(
        val icon: ImageVector,
        val label: String,
        val value: String,
        val colorHex: String // Use hex string instead of Compose Color
    )
    private val _currentMatch = MutableStateFlow<Match.MatchWithDetails?>(null)
    val currentMatch: StateFlow<Match.MatchWithDetails?> = _currentMatch.asStateFlow()
    private var lastCreatedMatch: Match.MatchWithDetails? = null


    fun createPlaydateRequest() {
        viewModelScope.launch {
            try {
                // Get required data from uiState
                val selectedMatch = uiState.value.selectedMatch
                    ?: throw IllegalStateException("No match selected")
                val selectedLocation = uiState.value.selectedLocation
                    ?: throw IllegalStateException("No location selected")
                val selectedDateTime = uiState.value.selectedDateTime
                    ?: throw IllegalStateException("No date/time selected")

                // Create the playdate request
                val result = playdateRepository.createPlaydateRequest(
                    match = selectedMatch,  // Pass MatchWithDetails
                    location = selectedLocation,
                    dateTime = selectedDateTime
                )

                // Update status and UI
                _requestStatus.value = RequestStatus.ACCEPTED
                loadPlaydateRequests()

                // Send notification
                notificationManager.showPlaydateRequestNotification(
                    result.id,
                    receiverProfile.value?.name ?: "Someone"
                )
            } catch (e: Exception) {
                _requestStatus.value = RequestStatus.DECLINED
                _uiState.update { it.copy(error = "Failed to send request: ${e.message}") }
            }
        }
    }

    fun getFirstUnscheduledMatch(): StateFlow<String?> {
        val matchId = MutableStateFlow<String?>(null)
        viewModelScope.launch {
            val match = uiState.value.unscheduledMatches.firstOrNull()
            matchId.value = match?.match?.id
        }
        return matchId.asStateFlow()
    }
    fun getMatchDetails(matchId: String): StateFlow<Match.MatchWithDetails?> {
        viewModelScope.launch {
            try {
                if (matchId == "new") {
                    // Only create new match if we haven't created one yet
                    if (_currentMatch.value == null && lastCreatedMatch == null) {
                        val currentUser = userRepository.getCurrentUser()
                        val currentUserDog = userRepository.getCurrentUserDog()

                        if (currentUser != null && currentUserDog != null) {
                            val timestamp = System.currentTimeMillis()
                            val expiryTimestamp = timestamp + (7 * 24 * 60 * 60 * 1000) // 7 days

                            val initialMatch = Match.MatchWithDetails(
                                match = Match(
                                    id = UUID.randomUUID().toString(),  // Generate proper UUID
                                    user1Id = currentUser.id,
                                    user2Id = "",
                                    dog1Id = currentUserDog.id,
                                    dog2Id = "",
                                    compatibilityScore = 0.0,
                                    status = MatchStatus.PENDING,
                                    matchReasons = emptyList(),
                                    matchType = MatchType.NORMAL,
                                    locationDistance = null,
                                    initiatorDogId = currentUserDog.id,
                                    timestamp = timestamp,
                                    lastInteractionTimestamp = timestamp,
                                    expiryTimestamp = expiryTimestamp,
                                    preferredPlaydateLocation = null,
                                    preferredPlaydateTime = null,
                                    chatId = null,
                                    chatCreatedAt = null,
                                    geoHash = "7zzzzzzzzz",  // Default geoHash
                                    lastUpdated = timestamp,
                                    isArchived = false,
                                    isHidden = false,
                                    hasUnreadMessages = false
                                ),
                                otherDog = currentUserDog,
                                distanceAway = "0 km"
                            )

                            lastCreatedMatch = initialMatch
                            _currentMatch.value = initialMatch
                            Log.d("PlaydateViewModel", "Created new match with dog: ${currentUserDog.id}")
                        } else {
                            Log.e("PlaydateViewModel", "User or dog not found. User: $currentUser, Dog: $currentUserDog")
                            _uiState.update { it.copy(error = "Please complete your dog's profile first") }
                        }
                    } else if (lastCreatedMatch != null) {
                        _currentMatch.value = lastCreatedMatch
                        Log.d("PlaydateViewModel", "Reusing existing match")
                    }
                } else {
                    // Existing match loading code
                    val matchResult = matchRepository.getMatchById(matchId)
                    val match = matchResult.getOrNull() ?: return@launch

                    val currentDogId = getCurrentDogId()
                    val otherDog = dogProfileRepository.getDogById(match.getOtherDogId(currentDogId))
                        .getOrNull() ?: return@launch

                    val matchWithDetails = Match.MatchWithDetails(
                        match = match,
                        otherDog = otherDog,
                        distanceAway = match.getDistanceString()
                    )
                    _currentMatch.value = matchWithDetails
                }
            } catch (e: Exception) {
                Log.e("PlaydateViewModel", "Error creating match", e)
                _uiState.update { it.copy(error = "Error: ${e.message}") }
            }
        }
        return currentMatch
    }
    private fun Match.getOtherDogId(currentDogId: String): String {
        return if (dog1Id == currentDogId) dog2Id else dog1Id
    }

    private fun Match.getDistanceString(): String {
        return locationDistance?.let { "${it.roundToInt()} km" } ?: "Unknown"
    }



    private suspend fun getCurrentDogId(): String {
        return userRepository.getCurrentUserDog()?.id
            ?: throw IllegalStateException("No current dog selected")
    }

    private suspend fun getCurrentUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User not logged in")
    }
    fun suggestMeetingLocations(match: Match) {
        viewModelScope.launch {
            try {
                // First get the Dog objects from their IDs
                val dog1Result = dogProfileRepository.getDogById(match.dog1Id)
                val dog2Result = dogProfileRepository.getDogById(match.dog2Id)

                // Unwrap the results and ensure both dogs were found
                val (dog1, dog2) = when {
                    dog1Result.isSuccess && dog2Result.isSuccess -> {
                        Pair(
                            dog1Result.getOrNull(),
                            dog2Result.getOrNull()
                        )
                    }
                    else -> {
                        _uiState.update { it.copy(
                            error = "Could not load dog profiles",
                            isLoading = false
                        )}
                        return@launch
                    }
                }

                // Make sure neither dog is null
                if (dog1 == null || dog2 == null) {
                    _uiState.update { it.copy(
                        error = "One or both dogs not found",
                        isLoading = false
                    )}
                    return@launch
                }

                // Now we can call findCommonAreas with the actual Dog objects
                val commonAreas = locationMatchingEngine.findCommonAreas(dog1, dog2)

                _uiState.update { it.copy(
                    suggestedLocations = commonAreas,
                    isLoading = false
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Failed to load suggested locations: ${e.message}",
                    isLoading = false
                )}
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun validateTimeSlot(
        startTime: LocalTime,
        endTime: LocalTime,
        dayOfWeek: DayOfWeek
    ): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val existingSlots = timeSlotRepository.getTimeSlotsForDay(userId, dayOfWeek.value)

        return !existingSlots.any { slot ->
            val slotStart = slot.startTime
            val slotEnd = slot.endTime

            (startTime >= slotStart && startTime < slotEnd) ||
                    (endTime > slotStart && endTime <= slotEnd) ||
                    (startTime <= slotStart && endTime >= slotEnd)
        }
    }

    fun manageTimeSlot(
        action: TimeSlotAction,
        timeSlot: TimeSlotUi? = null,
        dayOfWeek: DayOfWeek? = null,
        startTime: LocalTime? = null,
        endTime: LocalTime? = null
    ) {
        viewModelScope.launch {
            try {
                val userId =
                    auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

                when (action) {
                    is TimeSlotAction.Add -> {
                        if (dayOfWeek == null || startTime == null || endTime == null) {
                            throw IllegalArgumentException("Required parameters missing for adding time slot")
                        }

                        if (validateTimeSlot(startTime, endTime, dayOfWeek)) {
                            val newTimeSlot = TimeSlot(
                                id = UUID.randomUUID().toString(),
                                userId = userId,
                                dayOfWeek = dayOfWeek,
                                startTime = startTime,
                                endTime = endTime,
                                isAvailable = true
                            )
                            timeSlotRepository.updateTimeSlots(listOf(newTimeSlot))
                            loadAvailableTimeslots()
                        } else {
                            _uiState.update { it.copy(error = "Time slot overlaps with existing availability") }
                        }
                    }

                    is TimeSlotAction.Remove -> {
                        timeSlot?.let {
                            timeSlotRepository.deleteTimeSlot(it.id.toInt())
                            loadAvailableTimeslots()
                        }
                    }

                    is TimeSlotAction.Update -> {
                        if (timeSlot == null) {
                            throw IllegalArgumentException("Time slot required for update")
                        }
                        val domainTimeSlot = timeSlot.toDomainModel(userId)
                        timeSlotRepository.updateTimeSlots(listOf(domainTimeSlot))
                        loadAvailableTimeslots()
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to manage time slot: ${e.message}") }
            }
        }
    }

    private fun loadUnscheduledMatches() {
        viewModelScope.launch {
            try {
                val currentDogId = getCurrentDogId()
                playdateRepository.getUnscheduledMatches().collect { matches ->
                    val matchesWithDetails = matches.mapNotNull { match ->
                        try {
                            Match.MatchWithDetails(
                                match = match,
                                otherDog = match.getOtherDog(currentDogId, dogProfileRepository),
                                distanceAway = match.locationDistance?.let {
                                    "${it.roundToInt()} km"
                                } ?: "Unknown"
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    _uiState.update { it.copy(
                        unscheduledMatches = matchesWithDetails,
                        isLoading = false
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    error = "Failed to load matches: ${e.message}",
                    isLoading = false
                ) }
            }
        }
    }

    private fun updateStats() {
        viewModelScope.launch {
            try {
                val stats: PlaydateRepository.PlaydateStats = playdateRepository.getPlaydateStats()
                val uiStats = listOf(
                    PlaydateUiStat(
                        icon = Icons.Default.CalendarMonth,
                        label = "This Week",
                        value = "${stats.weeklyPlaydates} Playdates",
                        colorHex = "#1976D2"
                    ),
                    PlaydateUiStat(
                        icon = Icons.Default.AccessTime,
                        label = "Available",
                        value = "${stats.availableHours} Hours",
                        colorHex = "#22C55E"
                    ),
                    PlaydateUiStat(
                        icon = Icons.Default.CheckCircle,
                        label = "Completed",
                        value = "${stats.completedPlaydates} Meets",
                        colorHex = "#8B5CF6"
                    )
                )
                _uiState.update { it.copy(stats = uiStats) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load stats: ${e.message}") }
            }
        }
    }



    // Update PlaydateUiState in PlaydateViewModel.kt
    data class PlaydateUiState(
        val upcomingPlaydates: List<PlaydateWithDetails> = emptyList(),
        val availableTimeslots: List<TimeSlot> = emptyList(),
        val playdateRequests: List<PlaydateRequest> = emptyList(),
        val selectedDateTime: LocalDateTime? = null,  // Combined date and time
        val isReviewDialogVisible: Boolean = false,
        val selectedDate: LocalDate? = null,  // Add if missing
        val availableTimeSlots: List<TimeSlot> = emptyList(), // Change from availableTimeslots
        val selectedPlaydate: PlaydateWithDetails? = null, // Added this field
        val suggestedLocations: List<DogFriendlyLocation> = emptyList(),
        val savedLocations: List<DogFriendlyLocation> = emptyList(),
        val currentLocation: LatLng? = null,
        val selectedLocation: DogFriendlyLocation? = null,
        val isLocationSelectorOpen: Boolean = false,
        val isLoading: Boolean = false,
        val error: String? = null,
        val calendarEvents: List<CalendarManager.CalendarEvent> = emptyList(),
        val isCalendarSynced: Boolean = false,
        val selectedTimeSlots: Set<String> = emptySet(),
        val stats: List<PlaydateUiStat> = emptyList(),
        val currentTab: PlaydateTab = PlaydateTab.UPCOMING,
        val isUpdateSuccessful: Boolean = false,
        // In PlaydateUiState data class, change:
        val unscheduledMatches: List<Match.MatchWithDetails> = emptyList(), // Changed from List<Match>
        val isScheduling: Boolean = false,
        val selectedMatch: Match.MatchWithDetails? = null,  // Changed from Match?
        val incomingRequests: List<PlaydateRequest> = emptyList(),
        val outgoingRequests: List<PlaydateRequest> = emptyList(),
        val confirmedPlaydates: List<PlaydateWithDetails> = emptyList()

    )

    // Remove the sealed class RequestStatus completely since we'll use the enum
    private val _uiState = MutableStateFlow(PlaydateUiState())
    val uiState: StateFlow<PlaydateUiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate = _selectedDate.asStateFlow()

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }
    // Need to restructure:
    fun startScheduling(matchWithDetails: Match.MatchWithDetails) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isScheduling = true,
                selectedMatch = matchWithDetails
            )}
            loadUnscheduledMatches()  // Use existing function in PlaydateViewModel
        }
    }

    fun cancelScheduling() {
        viewModelScope.launch {
            _uiState.update { it.copy(
                isScheduling = false,
                selectedMatch = null
            )}
        }
    }

    fun acceptRequest(request: PlaydateRequest) {
        viewModelScope.launch {
            try {
                playdateRepository.updatePlaydateRequestStatus(request.id, RequestStatus.ACCEPTED)
                loadPlaydateRequests()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to accept request: ${e.message}") }
            }
        }
    }

    fun declineRequest(request: PlaydateRequest) {
        viewModelScope.launch {
            try {
                playdateRepository.updatePlaydateRequestStatus(request.id, RequestStatus.DECLINED)
                loadPlaydateRequests()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to decline request: ${e.message}") }
            }
        }
    }

    fun proposeChanges(request: PlaydateRequest) {
        viewModelScope.launch {
            try {
                val currentDogId = getCurrentDogId()
                val match = request.toMatch()
                // Convert Match to MatchWithDetails
                val matchWithDetails = Match.MatchWithDetails(
                    match = match,
                    otherDog = match.getOtherDog(currentDogId, dogProfileRepository),
                    distanceAway = "Unknown" // Or calculate if you have the data
                )
                _uiState.update { it.copy(
                    isScheduling = true,
                    selectedMatch = matchWithDetails
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to propose changes: ${e.message}") }
            }
        }
    }

    fun showReviewDialog() {
        _uiState.update { it.copy(isReviewDialogVisible = true) }
    }

    fun hideReviewDialog() {
        _uiState.update { it.copy(isReviewDialogVisible = false) }
    }

    fun onDateTimeSelected(dateTime: LocalDateTime) {
        _uiState.update { it.copy(
            selectedDateTime = dateTime,
            isReviewDialogVisible = true  // Show review dialog when date/time selected
        )}
    }


    private fun loadConfirmedPlaydates() {
        viewModelScope.launch {
            try {
                playdateRepository.getPlaydatesByStatus(PlaydateStatus.CONFIRMED)
                    .collect { playdates ->
                        _uiState.update { it.copy(confirmedPlaydates = playdates) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load playdates: ${e.message}") }
            }
        }
    }




    private val _availabilityState =
        MutableStateFlow<Map<DayOfWeek, List<UserAvailability>>>(emptyMap())
    val availabilityState: StateFlow<Map<DayOfWeek, List<UserAvailability>>> =
        _availabilityState.asStateFlow()


    private val _isCalendarSynced = MutableStateFlow(false)
    val isCalendarSynced: StateFlow<Boolean> = _isCalendarSynced.asStateFlow()

    private val _userAvailability = MutableStateFlow<Map<LocalDate, Set<LocalTime>>>(emptyMap())
    val userAvailability: StateFlow<Map<LocalDate, Set<LocalTime>>> =
        _userAvailability.asStateFlow()


    private val _availableTimeslots = MutableStateFlow<List<TimeslotEntity>>(emptyList())
    val availableTimeslots: StateFlow<List<TimeslotEntity>> = _availableTimeslots

    private val _playdateRequests = MutableStateFlow<List<PlaydateRequest>>(emptyList())
    val playdateRequests: StateFlow<List<PlaydateRequest>> = _playdateRequests

    private val _requestStatus = MutableStateFlow<RequestStatus>(RequestStatus.PENDING)
    val requestStatus: StateFlow<RequestStatus> = _requestStatus

    private val _receiverProfile = MutableStateFlow<Dog?>(null)
    val receiverProfile: StateFlow<Dog?> = _receiverProfile

    private val _playdatesForMonth = MutableStateFlow<List<PlaydateRequest>>(emptyList())
    val playdatesForMonth: StateFlow<List<PlaydateRequest>> = _playdatesForMonth

    private val _suggestedLocations = MutableStateFlow<List<DogFriendlyLocation>>(emptyList())
    val suggestedLocations: StateFlow<List<DogFriendlyLocation>> = _suggestedLocations


    init {
        viewModelScope.launch {
            loadConfirmedPlaydates()
            loadPlaydateRequests()
            loadConfirmedPlaydates()
            loadUnscheduledMatches()
            loadAvailableTimeslots()
            checkCalendarSyncStatus()
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun saveTimeSlot(startTime: String, endTime: String, dayOfWeek: Int) {
        viewModelScope.launch {
            try {
                val timeslotEntity = TimeslotEntity(
                    startTime = startTime,
                    endTime = endTime,
                    dayOfWeek = dayOfWeek,
                    date = LocalDate.now().toEpochDay()
                )
                playdateRepository.createTimeslot(startTime, endTime, dayOfWeek)

                if (uiState.value.isCalendarSynced) {
                    syncTimeSlotWithCalendar(startTime, endTime, dayOfWeek)
                }

                // Refresh available timeslots after saving
                loadAvailableTimeslots()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save time slot: ${e.message}") }
            }
        }
    }

    fun loadSavedLocations() {
        viewModelScope.launch {
            try {
                userRepository.getCurrentUserId()?.let { userId ->
                    val savedLocations = locationSuggestionService.getSavedLocations(userId)
                    _uiState.update { it.copy(savedLocations = savedLocations) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load saved locations: ${e.message}") }
            }
        }
    }

    fun selectTimeSlot(slot: TimeSlotUi) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedTimeSlots = it.selectedTimeSlots + slot.startTime
                )
            }
        }
    }

    fun updateAvailability(timeSlots: List<TimeSlotUi>) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val userId =
                    auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
                val domainTimeSlots = timeSlots.map { it.toDomainModel(userId) }

                timeSlotRepository.updateTimeSlots(domainTimeSlots)

                if (uiState.value.isCalendarSynced) {
                    domainTimeSlots.forEach { slot ->
                        syncTimeSlotWithCalendar(
                            TimeFormatUtils.formatLocalTime(slot.startTime),
                            TimeFormatUtils.formatLocalTime(slot.endTime),
                            slot.dayOfWeek.value
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        availableTimeSlots = domainTimeSlots,
                        isLoading = false,
                        isUpdateSuccessful = true
                    )
                }

                delay(2000)
                _uiState.update { it.copy(isUpdateSuccessful = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to update availability: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleCalendarSync() {
        viewModelScope.launch {
            toggleCalendarSync(!uiState.value.isCalendarSynced)
        }
    }

    fun saveLocation(location: DogFriendlyLocation) {
        viewModelScope.launch {
            try {
                userRepository.getCurrentUserId()?.let { userId ->
                    locationSuggestionService.saveLocation(userId, location)
                    loadSavedLocations() // Refresh the saved locations
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save location: ${e.message}") }
            }
        }
    }

    fun selectPlaydate(playdateId: String) {
        viewModelScope.launch {
            try {
                val playdate = playdateRepository.getPlaydateById(playdateId)
                _uiState.update { currentState ->
                    currentState.copy(
                        selectedPlaydate = playdate,
                        isLocationSelectorOpen = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to select playdate: ${e.message}") }
            }
        }
    }

    fun checkCalendarSyncStatus() {
        viewModelScope.launch {
            val isAuthenticated =
                calendarService.checkAuthStatus() is CalendarManager.CalendarAuthState.Authenticated
            _uiState.update { it.copy(isCalendarSynced = isAuthenticated) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun TimeslotEntity.toTimeSlotUi(): TimeSlotUi {
        var existingPlaydate: PlaydateWithDetails? = null
        viewModelScope.launch {
            if (playdateId.isNotEmpty()) {
                existingPlaydate = playdateRepository.getPlaydateById(playdateId)
            }
        }
        return TimeSlotUi(
            id = id.toString(),
            dayOfWeek = DayOfWeek.of(dayOfWeek),
            startTime = startTime,
            endTime = endTime,
            date = date,
            isAvailable = isAvailable,
            existingPlaydate = existingPlaydate
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun syncAvailabilityWithCalendar(
        date: LocalDate,
        timeSlots: Set<java.time.LocalTime>
    ) {
        timeSlots.forEach { time ->
            val calendarEvent = CalendarManager.CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = "Available for Playdate",
                description = "Regular availability slot",
                startTime = date.atTime(time).toEpochSecond(ZoneOffset.UTC) * 1000,
                endTime = date.atTime(time).plusHours(1).toEpochSecond(ZoneOffset.UTC) * 1000,
                location = null,
                participants = emptyList(),
                status = PlaydateStatus.NONE,
                reminders = emptyList()
            )
            val playdate = Playdate(
                id = calendarEvent.id,
                scheduledTime = calendarEvent.startTime,
                status = calendarEvent.status,
                location = calendarEvent.location ?: "",
                dog1Id = calendarEvent.participants.firstOrNull() ?: "",
                dog2Id = calendarEvent.participants.getOrNull(1) ?: ""
            )
            calendarService.addPlaydateEvent(playdate)
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun stringToLocalTime(timeString: String): LocalTime {
        return LocalTime.parse(timeString)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun convertGoogleDayOfWeek(dayOfWeek: DayOfWeek): JavaDayOfWeek {
        return when (dayOfWeek) {
            DayOfWeek.SUNDAY -> JavaDayOfWeek.SUNDAY
            DayOfWeek.MONDAY -> JavaDayOfWeek.MONDAY
            DayOfWeek.TUESDAY -> JavaDayOfWeek.TUESDAY
            DayOfWeek.WEDNESDAY -> JavaDayOfWeek.WEDNESDAY
            DayOfWeek.THURSDAY -> JavaDayOfWeek.THURSDAY
            DayOfWeek.FRIDAY -> JavaDayOfWeek.FRIDAY
            DayOfWeek.SATURDAY -> JavaDayOfWeek.SATURDAY
        }
    }

    private suspend fun syncTimeSlotWithCalendar(
        startTime: String,
        endTime: String,
        dayOfWeek: Int,
        date: Long? = null  // Optional parameter with default value
    ) {
        try {
            val startTimeMillis = convertTimeStringToMillis(startTime)
            val endTimeMillis = convertTimeStringToMillis(endTime)

            val calendarEvent = CalendarManager.CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = "Available for Playdate",
                description = "Regular availability slot",
                startTime = startTimeMillis,
                endTime = endTimeMillis,
                location = null,
                participants = emptyList(),
                status = PlaydateStatus.NONE,
                reminders = emptyList()
            )

            val playdate = Playdate(
                id = calendarEvent.id,
                scheduledTime = calendarEvent.startTime,
                status = calendarEvent.status,
                location = "",
                dog1Id = "",
                dog2Id = "",
                createdBy = ""
            )

            calendarService.addPlaydateEvent(playdate)
        } catch (e: Exception) {
            Log.e("PlaydateViewModel", "Error syncing timeslot with calendar", e)
            throw e
        }
    }


    private suspend fun syncAllTimeslotsToCalendar() {
        uiState.value.availableTimeslots.forEach { timeslot ->
            val dayOfWeek = when (timeslot.dayOfWeek) {
                DayOfWeek.SUNDAY -> 1
                DayOfWeek.MONDAY -> 2
                DayOfWeek.TUESDAY -> 3
                DayOfWeek.WEDNESDAY -> 4
                DayOfWeek.THURSDAY -> 5
                DayOfWeek.FRIDAY -> 6
                DayOfWeek.SATURDAY -> 7
            }

            syncTimeSlotWithCalendar(
                timeslot.startTime.toString(),
                timeslot.endTime.toString(),
                dayOfWeek
            )
        }
    }

    private suspend fun removeAllCalendarEvents() {
        val now = System.currentTimeMillis()
        val oneMonthLater = now + (30 * 24 * 60 * 60 * 1000)

        calendarService.observeCalendarEvents(now, oneMonthLater)
            .collect { result ->
                when (result) {
                    is CalendarService.CalendarResult.Success<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val events = (result.data as? List<CalendarManager.CalendarEvent>)
                        events?.forEach { event ->
                            if (event.title == "Available for Playdate") {
                                calendarService.deleteCalendarEvent(event.id)
                            }
                        }
                    }

                    else -> {}
                }
            }
    }

    private suspend fun syncTimeSlotWithCalendar(
        startTime: String,
        endTime: String,
        dayOfWeek: Int
    ) {
        try {
            val startTimeMillis = convertTimeStringToMillis(startTime)
            val endTimeMillis = convertTimeStringToMillis(endTime)

            val calendarEvent = CalendarManager.CalendarEvent(
                id = UUID.randomUUID().toString(),
                title = "Available for Playdate",
                description = "Regular availability slot",
                startTime = startTimeMillis,
                endTime = endTimeMillis,
                location = null,
                participants = emptyList(),
                status = PlaydateStatus.NONE,
                reminders = emptyList()
            )

            val playdate = Playdate(
                id = calendarEvent.id,
                scheduledTime = calendarEvent.startTime,
                status = calendarEvent.status,
                location = "",
                dog1Id = "",
                dog2Id = "",
                createdBy = ""
            )

            calendarService.addPlaydateEvent(playdate)
        } catch (e: Exception) {
            Log.e("PlaydateViewModel", "Error syncing timeslot with calendar", e)
            throw e
        }
    }

    private fun convertToCalendarEvent(playdate: Playdate): CalendarManager.CalendarEvent {
        return CalendarManager.CalendarEvent(
            id = playdate.id,
            title = "Dog Playdate",
            description = "Scheduled playdate",
            startTime = playdate.scheduledTime,
            endTime = playdate.scheduledTime + (60 * 60 * 1000),
            location = playdate.location,
            participants = listOf(playdate.dog1Id, playdate.dog2Id),
            status = playdate.status,
            reminders = emptyList()
        )
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun saveUserAvailability(timeslotEntities: List<TimeslotEntity>) {
        viewModelScope.launch {
            try {
                playdateRepository.saveUserAvailability(
                    dayOfWeek = timeslotEntities.first().dayOfWeek,
                    startTime = timeslotEntities.first().startTime,
                    endTime = timeslotEntities.first().endTime
                )
                loadAvailableTimeslots()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save availability: ${e.message}") }
            }
        }
    }



    private fun toggleCalendarSync(enable: Boolean) {
        viewModelScope.launch {
            try {
                if (enable) {
                    when (val result = calendarService.beginCalendarAuth()) {
                        is CalendarService.CalendarResult.Success -> {
                            _uiState.update { it.copy(isCalendarSynced = true) }
                            syncAllTimeslotsToCalendar()
                        }

                        is CalendarService.CalendarResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    error = "Failed to enable calendar sync: ${result.exception.message}",
                                    isCalendarSynced = false
                                )
                            }
                        }

                        else -> {}
                    }
                } else {
                    removeAllCalendarEvents()
                    _uiState.update { it.copy(isCalendarSynced = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = "Calendar sync error: ${e.message}",
                        isCalendarSynced = false
                    )
                }
            }
        }
    }

    fun selectLocation(location: DogFriendlyLocation) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    selectedLocation = location,
                    isLocationSelectorOpen = false
                )
            }
        }
    }


    private suspend fun syncAvailabilityToCalendar(availability: UserAvailability) {
        val event = CalendarManager.CalendarEvent(
            id = "availability_${availability.id}",
            title = "Available for Playdate",
            description = "Regular availability for dog playdates",
            startTime = availability.getStartTimeMillis(),
            endTime = availability.getEndTimeMillis(),
            location = null,
            participants = emptyList(),
            status = PlaydateStatus.NONE,
            reminders = emptyList()
        )
        calendarService.addPlaydateEvent(event.toPlaydate())
    }

    private fun UserAvailability.getStartTimeMillis(): Long {
        return (startHour * 3600 + startMinute * 60) * 1000L
    }

    private fun UserAvailability.getEndTimeMillis(): Long {
        return (endHour * 3600 + endMinute * 60) * 1000L
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun syncAllAvailabilityToCalendar() {
        _userAvailability.value.forEach { (currentDate, timeSlots) ->
            syncAvailabilityWithCalendar(currentDate, timeSlots)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadUserAvailability() {
        viewModelScope.launch {
            userRepository.getCurrentUserId()?.let { userId ->
                playdateRepository.getUserAvailabilityForWeek(userId)
                    .collect { availability: Map<DayOfWeek, List<UserAvailability>> ->
                        _availabilityState.value = availability
                    }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun addAvailabilitySlot(
        dayOfWeek: DayOfWeek,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        isRecurring: Boolean = true,
        specificDate: Long? = null
    ) {
        viewModelScope.launch {
            userRepository.getCurrentUserId()?.let { userId ->
                val javaDayOfWeek = convertGoogleDayOfWeek(dayOfWeek)
                val startTime = String.format("%02d:%02d", startHour, startMinute)
                val endTime = String.format("%02d:%02d", endHour, endMinute)

                playdateRepository.createTimeslot(
                    startTime,
                    endTime,
                    javaDayOfWeek.value
                )

                if (calendarService.checkAuthStatus() is CalendarManager.CalendarAuthState.Authenticated) {
                    val today = LocalDate.now()
                    val nextOccurrence = today.with(javaDayOfWeek)
                    syncAvailabilityWithCalendar(
                        nextOccurrence,
                        setOf(LocalTime.of(startHour, startMinute))
                    )
                }
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun convertDayOfWeek(day: DayOfWeek): java.time.DayOfWeek {
        return when (day) {
            DayOfWeek.SUNDAY -> java.time.DayOfWeek.SUNDAY
            DayOfWeek.MONDAY -> java.time.DayOfWeek.MONDAY
            DayOfWeek.TUESDAY -> java.time.DayOfWeek.TUESDAY
            DayOfWeek.WEDNESDAY -> java.time.DayOfWeek.WEDNESDAY
            DayOfWeek.THURSDAY -> java.time.DayOfWeek.THURSDAY
            DayOfWeek.FRIDAY -> java.time.DayOfWeek.FRIDAY
            DayOfWeek.SATURDAY -> java.time.DayOfWeek.SATURDAY
        }
    }


    private suspend fun removeAllAvailabilityFromCalendar() {
        // Get all availability events and delete them
        val now = System.currentTimeMillis()
        val oneMonthLater = now + (30 * 24 * 60 * 60 * 1000)

        calendarService.observeCalendarEvents(now, oneMonthLater)
            .collect { result ->
                when (result) {
                    is CalendarService.CalendarResult.Success<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val events = (result.data as? List<CalendarManager.CalendarEvent>)
                        events?.forEach { event ->
                            if (event.title == "Available for Playdate") {
                                calendarService.deleteCalendarEvent(event.id)
                            }
                        }
                    }

                    else -> {}
                }
            }
    }




    private fun PlaydateRepository.PlaydateStats.toUiStats(): List<PlaydateUiStat> {
        return listOf(
            PlaydateUiStat(
                icon = Icons.Default.CalendarMonth,
                label = "This Week",
                value = "$weeklyPlaydates Playdates",
                colorHex = "#1976D2"
            ),
            PlaydateUiStat(
                icon = Icons.Default.AccessTime,
                label = "Available",
                value = "$availableHours Hours",
                colorHex = "#22C55E"
            ),
            PlaydateUiStat(
                icon = Icons.Default.CheckCircle,
                label = "Completed",
                value = "$completedPlaydates Meets",
                colorHex = "#8B5CF6"
            )
        )
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun loadSavedAvailability() {
        viewModelScope.launch {
            try {
                userRepository.getCurrentUserId()?.let { userId ->
                    playdateRepository.getAvailableTimeslotsForDay(
                        dayOfWeek = LocalDate.now().dayOfWeek.value,
                        userId = userId
                    ).let { timeslots ->
                        val availabilityMap = timeslots
                            .groupBy(
                                { LocalDate.ofEpochDay(it.date) },
                                { LocalTime.parse(it.startTime) }
                            )
                            .mapValues { it.value.toSet() }
                        _userAvailability.value = availabilityMap
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load availability: ${e.message}") }
            }
        }
    }


    private suspend fun syncPlaydateToCalendar(playdateRequest: PlaydateRequest) {
        val startTime =
            playdateRequest.suggestedTimeslots.first() * 1000 // Convert epoch days to millis
        val calendarEvent = CalendarManager.CalendarEvent(
            id = playdateRequest.id,
            title = "Dog Playdate",
            description = "Scheduled playdate",
            startTime = startTime,
            endTime = startTime + (60 * 60 * 1000), // Add 1 hour
            location = null,
            participants = listOf(playdateRequest.requesterId, playdateRequest.receiverId),
            status = PlaydateStatus.PENDING,
            reminders = emptyList()
        )
        val playdate = Playdate(
            id = calendarEvent.id,
            scheduledTime = calendarEvent.startTime,
            status = calendarEvent.status,
            location = calendarEvent.location ?: "",
            dog1Id = calendarEvent.participants.firstOrNull() ?: "",
            dog2Id = calendarEvent.participants.getOrNull(1) ?: ""
        )
        calendarService.addPlaydateEvent(playdate)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun java.time.LocalTime.toLocalTimeString(): String {
        return String.format("%02d:%02d", this.hour, this.minute)
    }

    fun getOutputFileUri(context: Context): Uri {
        return try {
            val timeStamp =
                java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                    .format(java.util.Date())
            val imageFileName = "PROFILE_${timeStamp}_"
            val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val tempFile = java.io.File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
            )
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error creating image file", e)
            throw e
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun List<TimeslotEntity>.toTimeSlotUiList(): List<TimeSlotUi> {
        return map { it.toTimeSlotUi() }
    }



    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadAvailableTimeslots() {
        viewModelScope.launch {
            try {
                auth.currentUser?.uid?.let { userId ->
                    val timeslots = playdateRepository.getAvailableTimeslotsForDay(
                        dayOfWeek = LocalDate.now().dayOfWeek.value,
                        userId = userId
                    )

                    _availableTimeslots.value = timeslots

                    // Convert to UI models and update UI state
                    val uiTimeslots = timeslots.toTimeSlotUiList()
                    _uiState.update { currentState ->
                        currentState.copy(
                            availableTimeSlots = uiTimeslots.map { ui ->
                                TimeSlot(
                                    id = ui.id,
                                    userId = userId,
                                    dayOfWeek = ui.dayOfWeek,
                                    startTime = TimeFormatUtils.parseTime(ui.startTime),
                                    endTime = TimeFormatUtils.parseTime(ui.endTime),
                                    date = ui.date,
                                    isAvailable = ui.isAvailable,
                                    existingPlaydate = ui.existingPlaydate?.playdate?.id
                                )
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load timeslots: ${e.message}") }
            }
        }
    }


    private fun loadPlaydateRequests() {
        viewModelScope.launch {
            try {
                val currentUserId = getCurrentUserId() ?: return@launch
                playdateRepository.getPlaydateRequests().collect { requests ->
                    val incoming = requests.filter { it.receiverId == currentUserId }
                    val outgoing = requests.filter { it.requesterId == currentUserId }
                    _uiState.update { it.copy(
                        incomingRequests = incoming,
                        outgoingRequests = outgoing
                    )}
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load requests: ${e.message}") }
            }
        }
    }

    fun loadReceiverProfile(profileId: String) {
        viewModelScope.launch {
            _receiverProfile.value = userRepository.getDogProfileById(profileId)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun formatLocalTime(time: LocalTime): String {
        return TimeFormatUtils.formatLocalTime(time)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun parseTimeString(timeString: String): LocalTime {
        return TimeFormatUtils.parseTime(timeString)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun selectDate(date: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedDate = date, isLoading = true) }

            try {
                val startOfDay = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000
                val endOfDay = date.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC) * 1000

                calendarService.observeCalendarEvents(startOfDay, endOfDay)
                    .collect { result ->
                        when (result) {
                            is CalendarService.CalendarResult.Success<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val events = (result.data as? List<CalendarManager.CalendarEvent>)
                                    ?: emptyList()
                                _uiState.update {
                                    it.copy(
                                        calendarEvents = events,
                                        isLoading = false,
                                        error = null
                                    )
                                }
                            }

                            is CalendarService.CalendarResult.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        error = "Failed to load calendar: ${result.exception.message}"
                                    )
                                }
                            }

                            is CalendarService.CalendarResult.Loading -> {
                                _uiState.update { it.copy(isLoading = true) }
                            }
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load calendar events: ${e.message}"
                    )
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun sendPlaydateRequest(
        match: Match.MatchWithDetails,
        location: DogFriendlyLocation,
        dateTime: LocalDateTime
    ) {
        try {
            val request = playdateRepository.createPlaydateRequest(match, location, dateTime)
            _requestStatus.value = RequestStatus.ACCEPTED
            loadPlaydateRequests()
            notificationManager.showPlaydateRequestNotification(
                request.id,
                receiverProfile.value?.name ?: "Someone"
            )
        } catch (e: Exception) {
            _requestStatus.value = RequestStatus.DECLINED
            _uiState.update { it.copy(error = "Failed to send request: ${e.message}") }
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadPlaydatesForMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val startDate = yearMonth.atDay(1)
            val endDate = yearMonth.atEndOfMonth()
            playdateRepository.getPlaydatesForDateRange(startDate, endDate)
                .also { playdates ->
                    _playdatesForMonth.value = playdates.map { playdate ->
                        PlaydateRequest(
                            id = playdate.id,
                            requesterId = playdate.dog1Id,
                            receiverId = playdate.dog2Id,
                            suggestedTimeslots = listOf(playdate.scheduledTime),
                            status = RequestStatus.PENDING
                        )
                    }
                }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun getPlaydatesForDate(date: LocalDate): List<PlaydateRequest> {
        return _playdateRequests.value.filter { request ->
            request.suggestedTimeslots.any { it == date.toEpochDay() }
        }
    }

    fun acceptPlaydateRequest(requestId: String) {
        viewModelScope.launch {
            playdateRepository.updatePlaydateRequestStatus(
                requestId,
                RequestStatus.ACCEPTED
            )
            loadPlaydateRequests()
        }
    }

    // In PlaydateViewModel.kt
    // In PlaydateViewModel.kt
    fun getPlaydatesForDateRange(startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            try {
                val playdates = playdateRepository.getPlaydatesForDateRange(startDate, endDate)
                    .map { playdate ->
                        PlaydateRequest(
                            id = playdate.id,
                            matchId = playdate.matchId,
                            requesterId = playdate.dog1Id,
                            receiverId = playdate.dog2Id,
                            suggestedTimeslots = listOf(playdate.scheduledTime),
                            selectedLocationId = playdate.location,
                            status = RequestStatus.PENDING
                        )
                    }
                _uiState.update { it.copy(
                    playdateRequests = playdates,
                    isLoading = false,
                    error = null
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = "Failed to load playdates: ${e.message}"
                ) }
            }
        }
    }
    // In PlaydateViewModel.kt
    fun Match.MatchWithDetails.toPlaydateRequest(): PlaydateRequest {
        return PlaydateRequest(
            id = UUID.randomUUID().toString(),
            matchId = match.id,
            requesterId = match.user1Id,
            receiverId = match.user2Id,
            status = RequestStatus.PENDING
        )
    }

    private fun PlaydateRequest.toPlaydate(): Playdate {
        return Playdate(
            id = UUID.randomUUID().toString(),
            matchId = matchId,
            location = selectedLocationId,
            dog1Id = requesterId,
            dog2Id = receiverId,
            status = PlaydateStatus.CONFIRMED,
            scheduledTime = suggestedTimeslots.firstOrNull() ?: 0L,
            createdBy = requesterId
        )
    }

    fun declinePlaydateRequest(requestId: String) {
        viewModelScope.launch {
            playdateRepository.updatePlaydateRequestStatus(
                requestId,
                RequestStatus.DECLINED
            )
            loadPlaydateRequests()
        }
    }

    fun loadSuggestedLocations(user1Id: String, user2Id: String) {
        viewModelScope.launch {
            try {
                val user1 = userRepository.getUserById(user1Id)
                val user2 = userRepository.getUserById(user2Id)

                if (user1 != null && user2 != null) {
                    val locations = locationSuggestionService.getDogFriendlyLocations(
                        LatLng(user1.latitude ?: 0.0, user1.longitude ?: 0.0),
                        LatLng(user2.latitude ?: 0.0, user2.longitude ?: 0.0)
                    )
                    _suggestedLocations.value = locations.mapNotNull { place ->
                        DogFriendlyLocation.fromPlace(place)
                    }
                } else {
                    _suggestedLocations.value = emptyList()
                }
            } catch (e: Exception) {
                _suggestedLocations.value = emptyList()
            }
        }
    }

    private fun convertTimeStringToMillis(timeString: String): Long {
        return try {
            // Assuming time format is "HH:mm" (24-hour format)
            val (hours, minutes) = timeString.split(":")
                .map { it.toInt() }

            // Convert to milliseconds
            ((hours * 60 * 60) + (minutes * 60)) * 1000L
        } catch (e: Exception) {
            Log.e("PlaydateViewModel", "Error converting time string: $timeString", e)
            0L  // Return 0 if parsing fails
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // And add a helper function for the reverse conversion
    private fun convertMillisToTimeString(timeInMillis: Long): String {
        return try {
            val totalSeconds = timeInMillis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60

            String.format("%02d:%02d", hours, minutes)
        } catch (e: Exception) {
            Log.e("PlaydateViewModel", "Error converting millis to time string: $timeInMillis", e)
            "00:00"  // Return default if conversion fails
        }
    }

    fun refreshStats() {
        updateStats()
    }
    fun clearMatch() {
        lastCreatedMatch = null
        _currentMatch.value = null
    }

    sealed class TimeSlotAction {
        object Add : TimeSlotAction()
        object Remove : TimeSlotAction()
        object Update : TimeSlotAction()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun TimeSlotUi.toDomainModel(userId: String) = TimeSlot(
        id = id,
        userId = userId,
        dayOfWeek = dayOfWeek,
        startTime = TimeFormatUtils.parseTime(startTime),
        endTime = TimeFormatUtils.parseTime(endTime),
        date = date,
        isAvailable = isAvailable,
        existingPlaydate = existingPlaydate?.playdate?.id
    )
}


