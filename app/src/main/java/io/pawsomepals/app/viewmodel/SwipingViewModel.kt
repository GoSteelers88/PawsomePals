package io.pawsomepals.app.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchReason
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.MatchType
import io.pawsomepals.app.data.model.Settings
import io.pawsomepals.app.data.model.Swipe
import io.pawsomepals.app.data.model.SwipeDirection
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.data.repository.SettingsRepository
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.ui.screens.FilterState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject


@HiltViewModel
class SwipingViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val dogProfileRepository: DogProfileRepository,
    private val matchingService: MatchingService,
    private val settingsRepository: SettingsRepository,
    private val locationService: LocationService
) : ViewModel() {

    companion object {
        private const val TAG = "SwipingViewModel"
        private const val PROFILE_BATCH_SIZE = 20
        private const val MIN_PROFILES_THRESHOLD = 5
        private const val REFRESH_COOLDOWN = 60000L // 1 minute
    }

    // UI States
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

    data class RecentSwipe(
        val dog: Dog,
        val swipeDirection: SwipeDirection,
        val timestamp: Long = System.currentTimeMillis()
    )

    // State Management
    private val _uiState = MutableStateFlow<SwipingUIState>(SwipingUIState.Initial)
    val uiState: StateFlow<SwipingUIState> = _uiState.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _currentProfile = MutableStateFlow<Dog?>(null)
    val currentProfile: StateFlow<Dog?> = _currentProfile.asStateFlow()

    private val _profileQueue = MutableStateFlow<List<Dog>>(emptyList())
    val profileQueue: StateFlow<List<Dog>> = _profileQueue.asStateFlow()

    private val _cachedProfiles = MutableStateFlow<Map<String, Dog>>(emptyMap())

    private val _currentMatchDetail = MutableStateFlow<MatchDetail?>(null)
    val currentMatchDetail: StateFlow<MatchDetail?> = _currentMatchDetail.asStateFlow()

    private val _matches = MutableStateFlow<List<Dog>>(emptyList())
    val matches: StateFlow<List<Dog>> = _matches.asStateFlow()

    private val _swipeMetrics = MutableStateFlow(SwipeMetrics())
    private val _recentSwipes = MutableStateFlow<List<RecentSwipe>>(emptyList())

    // State flags
    private var isLoadingMore = false
    private var lastRefreshTime = 0L

    // Preloading
    private val preloadJob = SupervisorJob()
    private val preloadScope = CoroutineScope(Dispatchers.IO + preloadJob)

    init {
        initializeViewModel()
    }

    private fun initializeViewModel() {
        viewModelScope.launch {
            loadInitialData()
            setupProfilePreloading()
            observeSettingsChanges()
        }
    }

    private fun observeSettingsChanges() {
        viewModelScope.launch {
            settingsRepository.getSettingsFlow().collect { settings ->
                settings?.let { nonNullSettings ->
                    _filterState.value = FilterState(
                        maxDistance = nonNullSettings.maxDistance.toDouble(),
                        energyLevels = nonNullSettings.breedPreferences,
                        minAge = nonNullSettings.agePreferenceMin,
                        maxAge = nonNullSettings.agePreferenceMax
                    )
                    refresh()
                } ?: run {
                    // If settings is null, use default values
                    _filterState.value = FilterState(
                        maxDistance = 50.0,  // Default from Settings class
                        energyLevels = emptyList(),
                        minAge = 0,          // Default from Settings class
                        maxAge = 20          // Default from Settings class
                    )

                    // Create default settings
                    viewModelScope.launch {
                        settingsRepository.updateSettings(Settings())
                    }
                }
            }
        }
    }

    private suspend fun loadInitialData() {
        _uiState.value = SwipingUIState.Loading
        try {
            loadMoreProfiles()
            processNextProfile()
        } catch (e: Exception) {
            handleError(e)
        }
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
            val currentLocation = locationService.getLastKnownLocation()
            val filter = filterState.value

            dogProfileRepository.getSwipingProfiles(
                batchSize = PROFILE_BATCH_SIZE
            ).onSuccess { dogs ->
                val filteredDogs = dogs.filter { dog ->
                    filterProfile(dog, filter, currentLocation)
                }.filterNot { it.id in _cachedProfiles.value }

                _profileQueue.update { current -> current + filteredDogs }
                cacheProfiles(filteredDogs)
            }.onFailure { exception ->
                Log.e(TAG, "Failed to load profiles", exception)
                handleError(exception)
            }
        } finally {
            isLoadingMore = false
        }
    }
    private fun filterProfile(dog: Dog, filter: FilterState, currentLocation: Location?): Boolean {
        return (filter.energyLevels.contains("ANY") || filter.energyLevels.contains(dog.energyLevel)) &&
                dog.age in filter.minAge..filter.maxAge &&
                (filter.selectedBreeds.contains("ANY") || filter.selectedBreeds.contains(dog.breed)) &&
                (filter.size.contains("ANY") || filter.size.contains(dog.size)) &&
                (currentLocation == null || calculateDistance(currentLocation, dog) <= filter.maxDistance)
    }
    private fun calculateDistance(location: Location, dog: Dog): Double {
        return dog.latitude?.let { lat ->
            dog.longitude?.let { lng ->
                locationService.calculateDistance(
                    location.latitude,
                    location.longitude,
                    lat,
                    lng
                ).toDouble()
            }
        } ?: Double.POSITIVE_INFINITY
    }


    private fun cacheProfiles(dogs: List<Dog>) {
        _cachedProfiles.update { current ->
            current + dogs.associateBy { it.id }
        }
    }

    fun onSwipeWithCompatibility(dogId: String, direction: SwipeDirection) {
        viewModelScope.launch {
            try {
                _uiState.value = SwipingUIState.Loading
                val swipeMetrics = _swipeMetrics.value
                val currentDog = requireNotNull(_currentProfile.value) {
                    "No current profile available"
                }

                when (direction) {
                    SwipeDirection.RIGHT -> handleLike(currentDog, swipeMetrics)
                    SwipeDirection.LEFT -> handleDislike(currentDog, swipeMetrics)
                    SwipeDirection.UP -> handleSuperLike(currentDog, swipeMetrics)
                    else -> handleDislike(currentDog, swipeMetrics)
                }

                recordRecentSwipe(currentDog, direction)
                processNextProfile()
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }

    private suspend fun handleLike(dog: Dog, metrics: SwipeMetrics) {
        val matchResult = matchingService.calculateMatch(requireNotNull(_currentProfile.value), dog)

        val swipe = Swipe(
            swiperId = getCurrentDogId(),
            swipedId = dog.id,
            isLike = true,
            compatibilityScore = matchResult.compatibilityScore,
            viewDuration = System.currentTimeMillis() - metrics.viewStartTime,
            photosViewed = metrics.photosViewed,
            profileScrollDepth = metrics.scrollDepth
        )

        matchRepository.addSwipe(swipe)

        if (matchResult.isMatch) {
            processMatch(dog, matchResult, metrics)
        } else {
            _uiState.value = SwipingUIState.Success
        }
    }

    private suspend fun handleSuperLike(dog: Dog, metrics: SwipeMetrics) {
        val currentDog = requireNotNull(_currentProfile.value)
        val matchResult = matchingService.calculateMatch(currentDog, dog)

        val superLikeSwipe = Swipe(
            swiperId = currentDog.id,
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
    private suspend fun checkForMatch(dog: Dog, matchResult: MatchingService.MatchResult, isSuperLike: Boolean) {
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

    private suspend fun handleDislike(dog: Dog, metrics: SwipeMetrics) {
        val swipe = Swipe(
            swiperId = getCurrentDogId(),
            swipedId = dog.id,
            isLike = false,
            compatibilityScore = 0.0,
            viewDuration = System.currentTimeMillis() - metrics.viewStartTime,
            photosViewed = metrics.photosViewed,
            profileScrollDepth = metrics.scrollDepth
        )

        matchRepository.addSwipe(swipe)
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

    private suspend fun createMatch(matchDetail: MatchDetail) {
        val currentDogId = getCurrentDogId()
        val match = Match(
            id = generateMatchId(),
            user1Id = requireNotNull(_currentProfile.value?.ownerId),
            user2Id = matchDetail.dog.ownerId,
            dog1Id = currentDogId,
            dog2Id = matchDetail.dog.id,
            compatibilityScore = matchDetail.compatibilityScore,
            matchReasons = matchDetail.compatibilityReasons,
            status = MatchStatus.PENDING,
            timestamp = System.currentTimeMillis(),
            locationDistance = matchDetail.distance,
            matchType = determineMatchType(matchDetail.compatibilityScore),
            initiatorDogId = currentDogId
        )

        matchRepository.createMatch(match)
    }

    private fun determineMatchType(score: Double): MatchType = when {
        score >= 0.95 -> MatchType.PERFECT_MATCH
        score >= 0.8 -> MatchType.HIGH_COMPATIBILITY
        else -> MatchType.NORMAL
    }

    private fun recordRecentSwipe(dog: Dog, direction: SwipeDirection) {
        _recentSwipes.update { current ->
            (current + RecentSwipe(dog, direction)).takeLast(10)
        }
    }

    fun undoLastSwipe() {
        viewModelScope.launch {
            val lastSwipe = _recentSwipes.value.lastOrNull() ?: return@launch
            try {
                matchRepository.removeSwipe(getCurrentDogId(), lastSwipe.dog.id)
                _profileQueue.update { listOf(lastSwipe.dog) + it }
                _recentSwipes.update { it.dropLast(1) }
                processNextProfile()
            } catch (e: Exception) {
                handleError(e)
            }
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

    fun updateFilterState(newFilter: FilterState) {
        viewModelScope.launch {
            _filterState.value = newFilter
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (System.currentTimeMillis() - lastRefreshTime < REFRESH_COOLDOWN) return@launch

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

    private fun getCurrentDogId(): String {
        return requireNotNull(_currentProfile.value?.id) {
            "No current profile available"
        }
    }

    private fun generateMatchId(): String =
        "match_${System.currentTimeMillis()}_${UUID.randomUUID()}"

    override fun onCleared() {
        super.onCleared()
        preloadJob.cancel()
    }
}
