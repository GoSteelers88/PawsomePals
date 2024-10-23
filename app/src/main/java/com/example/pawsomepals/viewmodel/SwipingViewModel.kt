package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.Dog
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

    private val _currentProfile = MutableStateFlow<Dog?>(null)
    val currentProfile: StateFlow<Dog?> = _currentProfile

    private val _matches = MutableStateFlow<List<Dog>>(emptyList())
    val matches: StateFlow<List<Dog>> = _matches

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val profiles = mutableListOf<Dog>()
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
                _errorMessage.value = "Failed to load profiles: ${e.localizedMessage}"
            }
        }
    }

    private fun loadMatches() {
        viewModelScope.launch {
            try {
                _matches.value = matchRepository.getUserMatches()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load matches: ${e.localizedMessage}"
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

    private suspend fun checkForMatch(profile: Dog) {
        if (matchRepository.isMatch(profile.id)) {
            // It's a match!
            _matches.value = _matches.value + profile
            // You could also trigger a notification or UI event here
        }
    }

    fun refreshProfiles() {
        loadProfiles()
    }

    fun getMatchById(matchId: String): Dog? {
        return _matches.value.find { it.id == matchId }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}