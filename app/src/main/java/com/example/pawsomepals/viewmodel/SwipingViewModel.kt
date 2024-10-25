package com.example.pawsomepals.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.model.ResultWrapper
import com.example.pawsomepals.data.repository.DogProfileRepository
import com.example.pawsomepals.data.repository.MatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SwipingViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val dogProfileRepository: DogProfileRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SwipingViewModel"
    }

    // UI State
    private val _uiState = MutableStateFlow<SwipingUIState>(SwipingUIState.Initial)
    val uiState: StateFlow<SwipingUIState> = _uiState.asStateFlow()

    // Profiles State
    private val _availableProfiles = MutableStateFlow<List<Dog>>(emptyList())
    val availableProfiles: StateFlow<List<Dog>> = _availableProfiles.asStateFlow()

    private val _currentProfile = MutableStateFlow<Dog?>(null)
    val currentProfile: StateFlow<Dog?> = _currentProfile.asStateFlow()

    private val _matches = MutableStateFlow<List<Dog>>(emptyList())
    val matches: StateFlow<List<Dog>> = _matches.asStateFlow()

    // Queue of profiles
    private val profileQueue = mutableListOf<Dog>()
    private var currentIndex = 0

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            loadSwipingProfiles()
            loadMatches()
        }
    }

    private fun loadSwipingProfiles() {
        viewModelScope.launch {
            try {
                _uiState.value = SwipingUIState.Loading
                dogProfileRepository.getSwipingProfiles().fold(
                    onSuccess = { dogs ->
                        dogs?.let {
                            profileQueue.clear()
                            profileQueue.addAll(it)
                            _availableProfiles.value = it
                            showNextProfile()
                            _uiState.value = SwipingUIState.Success
                        } ?: run {
                            _uiState.value = SwipingUIState.Error("No profiles available")
                        }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Failed to load profiles", exception)
                        _uiState.value = SwipingUIState.Error("Failed to load profiles: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in loadSwipingProfiles", e)
                _uiState.value = SwipingUIState.Error("Unexpected error loading profiles")
            }
        }
    }

    fun onSwipe(liked: Boolean) {
        viewModelScope.launch {
            _currentProfile.value?.let { profile ->
                handleSwipe(profile.id, liked)
            }
        }
    }

    private fun loadMatches() {
        viewModelScope.launch {
            try {
                matchRepository.getRecentMatches().collect { matchedDogs ->
                    _matches.value = matchedDogs
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading matches", e)
                _uiState.value = SwipingUIState.Error("Failed to load matches")
            }
        }
    }

    fun onSwipeLeft(dogId: String) {
        viewModelScope.launch {
            handleSwipe(dogId, liked = false)
        }
    }

    fun onSwipeRight(dogId: String) {
        viewModelScope.launch {
            handleSwipe(dogId, liked = true)
        }
    }

    private suspend fun handleSwipe(dogId: String, liked: Boolean) {
        try {
            _uiState.value = SwipingUIState.Loading

            val result = if (liked) {
                matchRepository.addLike(dogId)
            } else {
                matchRepository.addDislike(dogId)
            }

            when (result) {
                is ResultWrapper.Success<Unit> -> {
                    if (liked) {
                        checkForMatch(dogId)
                    }
                    showNextProfile()
                    _uiState.value = SwipingUIState.Success
                }
                is ResultWrapper.Error -> {
                    _uiState.value = SwipingUIState.Error("Failed to process swipe: ${result.exception.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleSwipe", e)
            _uiState.value = SwipingUIState.Error("Unexpected error processing swipe")
        }
    }

    private suspend fun checkForMatch(dogId: String) {
        try {
            when (val result = matchRepository.isMatch(dogId)) {
                is ResultWrapper.Success<Boolean> -> {
                    if (result.data) {
                        dogProfileRepository.getDogProfile(dogId).collect { dog ->
                            dog?.let {
                                _matches.value = _matches.value + it
                                _uiState.value = SwipingUIState.Match(it)
                            }
                        }
                    }
                }
                is ResultWrapper.Error -> {
                    Log.e(TAG, "Error checking match", result.exception)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkForMatch", e)
        }
    }

    private fun showNextProfile() {
        if (currentIndex < profileQueue.size) {
            _currentProfile.value = profileQueue[currentIndex++]
        } else {
            _currentProfile.value = null
            _uiState.value = SwipingUIState.NoMoreProfiles
        }
    }

    fun refresh() {
        viewModelScope.launch {
            currentIndex = 0
            profileQueue.clear()
            _currentProfile.value = null
            loadInitialData()
        }
    }

    fun dismissError() {
        if (_uiState.value is SwipingUIState.Error) {
            _uiState.value = SwipingUIState.Success
        }
    }

    fun dismissMatch() {
        if (_uiState.value is SwipingUIState.Match) {
            _uiState.value = SwipingUIState.Success
        }
    }

    sealed class SwipingUIState {
        object Initial : SwipingUIState()
        object Loading : SwipingUIState()
        object Success : SwipingUIState()
        object NoMoreProfiles : SwipingUIState()
        data class Error(val message: String) : SwipingUIState()
        data class Match(val matchedDog: Dog) : SwipingUIState()
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any resources if needed
    }
}