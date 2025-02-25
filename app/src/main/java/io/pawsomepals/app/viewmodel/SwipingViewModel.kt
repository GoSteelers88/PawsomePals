package io.pawsomepals.app.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchReason
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.MatchType
import io.pawsomepals.app.data.model.MessageType
import io.pawsomepals.app.data.model.Settings
import io.pawsomepals.app.data.model.Swipe
import io.pawsomepals.app.data.model.SwipeDirection
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.data.repository.AuthRepository
import io.pawsomepals.app.data.repository.ChatRepository
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.data.repository.SettingsRepository
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.discovery.ProfileDiscoveryService
import io.pawsomepals.app.discovery.queue.LocationAwareQueueManager
import io.pawsomepals.app.service.MatchingService
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.ui.components.FilterState
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
import kotlin.math.roundToInt



@HiltViewModel
class SwipingViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val dogProfileRepository: DogProfileRepository,
    private val matchingService: MatchingService,
    private val settingsRepository: SettingsRepository,
    private val locationService: LocationService,
    private val chatRepository: ChatRepository, // Add this
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val authStateManager: AuthStateManager,
    private val auth: FirebaseAuth,
    private val profileDiscoveryService: ProfileDiscoveryService,
    private val locationMatchingEngine: LocationMatchingEngine,
    private val locationAwareQueueManager: LocationAwareQueueManager,// Add this



) : ViewModel() {
    data class ProfileWithScore(
        val dog: Dog,
        val score: Double,
        val distance: Double?
    )
    private val _nearbyProfiles = MutableStateFlow<List<ProfileWithScore>>(emptyList())
    val nearbyProfiles: StateFlow<List<ProfileWithScore>> = _nearbyProfiles.asStateFlow()
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()





    companion object {
        private const val TAG = "SwipingViewModel"
        private const val PROFILE_BATCH_SIZE = 20
        private const val MIN_PROFILES_THRESHOLD = 5
        private const val REFRESH_COOLDOWN = 60000L // 1 minute
    }
    private fun filterProfile(dog: Dog, filter: FilterState, currentLocation: Location?): Boolean {
        Log.d(TAG, """
    Filtering dog ${dog.id}:
    Dog data:
      - Name: ${dog.name}
      - Energy: ${dog.energyLevel}
      - Age: ${dog.age}
      - Breed: ${dog.breed}
      - Size: ${dog.size}
      - Owner ID: ${dog.ownerId}
    
    Filter settings:
      - Energy levels: ${filter.energyLevels}
      - Age range: ${filter.minAge}..${filter.maxAge}
      - Breeds: ${filter.selectedBreeds}
      - Sizes: ${filter.size}
      - Max distance: ${filter.maxDistance}
    """.trimIndent())

        // Skip profiles without basic required data
        if (dog.ownerId.isBlank()) {
            Log.d(TAG, "Skipping dog ${dog.id} - missing owner ID")
            return false
        }

        // Energy level check (more permissive)
        val energyMatch = filter.energyLevels.contains("ANY") ||
                filter.energyLevels.isEmpty() ||
                filter.energyLevels.contains(dog.energyLevel)
        Log.d(TAG, "Energy match: $energyMatch")

        // Age check (with validation)
        val ageMatch = dog.age in filter.minAge..filter.maxAge
        Log.d(TAG, "Age match: $ageMatch (dog age: ${dog.age})")

        // Breed check (more permissive)
        val breedMatch = filter.selectedBreeds.contains("ANY") ||
                filter.selectedBreeds.isEmpty() ||
                filter.selectedBreeds.contains(dog.breed)
        Log.d(TAG, "Breed match: $breedMatch")

        // Size check (more permissive)
        val sizeMatch = filter.size.contains("ANY") ||
                filter.size.isEmpty() ||
                filter.size.contains(dog.size)
        Log.d(TAG, "Size match: $sizeMatch")

        val matches = energyMatch && ageMatch && breedMatch && sizeMatch
        Log.d(TAG, """
    Final results for dog ${dog.id}:
    - All conditions match: $matches
    - Individual results:
      * Energy: $energyMatch
      * Age: $ageMatch
      * Breed: $breedMatch
      * Size: $sizeMatch
    """.trimIndent())

        return matches
    }
    // UI States
    sealed class SwipingUIState {
        object Initial : SwipingUIState()
        object Loading : SwipingUIState()
        object Success : SwipingUIState()
        object ProfileLoading : SwipingUIState()  // Add this state

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
        val warnings: List<String> = emptyList(),
        val chatId: String,
        val matchId: String,

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

    val isProcessing = MutableStateFlow(false)
    val canUndo get() = _recentSwipes.value.isNotEmpty()

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

    private val _ownerProfile = MutableStateFlow<User?>(null)
    val ownerProfile = _ownerProfile.asStateFlow()

    // State flags
    private var isLoadingMore = false
    private var lastRefreshTime = 0L

    // Preloading
    private val preloadJob = SupervisorJob()
    private val preloadScope = CoroutineScope(Dispatchers.IO + preloadJob)

    init {
        viewModelScope.launch {
            // Use AuthStateManager's authState
            authStateManager.authState.collect { authState ->
                when (authState) {
                    is AuthStateManager.AuthState.Authenticated.Complete -> {
                        loadInitialData()
                        setupProfilePreloading()
                        observeSettingsChanges()
                    }
                    is AuthStateManager.AuthState.Authenticated.NeedsTerms -> {
                        _uiState.value = SwipingUIState.Error(
                            "Please accept terms to continue",
                            ErrorType.PERMISSION
                        )
                    }
                    is AuthStateManager.AuthState.Authenticated.NeedsQuestionnaire -> {
                        _uiState.value = SwipingUIState.Error(
                            "Please complete questionnaire to continue",
                            ErrorType.PERMISSION
                        )
                    }
                    is AuthStateManager.AuthState.Unauthenticated -> {
                        // Clear existing data
                        _profileQueue.value = emptyList()
                        _currentProfile.value = null
                        _uiState.value = SwipingUIState.Initial
                    }
                    is AuthStateManager.AuthState.Error -> {
                        _uiState.value = SwipingUIState.Error(
                            authState.message,
                            ErrorType.GENERAL
                        )
                    }
                    AuthStateManager.AuthState.Initial -> {
                        // Wait for actual auth state
                        _uiState.value = SwipingUIState.Initial
                    }
                }
            }
        }
    }
    private suspend fun loadMoreProfiles() {
        if (isLoadingMore) {
            Log.d(TAG, "Already loading profiles, skipping")
            return
        }
        isLoadingMore = true

        try {
            val currentDog = _currentProfile.value
            Log.d(TAG, "Loading profiles. Current dog: ${currentDog?.id}")

            if (currentDog == null) {
                Log.e(TAG, "No current dog profile available for loading profiles")
                _uiState.value = SwipingUIState.Error("No active dog profile")
                return
            }

            // Create preferences with exclusions
            val preferences = ProfileDiscoveryService.DiscoveryPreferences(
                maxDistance = filterState.value.maxDistance,
                excludedUserIds = setOf(currentDog.ownerId),
                excludedDogIds = setOf(currentDog.id)
            )

            // Log discovery service call
            Log.d(TAG, """
            Calling profile discovery service with:
            - Max distance: ${preferences.maxDistance}
            - Excluded user: ${preferences.excludedUserIds}
            - Excluded dogs: ${preferences.excludedDogIds}
        """.trimIndent())

            profileDiscoveryService.discoverProfiles(
                currentDog = currentDog,
                preferences = preferences
            ).collect { profiles ->
                // Rest of your existing collection logic...
                Log.d(TAG, """
                Profile Discovery Results:
                - Total profiles: ${profiles.size}
                - Profile IDs: ${profiles.map { it.id }}
                - Unique owners: ${profiles.map { it.ownerId }.distinct().size}
            """.trimIndent())

                val filteredProfiles = profiles.filter { dog ->
                    val hasBeenSwiped = hasBeenSwiped(currentDog.ownerId, dog.id).getOrNull() ?: false
                    !hasBeenSwiped
                }

                // Update queue
                locationService.getLastKnownLocation()?.let { location ->
                    locationAwareQueueManager.addBatchToQueue(
                        dogs = filteredProfiles,
                        currentLat = location.latitude,
                        currentLng = location.longitude
                    )
                    val nextBatch = locationAwareQueueManager.getNextBatch(PROFILE_BATCH_SIZE)
                    Log.d(TAG, "Queue updated with ${nextBatch.size} profiles")
                    _profileQueue.value = nextBatch
                } ?: run {
                    Log.w(TAG, "No location available, adding profiles without location sorting")
                    _profileQueue.value = filteredProfiles.take(PROFILE_BATCH_SIZE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profiles", e)
            _uiState.value = SwipingUIState.Error("Failed to load profiles: ${e.message}")
        } finally {
            isLoadingMore = false
        }
    }
    private suspend fun hasBeenSwiped(userId: String, profileId: String): Result<Boolean> {
        return matchRepository.hasUserSwipedProfile(userId, profileId)
    }
    private suspend fun loadOwnerProfile(dog: Dog): Boolean {
        try {
            _uiState.value = SwipingUIState.ProfileLoading

            val owner = userRepository.getUserById(dog.ownerId)
            Log.d(TAG, "Loaded owner profile: ${owner?.username}")

            if (owner == null) {
                Log.e(TAG, "Owner profile not found for dog ${dog.id}")
                return false
            }

            _ownerProfile.value = owner
            _uiState.value = SwipingUIState.Success
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error loading owner profile", e)
            _uiState.value = SwipingUIState.Error("Failed to load profile")
            return false
        }
    }
    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                _uiState.value = SwipingUIState.Loading

                val currentState = authStateManager.authState.value
                Log.d(TAG, "Current auth state: $currentState")

                if (currentState is AuthStateManager.AuthState.Authenticated.Complete) {
                    // Get current user's dog profile first
                    val userId = auth.currentUser?.uid
                    Log.d(TAG, "Attempting to load profile for user: $userId")

                    if (userId == null) {
                        Log.e(TAG, "No authenticated user found")
                        _uiState.value = SwipingUIState.Error("Authentication required")
                        return@launch
                    }

                    // Get user's dog profile
                    val dogResult = dogProfileRepository.getCurrentUserDogProfile(userId)
                    val userDog = when {
                        dogResult.isSuccess -> dogResult.getOrNull()
                        else -> {
                            Log.e(TAG, "Error loading user's dog profile", dogResult.exceptionOrNull())
                            null
                        }
                    }

                    Log.d(TAG, "User's dog profile loaded: ${userDog?.id}")

                    if (userDog == null) {
                        Log.e(TAG, "No dog profile found for user: $userId")
                        _uiState.value = SwipingUIState.Error("No dog profile found")
                        return@launch
                    }

                    _currentProfile.value = userDog

                    Log.d(TAG, "Starting to load more profiles")
                    _cachedProfiles.value = emptyMap()

                    try {
                        loadMoreProfiles()
                    } catch (e: FirebaseFirestoreException) {
                        if (e.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                            _uiState.value = SwipingUIState.Error(
                                "System is being updated. Please try again in a few minutes.",
                                ErrorType.GENERAL
                            )
                            return@launch
                        }
                        throw e
                    }

                    Log.d(TAG, "Profile load complete, processing next profile")
                    processNextProfile()
                } else {
                    Log.e(TAG, "Auth state not complete: $currentState")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in initial data load", e)
                handleError(e)
            }
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

    private val _cachedProfilesWithTimestamp = MutableStateFlow<Map<String, Long>>(emptyMap())

    private suspend fun verifyUserSetup(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            authStateManager.verifyUserSetup(userId)
            authStateManager.authState.value is AuthStateManager.AuthState.Authenticated.Complete
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying user setup", e)
            false
        }
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

    fun onSwipeWithCompatibility(targetDogId: String, direction: SwipeDirection) {
        viewModelScope.launch {
            try {
                _uiState.value = SwipingUIState.Loading
                Log.d(TAG, "Processing swipe: currentDog=${_currentProfile.value?.id}")

                val swipeMetrics = _swipeMetrics.value
                val targetDog = requireNotNull(_currentProfile.value) {
                    "No current profile available"
                }

                // Get current user ID
                val currentUserId = auth.currentUser?.uid
                if (currentUserId == null) {
                    _uiState.value = SwipingUIState.Error("Not authenticated", ErrorType.PERMISSION)
                    return@launch
                }

                // Verify we're not swiping on our own dog
                if (targetDog.ownerId == currentUserId) {
                    Log.e(TAG, "Attempted to swipe on own dog: $targetDogId")
                    _uiState.value = SwipingUIState.Error("Cannot swipe on your own dog", ErrorType.GENERAL)
                    return@launch
                }

                // Get our user's dog
                val userDog = dogProfileRepository.getCurrentUserDogProfile(currentUserId).getOrNull()
                if (userDog == null) {
                    _uiState.value = SwipingUIState.Error("No dog profile found", ErrorType.GENERAL)
                    return@launch
                }

                when (direction) {
                    SwipeDirection.RIGHT -> handleLike(userDog, targetDog, swipeMetrics)
                    SwipeDirection.LEFT -> handleDislike(userDog, targetDog, swipeMetrics)
                    SwipeDirection.UP -> handleSuperLike(userDog, targetDog, swipeMetrics)
                    else -> handleDislike(userDog, targetDog, swipeMetrics)
                }

                recordRecentSwipe(targetDog, direction)
                processNextProfile()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing swipe", e)
                handleError(e)
            }
        }
    }

    private suspend fun handleLike(userDog: Dog, targetDog: Dog, metrics: SwipeMetrics) {
        val matchResult = matchingService.calculateMatch(userDog, targetDog)

        val swipe = Swipe(
            id = UUID.randomUUID().toString(),
            swiperId = userDog.id,
            swipedId = targetDog.id,
            swiperDogId = userDog.id,
            swipedDogId = targetDog.id,
            isLike = true,
            compatibilityScore = matchResult.compatibilityScore,
            timestamp = System.currentTimeMillis(),
            viewDuration = System.currentTimeMillis() - metrics.viewStartTime,
            photosViewed = metrics.photosViewed,
            profileScrollDepth = metrics.scrollDepth
        )

        matchRepository.addSwipe(swipe)

        if (matchResult.isMatch) {
            processMatch(targetDog, matchResult, metrics)
        } else {
            _uiState.value = SwipingUIState.Success
        }
    }

    private suspend fun handleSuperLike(userDog: Dog, targetDog: Dog, metrics: SwipeMetrics) {
        val matchResult = matchingService.calculateMatch(userDog, targetDog)

        val superLikeSwipe = Swipe(
            swiperId = userDog.id,
            swipedId = targetDog.id,
            swiperDogId = userDog.id,
            swipedDogId = targetDog.id,
            isLike = true,
            superLike = true,
            compatibilityScore = matchResult.compatibilityScore,
            viewDuration = System.currentTimeMillis() - metrics.viewStartTime,
            photosViewed = metrics.photosViewed,
            profileScrollDepth = metrics.scrollDepth
        )

        matchRepository.addSwipe(superLikeSwipe)
        checkForMatch(targetDog, matchResult, true)
    }

    private suspend fun checkForMatch(dog: Dog, matchResult: MatchingService.MatchResult, isSuperLike: Boolean) {
        if (matchResult.isMatch) {
            val matchDetail = MatchDetail(
                dog = dog,
                compatibilityScore = matchResult.compatibilityScore,
                compatibilityReasons = matchResult.reasons,
                distance = matchResult.distance,
                warnings = matchResult.warnings,
                chatId = "chat_${generateMatchId()}",
                matchId = generateMatchId()
            )
            createMatch(matchDetail)
            _uiState.value = SwipingUIState.Match(matchDetail, isSuperLike)
        } else {
            _uiState.value = SwipingUIState.Success
        }
    }
    private suspend fun handleDislike(userDog: Dog, targetDog: Dog, metrics: SwipeMetrics) {
        val swipe = Swipe(
            swiperId = userDog.id,
            swipedId = targetDog.id,
            swiperDogId = userDog.id,
            swipedDogId = targetDog.id,
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
        targetDog: Dog,
        matchResult: MatchingService.MatchResult,
        metrics: SwipeMetrics? = null
    ) {
        val currentUserId = auth.currentUser?.uid ?: return
        val matchId = generateMatchId()

        val match = Match(
            id = matchId,
            user1Id = currentUserId,
            user2Id = targetDog.ownerId,
            dog1Id = getCurrentDogId(),
            dog2Id = targetDog.id,
            compatibilityScore = matchResult.compatibilityScore,
            matchReasons = matchResult.reasons,
            status = MatchStatus.PENDING,
            timestamp = System.currentTimeMillis(),
            locationDistance = matchResult.distance,
            matchType = determineMatchType(matchResult.compatibilityScore)
        )

        // Create match
        matchRepository.createMatch(match)

        // Create chat using the version with dog IDs
        val chatId = chatRepository.createChat(
            user1Id = currentUserId,
            user2Id = targetDog.ownerId,
            matchId = matchId,
            dog1Id = getCurrentDogId(),  // Add dog IDs
            dog2Id = targetDog.id        // Add dog IDs
        )

        // Send welcome message
        chatRepository.sendMessage(
            chatId = chatId,
            senderId = "system",
            content = generateWelcomeMessage(match),
            type = MessageType.SYSTEM
        )
    }
    private fun generateWelcomeMessage(match: Match): String {
        val compatibilityPercent = (match.compatibilityScore * 100).roundToInt()
        return """
        🎉 It's a match! You have a $compatibilityPercent% compatibility score.
        
        Why you might be great playmates:
        ${match.matchReasons.joinToString("\n") { "• ${it.description}" }}
        
        Complete the safety checklist and schedule your first playdate!
    """.trimIndent()
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

    private fun isAuthenticated(): Boolean {
        return authStateManager.authState.value is AuthStateManager.AuthState.Authenticated.Complete
    }

    fun dismissMatch() {
        _currentMatchDetail.value = null
        _uiState.value = SwipingUIState.Success
    }

    private suspend fun processNextProfile() {
        Log.d(TAG, "Processing next profile. Current queue size: ${_profileQueue.value.size}")

        if (_profileQueue.value.isEmpty()) {
            Log.d(TAG, "Queue empty, attempting to load more profiles")
            loadMoreProfiles()
            if (_profileQueue.value.isEmpty()) {
                _uiState.value = SwipingUIState.NoMoreProfiles
                return
            }
        }

        try {
            // Get next profile and remove it from queue immediately
            val nextProfile = _profileQueue.value.first()
            _profileQueue.update { it.drop(1) }

            Log.d(TAG, "Processing profile: ${nextProfile.id}")

            // Attempt to load owner
            val ownerLoadSuccess = loadOwnerProfile(nextProfile)

            if (ownerLoadSuccess) {
                _currentProfile.value = nextProfile
                _swipeMetrics.value = SwipeMetrics()
            } else {
                // If owner load failed, try next profile
                processNextProfile()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing next profile", e)
            _uiState.value = SwipingUIState.Error("Failed to load next profile")
        }
    }
    private fun getCurrentDogId(): String {
        val currentState = authStateManager.authState.value
        if (currentState !is AuthStateManager.AuthState.Authenticated) {
            throw IllegalStateException("User must be authenticated to get current dog ID")
        }
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
