package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.data.repository.DogProfileRepository
import com.example.pawsomepals.data.repository.MatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SwipingViewModel @Inject constructor(
    private val dogProfileRepository: DogProfileRepository,
    private val matchRepository: MatchRepository
) : ViewModel() {

    private val _currentProfile = MutableStateFlow<DogProfile?>(null)
    val currentProfile: StateFlow<DogProfile?> = _currentProfile

    private val _matches = MutableStateFlow<List<DogProfile>>(emptyList())
    val matches: StateFlow<List<DogProfile>> = _matches

    private val profiles = mutableListOf<DogProfile>()
    private var currentIndex = 0

    init {
        loadProfiles()
        loadMatches()
    }

    private fun loadProfiles() {
        viewModelScope.launch {
            try {
                profiles.clear()
                profiles.addAll(dogProfileRepository.getSwipingProfiles())
                showNextProfile()
            } catch (e: Exception) {
                // Handle error (e.g., show error message)
            }
        }
    }

    private fun loadMatches() {
        viewModelScope.launch {
            try {
                _matches.value = matchRepository.getUserMatches()
            } catch (e: Exception) {
                // Handle error (e.g., show error message)
            }
        }
    }

    private fun showNextProfile() {
        _currentProfile.value = if (currentIndex < profiles.size) {
            profiles[currentIndex++]
        } else {
            null // No more profiles to show
        }
    }

    fun onSwipe(liked: Boolean) {
        viewModelScope.launch {
            currentProfile.value?.let { profile ->
                if (liked) {
                    matchRepository.addLike(profile.id)
                    checkForMatch(profile)
                } else {
                    matchRepository.addDislike(profile.id)
                }
            }
            showNextProfile()
        }
    }

    private suspend fun checkForMatch(profile: DogProfile) {
        if (matchRepository.isMatch(profile.id)) {
            // It's a match!
            _matches.value += profile
            // You could also trigger a notification or UI event here
        }
    }

    fun refreshProfiles() {
        loadProfiles()
    }

    fun getMatchById(matchId: String): DogProfile? {
        return _matches.value.find { it.id == matchId }
    }
}