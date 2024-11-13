package io.pawsomepals.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pawsomepals.app.data.model.Match
import io.pawsomepals.app.data.model.MatchStatus
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.MatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MatchesViewModel @Inject constructor(
    private val matchRepository: MatchRepository,
    private val dogProfileRepository: DogProfileRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // Separate flows for different match types
    private val activeMatches = matchRepository.getActiveMatches()
    private val pendingMatches = matchRepository.getPendingMatches()

    // Combine active and pending matches with dog profiles
    val matches = combine(
        activeMatches,
        pendingMatches
    ) { active, pending ->
        _isLoading.value = true
        try {
            val activeResult = active.getOrDefault(emptyList())
            val pendingResult = pending.getOrDefault(emptyList())

            // Sort matches by priority and timestamp
            (activeResult + pendingResult).sortedWith(
                compareByDescending<Match> { it.matchType.priorityLevel }
                    .thenByDescending { it.timestamp }
            )
        } finally {
            _isLoading.value = false
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun acceptMatch(matchId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                matchRepository.updateMatchStatus(matchId, MatchStatus.ACTIVE)
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

    // For managing expired matches
    fun checkAndUpdateExpiredMatches() {
        viewModelScope.launch {
            matches.value
                .filter { it.isExpired() }
                .forEach { match ->
                    matchRepository.updateMatchStatus(match.id, MatchStatus.EXPIRED)
                }
        }
    }
}