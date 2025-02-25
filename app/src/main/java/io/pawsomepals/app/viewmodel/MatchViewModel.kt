package io.pawsomepals.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.MatchToChatCoordinator
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.model.toMatchWithDetails
import io.pawsomepals.app.data.repository.ChatRepository
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.MatchRepository
import io.pawsomepals.app.service.location.LocationMatchingEngine
import io.pawsomepals.app.ui.screens.MatchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

const val TAG = "MatchesViewModel"

@HiltViewModel
class MatchesViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val dogProfileRepository: DogProfileRepository,
    private val auth: FirebaseAuth,
    private val locationMatchingEngine: LocationMatchingEngine,
    private val matchToChatCoordinator: MatchToChatCoordinator,
    private val chatRepository: ChatRepository


) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _nearbyMatches =
        MutableStateFlow<List<Match.MatchWithDetails>>(emptyList())  // Changed from Match.MatchWithDetails
    val nearbyMatches = _nearbyMatches.asStateFlow()

    private val _uiState = MutableStateFlow<MatchUiState>(MatchUiState.Idle)
    val uiState: StateFlow<MatchUiState> = _uiState.asStateFlow()

    // Separate flows for different match types
    private val _activeMatches = MutableStateFlow<List<Match>>(emptyList())
    val activeMatches: StateFlow<List<Match>> = _activeMatches.asStateFlow()

    // Get pending matches for current user
    private val pendingMatches = matchRepository.getPendingMatches()


    // Combine active and pending matches
    val matches = combine(
        activeMatches.map { Result.success(it) },
        pendingMatches
    ) { activeResult, pendingResult ->
        _isLoading.value = true
        try {
            val active = activeResult.getOrNull() ?: emptyList()
            val pending = pendingResult.getOrNull() ?: emptyList()
            val currentUserId = auth.currentUser?.uid
            val currentDog = currentUserId?.let {
                dogProfileRepository.getCurrentUserDogProfile(it).getOrNull()
            }

            if (currentUserId == null || currentDog == null) {
                emptyList()
            } else {
                (active + pending)
                    .filter { match ->
                        // Extra validation
                        match.user1Id != match.user2Id &&
                                match.dog1Id != match.dog2Id &&
                                (match.user1Id == currentUserId || match.user2Id == currentUserId) &&
                                (match.dog1Id == currentDog.id || match.dog2Id == currentDog.id)
                    }
                    .mapNotNull { match ->
                        runCatching {
                            match.toMatchWithDetails(currentDog.id, dogProfileRepository)
                        }.getOrNull()
                    }
            }
        } finally {
            _isLoading.value = false
        }
    }

    init {
        loadActiveMatches()
    }

    private fun loadActiveMatches() {
        viewModelScope.launch {
            try {
                auth.currentUser?.uid?.let { userId ->
                    matchRepository.getActiveMatches(userId).collect { result ->
                        result.fold(
                            onSuccess = { matches ->
                                val matchesWithDogs = matches.mapNotNull { match ->
                                    val dog1 =
                                        dogProfileRepository.getDogById(match.dog1Id).getOrNull()
                                    val dog2 =
                                        dogProfileRepository.getDogById(match.dog2Id).getOrNull()

                                    if (dog1 != null && dog2 != null) {
                                        Triple(match, dog1, dog2)
                                    } else null
                                }

                                val sortedMatches = matchesWithDogs.map { (match, dog1, dog2) ->
                                    val locationScore =
                                        locationMatchingEngine.calculateLocationScore(dog1, dog2)
                                    match to locationScore
                                }.sortedWith(
                                    compareByDescending<Pair<Match, LocationMatchingEngine.LocationScore>> {
                                        it.second.score
                                    }.thenByDescending {
                                        it.first.compatibilityScore
                                    }
                                ).map { it.first }

                                _activeMatches.value = sortedMatches
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Error fetching matches", error)
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in matches flow", e)
            }
        }
    }

    fun acceptMatch(matchId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Update match status first
                val updateResult = matchRepository.updateMatchStatus(matchId, MatchStatus.ACTIVE)

                if (updateResult.isSuccess) {
                    // Then initiate chat creation
                    when (val result = matchToChatCoordinator.initiateChatFromMatch(matchId)) {
                        is MatchToChatCoordinator.CoordinationResult.Success -> {
                            // Success state handling
                            loadActiveMatches() // Refresh matches list
                        }

                        is MatchToChatCoordinator.CoordinationResult.Error -> {
                            // Error state handling
                            when (result.error) {
                                is MatchToChatCoordinator.MatchChatError.MatchNotFound -> {
                                    // Handle match not found
                                }

                                is MatchToChatCoordinator.MatchChatError.ChatCreationFailed -> {
                                    // Handle chat creation failure
                                }

                                is MatchToChatCoordinator.MatchChatError.DogProfileNotFound -> {
                                    // Handle dog profile not found
                                }

                                MatchToChatCoordinator.MatchChatError.InvalidMatchStatus -> {
                                    // Handle invalid status
                                }
                            }
                        }
                    }
                } else {
                    // Handle match update failure
                    Log.e(TAG, "Failed to update match status")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in accept match flow", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun declineMatch(matchId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                matchRepository.updateMatchStatus(matchId, MatchStatus.DECLINED)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun removeMatch(matchId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                matchRepository.removeMatch(matchId)
            } finally {
                _isLoading.value = false
            }
        }
    }
    fun onNavigateToChat(chatId: String) {
        Log.d("Navigation", "Navigating to chat with ID: $chatId")
        viewModelScope.launch {
            try {
                chatRepository.markChatRead(chatId)
            } catch (e: Exception) {
                Log.e("MatchesViewModel", "Error marking chat as read", e)
            }
        }
    }

    fun checkAndUpdateExpiredMatches() {
        viewModelScope.launch {
            // Create a new coroutine to collect from the Flow
            matches.collect { matchList ->
                matchList.forEach { matchWithDetails ->
                    if (matchWithDetails.match.isExpired()) {
                        matchRepository.updateMatchStatus(
                            matchWithDetails.match.id,
                            MatchStatus.EXPIRED
                        )
                    }
                }
            }
        }
    }
}