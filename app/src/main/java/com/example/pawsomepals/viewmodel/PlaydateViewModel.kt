package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.DogFriendlyLocation
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.data.model.PlaydateRequest
import com.example.pawsomepals.data.model.Timeslot
import com.example.pawsomepals.data.repository.PlaydateRepository
import com.example.pawsomepals.data.repository.UserRepository
import com.example.pawsomepals.notification.NotificationManager
import com.example.pawsomepals.service.LocationSuggestionService
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import com.google.android.libraries.places.api.model.Place
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject


@HiltViewModel
class PlaydateViewModel @Inject constructor(
    private val playdateRepository: PlaydateRepository,
    private val userRepository: UserRepository,
    private val notificationManager: NotificationManager,
    private val locationSuggestionService: LocationSuggestionService
) : ViewModel() {

    private val _availableTimeslots = MutableStateFlow<List<Timeslot>>(emptyList())
    val availableTimeslots: StateFlow<List<Timeslot>> = _availableTimeslots

    private val _playdateRequests = MutableStateFlow<List<PlaydateRequest>>(emptyList())
    val playdateRequests: StateFlow<List<PlaydateRequest>> = _playdateRequests

    private val _requestStatus = MutableStateFlow<RequestStatus>(RequestStatus.Idle)
    val requestStatus: StateFlow<RequestStatus> = _requestStatus

    private val _receiverProfile = MutableStateFlow<DogProfile?>(null)
    val receiverProfile: StateFlow<DogProfile?> = _receiverProfile

    private val _playdatesForMonth = MutableStateFlow<List<PlaydateRequest>>(emptyList())
    val playdatesForMonth: StateFlow<List<PlaydateRequest>> = _playdatesForMonth

    private val _suggestedLocations = MutableStateFlow<List<DogFriendlyLocation>>(emptyList())
    val suggestedLocations: StateFlow<List<DogFriendlyLocation>> = _suggestedLocations

    init {
        loadAvailableTimeslots()
        loadPlaydateRequests()
    }

    private fun loadAvailableTimeslots() {
        viewModelScope.launch {
            playdateRepository.getAvailableTimeslots().collect {
                _availableTimeslots.value = it
            }
        }
    }



    private fun loadPlaydateRequests() {
        viewModelScope.launch {
            playdateRepository.getPlaydateRequests().collect {
                _playdateRequests.value = it
            }
        }
    }
    fun loadReceiverProfile(profileId: String) {
        viewModelScope.launch {
            _receiverProfile.value = userRepository.getDogProfileById(profileId)
        }
    }

    fun sendPlaydateRequest(receiverId: String, selectedTimeslots: List<LocalDate>) {
        viewModelScope.launch {
            _requestStatus.value = RequestStatus.Loading
            try {
                val request = playdateRepository.createPlaydateRequest(receiverId, selectedTimeslots)
                if (request != null) {
                    _requestStatus.value = RequestStatus.Success
                    loadPlaydateRequests()
                    notificationManager.showPlaydateRequestNotification(request.id, receiverProfile.value?.name ?: "Someone")
                } else {
                    _requestStatus.value = RequestStatus.Error("Failed to create playdate request")
                }
            } catch (e: Exception) {
                _requestStatus.value = RequestStatus.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    fun loadPlaydatesForMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val startDate = yearMonth.atDay(1)
            val endDate = yearMonth.atEndOfMonth()
            _playdatesForMonth.value = playdateRepository.getPlaydatesForDateRange(startDate, endDate)
        }
    }

    fun acceptPlaydateRequest(requestId: Int) {
        viewModelScope.launch {
            playdateRepository.updatePlaydateRequestStatus(requestId, com.example.pawsomepals.data.model.RequestStatus.ACCEPTED)
            loadPlaydateRequests()
        }
    }

    fun declinePlaydateRequest(requestId: Int) {
        viewModelScope.launch {
            playdateRepository.updatePlaydateRequestStatus(requestId, com.example.pawsomepals.data.model.RequestStatus.DECLINED)
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


    class Factory(
        private val playdateRepository: PlaydateRepository,
        private val userRepository: UserRepository,
        private val notificationManager: NotificationManager,
        private val locationSuggestionService: LocationSuggestionService
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlaydateViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PlaydateViewModel(playdateRepository, userRepository, notificationManager, locationSuggestionService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed class RequestStatus {
    object Idle : RequestStatus()
    object Loading : RequestStatus()
    object Success : RequestStatus()
    data class Error(val message: String) : RequestStatus()
}