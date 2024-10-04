package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.data.repository.UserRepository
import com.example.pawsomepals.service.LocationService
import com.example.pawsomepals.service.MatchingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SwipeViewModel @Inject constructor(
    private val userRepository: UserRepository,
                                         private val matchingService: MatchingService,
                                         private val locationService: LocationService
) : ViewModel() {
    private val _currentProfile = MutableStateFlow<DogProfile?>(null)
   val currentProfile: StateFlow<DogProfile?> = _currentProfile

    private val _matches = MutableStateFlow<List<DogProfile>>(emptyList())
    val matches: StateFlow<List<DogProfile>> = _matches

    init {
        loadProfiles()
    }

    fun onSwipe(liked: Boolean) {
        viewModelScope.launch {
            currentProfile.value?.let { swipedProfile ->
                if (liked) {
                    checkForMatch(swipedProfile)
                }
                loadNextProfile()
            }
        }
    }

    private fun checkForMatch(swipedProfile: DogProfile) {
        viewModelScope.launch {
            val currentUserDog = userRepository.getCurrentUserDog() ?: return@launch
            if (matchingService.isMatch(currentUserDog, swipedProfile)) {
                _matches.value += swipedProfile
            }
        }
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            loadNextProfile()
        }
    }

    private fun loadNextProfile() {
        viewModelScope.launch {
            val currentLocation = locationService.getCurrentLocation()
            val nextProfile = userRepository.getNextDogProfile()

            nextProfile?.let { profile ->
                _currentProfile.value = profile.copy(
                    latitude = currentLocation?.latitude ?: profile.latitude,
                    longitude = currentLocation?.longitude ?: profile.longitude
                )
            } ?: run {
                _currentProfile.value = null
            }
        }
    }

    class Factory(
        private val userRepository: UserRepository,
        private val matchingService: MatchingService,
        private val locationService: LocationService
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SwipeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SwipeViewModel(userRepository, matchingService, locationService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}