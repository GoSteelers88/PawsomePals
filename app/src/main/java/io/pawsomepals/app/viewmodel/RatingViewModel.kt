package io.pawsomepals.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.model.Rating
import io.pawsomepals.app.data.repository.RatingRepository
import io.pawsomepals.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RatingViewModel @Inject constructor(
    private val ratingRepository: RatingRepository,
    private val userRepository: UserRepository

) : ViewModel() {

    private val _ratingState = MutableStateFlow<RatingState>(RatingState.Idle)
    val ratingState: StateFlow<RatingState> = _ratingState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun submitRating(rating: Rating) {
        viewModelScope.launch {
            _ratingState.value = RatingState.Loading
            try {
                ratingRepository.submitRating(rating)
                _ratingState.value = RatingState.Success("Rating submitted successfully")
            } catch (e: Exception) {
                _ratingState.value = RatingState.Error("Failed to submit rating: ${e.message}")
                _errorMessage.value = "Failed to submit rating: ${e.message}"
            }
        }
    }
    fun getCurrentUserId(): String? {
        return userRepository.getCurrentUserId() // Implement this method in UserRepository
    }

    fun getUserRatings(userId: String) {
        viewModelScope.launch {
            _ratingState.value = RatingState.Loading
            try {
                ratingRepository.getUserRatings(userId).collect { ratings ->
                    _ratingState.value = RatingState.RatingsLoaded(ratings)
                }
            } catch (e: Exception) {
                _ratingState.value = RatingState.Error("Failed to load ratings: ${e.message}")
                _errorMessage.value = "Failed to load ratings: ${e.message}"
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun updateErrorMessage(error: String) {
        _errorMessage.value = error
    }

    fun resetState() {
        _ratingState.value = RatingState.Idle
        _errorMessage.value = null
    }

    sealed class RatingState {
        object Idle : RatingState()
        object Loading : RatingState()
        data class Success(val message: String) : RatingState()
        data class RatingsLoaded(val ratings: List<Rating>) : RatingState()
        data class Error(val message: String) : RatingState()
    }
}