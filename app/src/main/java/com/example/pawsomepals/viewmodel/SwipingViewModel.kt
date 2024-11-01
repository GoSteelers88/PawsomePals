package com.example.pawsomepals.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.*
import com.example.pawsomepals.data.repository.DogProfileRepository
import com.example.pawsomepals.data.repository.MatchRepository
import com.example.pawsomepals.service.MatchingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.example.pawsomepals.service.LocationService
import kotlinx.coroutines.delay
import javax.inject.Inject
import com.example.pawsomepals.service.LocationService.LocationException

@HiltViewModel
class SwipingViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val dogProfileRepository: DogProfileRepository,
    private val matchingService: MatchingService
) : ViewModel() {

    // Add back the preloadJob and preloadScope declarations
    private val preloadJob = SupervisorJob()
    private val preloadScope = CoroutineScope(Dispatchers.IO + preloadJob)

    companion object {
        private const val TAG = "SwipingViewModel"
        private const val PROFILE_BATCH_SIZE = 20
        private const val MIN_PROFILES_THRESHOLD = 5
        private const val REFRESH_COOLDOWN = 60000L // 1 minute
    }

    // Enhanced UI States
    sealed class SwipingUIState {
        object Initial : SwipingUIState()
        object Loading : SwipingUIState()
        object Success : SwipingUIState()
        object NoMoreProfiles : SwipingUIState()
        object LocationNeeded : SwipingUIState()
        data class Error(
            val message: String,
            val type: ErrorType = ErrorType.GENERAL
        ) : SwipingUIState()
        data class Match(
            val matchDetail: MatchDetail,
            val isSuper: Boolean = false
        ) : SwipingUIState()
    }

    enum class ErrorType {
        NETWORK, LOCATION, PERMISSION, GENERAL
    }

    data class MatchDetail(
        val dog: Dog,
        val compatibilityScore: Double,
        val compatibilityReasons: List<MatchReason>,
        val distance: Double?,
        val warnings: List<String> = emptyList()
    )

    data class SwipeMetrics(
        val viewStartTime: Long = System.currentTimeMillis(),
        val photosViewed: Int = 0,
        val scrollDepth: Int = 0
    )

    // State Management
    private val _currentMatchDetail = MutableStateFlow<MatchDetail?>(null)
    val currentMatchDetail: StateFlow<MatchDetail?> = _currentMatchDetail.asStateFlow()

    private val _uiState = MutableStateFlow<SwipingUIState>(SwipingUIState.Initial)
    val uiState: StateFlow<SwipingUIState> = _uiState.asStateFlow()

    private val _currentProfile = MutableStateFlow<Dog?>(null)
    val currentProfile: StateFlow<Dog?> = _currentProfile.asStateFlow()

    private val _matches = MutableStateFlow<List<Dog>>(emptyList())
    val matches: StateFlow<List<Dog>> = _matches.asStateFlow()

    private val _swipeMetrics = MutableStateFlow(SwipeMetrics())

    private val _profileQueue = MutableStateFlow<List<Dog>>(emptyList())
    private var lastRefreshTime = 0L
    private var isLoadingMore = false

        init {
            initializeViewModel()
        }
        // Add inside SwipingViewModel class

        private suspend fun loadInitialData() {
            try {
                _uiState.value = SwipingUIState.Loading

                // Load active matches
                matchRepository.getActiveMatches().collect { matches ->
                    _matches.value = matches.mapNotNull { match ->
                        dogProfileRepository.getDogById(match.dog2Id).getOrNull()
                    }
                }

                // Load initial profiles
                loadMoreProfiles()
                processNextProfile()

                _uiState.value = SwipingUIState.Success
            } catch (e: Exception) {
                handleError(e)
            }
        }

        private suspend fun processNextProfile() {
            if (_profileQueue.value.isEmpty()) {
                _uiState.value = SwipingUIState.NoMoreProfiles
                loadMoreProfiles()
                return
            }

            val nextProfile = _profileQueue.value.first()
            _profileQueue.update { it.drop(1) }
            _currentProfile.value = nextProfile
            _swipeMetrics.value = SwipeMetrics()
        }

        private suspend fun checkForMatch(
            dog: Dog,
            matchResult: MatchingService.MatchResult,
            isSuperLike: Boolean = false
        ) {
            if (matchResult.isMatch) {
                val matchDetail = MatchDetail(
                    dog = dog,
                    compatibilityScore = matchResult.compatibilityScore,
                    compatibilityReasons = matchResult.reasons,
                    distance = matchResult.distance,
                    warnings = matchResult.warnings
                )
                createMatch(matchDetail)
                _uiState.value = SwipingUIState.Match(matchDetail, isSuperLike)
            } else {
                _uiState.value = SwipingUIState.Success
            }
        }

        private suspend fun createMatch(matchDetail: MatchDetail) {
            val currentDogId = getCurrentDogId()
            val match = Match(
                id = generateMatchId(),
                user1Id = _currentProfile.value?.ownerId
                    ?: throw IllegalStateException("No current profile owner"),
                user2Id = matchDetail.dog.ownerId,
                dog1Id = currentDogId,
                dog2Id = matchDetail.dog.id,
                compatibilityScore = matchDetail.compatibilityScore,
                matchReasons = matchDetail.compatibilityReasons,
                status = MatchStatus.PENDING,
                timestamp = System.currentTimeMillis()
            )
            matchRepository.createMatch(match)
        }

        private fun generateMatchId(): String {
            return "match_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}"
        }

        private fun initializeViewModel() {
            viewModelScope.launch {
                loadInitialData()
                setupProfilePreloading()
            }
        }

        // Public Interface
        fun onSwipeWithCompatibility(dogId: String, direction: SwipeDirection) {
            viewModelScope.launch {
                try {
                    _uiState.value = SwipingUIState.Loading

                    val swipeMetrics = _swipeMetrics.value
                    val currentDog = _currentProfile.value ?: return@launch

                    when (direction) {
                        SwipeDirection.RIGHT -> handleLike(currentDog, swipeMetrics)
                        SwipeDirection.LEFT -> handleDislike(currentDog, swipeMetrics)
                        SwipeDirection.UP -> handleSuperLike(currentDog, swipeMetrics)
                        else -> handleDislike(currentDog, swipeMetrics)
                    }

                    processNextProfile()
                } catch (e: Exception) {
                    handleError(e)
                }
            }
        }

        fun onPhotoViewed() {
            _swipeMetrics.update { it.copy(photosViewed = it.photosViewed + 1) }
        }

        fun onProfileScroll(depth: Int) {
            _swipeMetrics.update { it.copy(scrollDepth = maxOf(it.scrollDepth, depth)) }
        }

        fun refresh() {
            viewModelScope.launch {
                if (System.currentTimeMillis() - lastRefreshTime < REFRESH_COOLDOWN) {
                    return@launch
                }

                _uiState.value = SwipingUIState.Loading
                _profileQueue.value = emptyList()
                _currentProfile.value = null
                lastRefreshTime = System.currentTimeMillis()

                loadInitialData()
            }
        }

        fun dismissMatch() {
            _currentMatchDetail.value = null
            _uiState.value = SwipingUIState.Success
        }

        // Private Implementation


        private suspend fun handleLike(dog: Dog, metrics: SwipeMetrics) {
            val matchResult = matchingService.calculateMatch(_currentProfile.value!!, dog)

            if (matchResult.isMatch) {
                processMatch(dog, matchResult, metrics)
            } else {
                recordSwipe(dog.id, true, metrics, matchResult.compatibilityScore)
                _uiState.value = SwipingUIState.Success
            }
        }

        private suspend fun handleSuperLike(dog: Dog, metrics: SwipeMetrics) {
            val matchResult = matchingService.calculateMatch(_currentProfile.value!!, dog)
            val superLikeSwipe = Swipe(
                swiperId = getCurrentDogId(),
                swipedId = dog.id,
                isLike = true,
                superLike = true,
                compatibilityScore = matchResult.compatibilityScore,
                viewDuration = System.currentTimeMillis() - metrics.viewStartTime,
                photosViewed = metrics.photosViewed,
                profileScrollDepth = metrics.scrollDepth
            )

            matchRepository.addSwipe(superLikeSwipe)
            checkForMatch(dog, matchResult, true)
        }

        private suspend fun handleDislike(dog: Dog, metrics: SwipeMetrics) {
            recordSwipe(dog.id, false, metrics, 0.0)
            _uiState.value = SwipingUIState.Success
        }

        private suspend fun processMatch(
            dog: Dog,
            matchResult: MatchingService.MatchResult,
            metrics: SwipeMetrics
        ) {
            val matchDetail = MatchDetail(
                dog = dog,
                compatibilityScore = matchResult.compatibilityScore,
                compatibilityReasons = matchResult.reasons,
                distance = matchResult.distance,
                warnings = matchResult.warnings
            )

            createMatch(matchDetail)
            recordSwipe(dog.id, true, metrics, matchResult.compatibilityScore)
            _uiState.value = SwipingUIState.Match(matchDetail)
        }

        private suspend fun recordSwipe(
            dogId: String,
            isLike: Boolean,
            metrics: SwipeMetrics,
            compatibilityScore: Double
        ) {
            val swipe = Swipe(
                swiperId = getCurrentDogId(),
                swipedId = dogId,
                isLike = isLike,
                compatibilityScore = compatibilityScore,
                viewDuration = System.currentTimeMillis() - metrics.viewStartTime,
                photosViewed = metrics.photosViewed,
                profileScrollDepth = metrics.scrollDepth
            )
            matchRepository.addSwipe(swipe)
        }

        private fun setupProfilePreloading() {
            viewModelScope.launch {
                _profileQueue
                    .map { it.size }
                    .distinctUntilChanged()
                    .collect { size ->
                        if (size < MIN_PROFILES_THRESHOLD && !isLoadingMore) {
                            loadMoreProfiles()
                        }
                    }
            }
        }

        private suspend fun loadMoreProfiles() {
            if (isLoadingMore) return
            isLoadingMore = true

            try {
                val newProfiles = dogProfileRepository.getSwipingProfiles(PROFILE_BATCH_SIZE)
                newProfiles.fold(
                    onSuccess = { dogs ->
                        _profileQueue.update { current -> current + dogs }
                    },
                    onFailure = { exception ->
                        Log.e(TAG, "Failed to load more profiles", exception)
                    }
                )
            } finally {
                isLoadingMore = false
            }
        }

        private fun handleError(error: Throwable) {
            val errorState = when (error) {
                is LocationService.LocationException ->
                    SwipingUIState.Error("Location services required", ErrorType.LOCATION)

                is SecurityException ->
                    SwipingUIState.Error("Permission denied", ErrorType.PERMISSION)

                is java.io.IOException ->
                    SwipingUIState.Error("Network error", ErrorType.NETWORK)

                else ->
                    SwipingUIState.Error("Unexpected error: ${error.message}")
            }
            _uiState.value = errorState
        }

        private fun getCurrentDogId(): String {
            return _currentProfile.value?.id ?: throw IllegalStateException("No current profile")
        }

        override fun onCleared() {
            super.onCleared()
            preloadJob.cancel()
        }
    }

