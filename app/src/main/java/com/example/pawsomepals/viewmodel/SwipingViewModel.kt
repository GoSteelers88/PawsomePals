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
import kotlin.math.abs

@HiltViewModel
class SwipingViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val dogProfileRepository: DogProfileRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SwipingViewModel"
        private const val COMPATIBILITY_THRESHOLD = 0.7
    }

    // UI States
    sealed class SwipingUIState {
        object Initial : SwipingUIState()
        object Loading : SwipingUIState()
        object Success : SwipingUIState()
        object NoMoreProfiles : SwipingUIState()
        object LocationNeeded : SwipingUIState()
        data class Error(val message: String) : SwipingUIState()
        data class Match(val matchedDog: Dog) : SwipingUIState()
    }

    data class MatchDetail(
        val dog: Dog,
        val compatibilityScore: Double,
        val compatibilityReasons: List<String>
    )

    // State Flows
    private val _uiState = MutableStateFlow<SwipingUIState>(SwipingUIState.Initial)
    val uiState: StateFlow<SwipingUIState> = _uiState.asStateFlow()

    private val _currentProfile = MutableStateFlow<Dog?>(null)
    val currentProfile: StateFlow<Dog?> = _currentProfile.asStateFlow()

    private val _matches = MutableStateFlow<List<Dog>>(emptyList())
    val matches: StateFlow<List<Dog>> = _matches.asStateFlow()

    private val _currentMatchDetail = MutableStateFlow<MatchDetail?>(null)
    val currentMatchDetail: StateFlow<MatchDetail?> = _currentMatchDetail.asStateFlow()

    private val _availableProfiles = MutableStateFlow<List<Dog>>(emptyList())
    val availableProfiles: StateFlow<List<Dog>> = _availableProfiles.asStateFlow()

    // Profile Queue Management
    private val profileQueue = mutableListOf<Dog>()
    private var currentIndex = 0

    init {
        loadInitialData()
    }

    // Public Functions
    fun onSwipeWithCompatibility(dogId: String, liked: Boolean) {
        viewModelScope.launch {
            try {
                _uiState.value = SwipingUIState.Loading

                if (liked) {
                    handleLike(dogId)
                } else {
                    handleDislike(dogId)
                }

                showNextProfile()
            } catch (e: Exception) {
                _uiState.value = SwipingUIState.Error("Failed to process swipe")
            }
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

    // Private Helper Functions
    private fun loadInitialData() {
        viewModelScope.launch {
            loadSwipingProfiles()
            loadMatches()
        }
    }

    private suspend fun handleLike(dogId: String) {
        when (val result = matchRepository.getCompatibilityScore(dogId)) {
            is ResultWrapper.Success -> {
                val score = result.data
                if (score >= COMPATIBILITY_THRESHOLD) {
                    processLike(dogId, score)
                } else {
                    _uiState.value = SwipingUIState.Error("Not compatible enough for a match")
                }
            }
            is ResultWrapper.Error -> {
                _uiState.value = SwipingUIState.Error("Failed to check compatibility")
            }
        }
    }

    private suspend fun handleDislike(dogId: String) {
        matchRepository.addDislike(dogId)
        _uiState.value = SwipingUIState.Success
    }

    private suspend fun processLike(dogId: String, compatibilityScore: Double) {
        when (val likeResult = matchRepository.addLike(dogId)) {
            is ResultWrapper.Success -> {
                checkForMatch(dogId, compatibilityScore)
            }
            is ResultWrapper.Error -> {
                _uiState.value = SwipingUIState.Error("Failed to process like")
            }
        }
    }

    private suspend fun checkForMatch(dogId: String, compatibilityScore: Double) {
        when (val matchResult = matchRepository.isMatch(dogId)) {
            is ResultWrapper.Success -> {
                if (matchResult.data) {
                    createMatch(dogId, compatibilityScore)
                } else {
                    _uiState.value = SwipingUIState.Success
                }
            }
            is ResultWrapper.Error -> {
                _uiState.value = SwipingUIState.Error("Failed to check match status")
            }
        }
    }

    private suspend fun createMatch(dogId: String, compatibilityScore: Double) {
        dogProfileRepository.getDogProfile(dogId).collect { dogResult ->
            dogResult.fold(
                onSuccess = { matchedDog ->
                    matchedDog?.let {
                        _currentMatchDetail.value = MatchDetail(
                            dog = it,
                            compatibilityScore = compatibilityScore,
                            compatibilityReasons = getCompatibilityReasons(it)
                        )
                        _matches.value = _matches.value + it
                        _uiState.value = SwipingUIState.Match(it)
                    }
                },
                onFailure = { e ->
                    _uiState.value = SwipingUIState.Error("Failed to load match details")
                }
            )
        }
    }

    private fun getCompatibilityReasons(matchedDog: Dog): List<String> {
        val reasons = mutableListOf<String>()
        currentProfile.value?.let { currentDog ->
            if (currentDog.size == matchedDog.size) {
                reasons.add("Same size")
            }
            if (currentDog.energyLevel == matchedDog.energyLevel) {
                reasons.add("Matching energy levels")
            }
            if (abs((currentDog.age ?: 0) - (matchedDog.age ?: 0)) <= 2) {
                reasons.add("Similar age")
            }
        }
        return reasons
    }

    private fun showNextProfile() {
        if (currentIndex < profileQueue.size) {
            _currentProfile.value = profileQueue[currentIndex++]
        } else {
            _currentProfile.value = null
            _uiState.value = SwipingUIState.NoMoreProfiles
        }
    }

    private fun loadSwipingProfiles() {
        viewModelScope.launch {
            try {
                _uiState.value = SwipingUIState.Loading
                val result = dogProfileRepository.getSwipingProfiles()
                result.fold(
                    onSuccess = { dogs ->
                        profileQueue.clear()
                        profileQueue.addAll(dogs)
                        _availableProfiles.value = dogs
                        showNextProfile()
                        _uiState.value = SwipingUIState.Success
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

    private fun loadMatches() {
        viewModelScope.launch {
            try {
                matchRepository.getUserMatches().collect { matchedDogs ->
                    _matches.value = matchedDogs
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading matches", e)
                _uiState.value = SwipingUIState.Error("Failed to load matches")
            }
        }
    }
}