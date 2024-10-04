package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.ai.AIFeatures
import com.example.pawsomepals.data.repository.UserRepository
import com.example.pawsomepals.subscription.SubscriptionManager
import com.example.pawsomepals.subscription.SubscriptionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HealthAdvisorViewModel @Inject constructor(
    private val aiFeatures: AIFeatures,
    private val userRepository: UserRepository,
    private val subscriptionManager: SubscriptionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthAdvisorUiState())
    val uiState: StateFlow<HealthAdvisorUiState> = _uiState.asStateFlow()

    private val _healthAdvice = MutableStateFlow<String?>(null)
    val healthAdvice: StateFlow<String?> = _healthAdvice

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _pastQuestions = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val pastQuestions: StateFlow<List<Pair<String, String>>> = _pastQuestions

    private val _subscriptionStatus = MutableStateFlow<SubscriptionStatus>(SubscriptionStatus.NOT_SUBSCRIBED)
    val subscriptionStatus: StateFlow<SubscriptionStatus> = _subscriptionStatus

    private val _remainingQuestions = MutableStateFlow(0)
    val remainingQuestions: StateFlow<Int> = _remainingQuestions

    init {
        viewModelScope.launch {
            initializeState()
        }
    }

    private suspend fun initializeState() {
        val userId = userRepository.getCurrentUserId()
        if (userId != null) {
            _uiState.value = _uiState.value.copy(
                subscriptionStatus = subscriptionManager.getSubscriptionStatus(userId).first(),
                remainingQuestions = subscriptionManager.getRemainingQuestions(userId),
                pastQuestions = aiFeatures.getUserQuestions(userId)
            )
        } else {
            _uiState.value = _uiState.value.copy(
                subscriptionStatus = SubscriptionStatus.NOT_SUBSCRIBED,
                remainingQuestions = 0,
                pastQuestions = emptyList(),
                error = "User not logged in"
            )
        }
    }

    fun askHealthQuestion(question: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val userId = userRepository.getCurrentUserId()
                if (userId == null) {
                    _uiState.value = _uiState.value.copy(error = "User not logged in")
                    return@launch
                }

                val dogProfile = userRepository.getCurrentUserDog()
                if (dogProfile == null) {
                    _uiState.value = _uiState.value.copy(error = "No dog profile found for current user")
                    return@launch
                }

                if ((_uiState.value.subscriptionStatus == SubscriptionStatus.ACTIVE) || (_uiState.value.remainingQuestions > 0)) {
                    val advice = aiFeatures.getHealthAdvice(userId, question, dogProfile)
                    handleSuccessfulResponse(userId, question, advice)
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "You've reached your daily question limit. Please subscribe for unlimited questions."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private suspend fun handleSuccessfulResponse(userId: String, question: String, advice: String?) {
        val updatedPastQuestions = _uiState.value.pastQuestions.toMutableList()
        updatedPastQuestions.add(0, question to (advice ?: "No advice available"))

        _uiState.value = _uiState.value.copy(
            healthAdvice = advice ?: "Unable to get health advice at this time.",
            pastQuestions = updatedPastQuestions,
            remainingQuestions = if (_uiState.value.subscriptionStatus != SubscriptionStatus.ACTIVE) {
                subscriptionManager.decrementRemainingQuestions(userId)
            } else {
                _uiState.value.remainingQuestions
            }
        )
    }

    fun clearCurrentAdvice() {
        _uiState.value = _uiState.value.copy(healthAdvice = null)
    }

    fun refreshSubscriptionStatus() {
        viewModelScope.launch {
            val userId = userRepository.getCurrentUserId()
            if (userId != null) {
                val newStatus = subscriptionManager.getSubscriptionStatus(userId).first()
                _uiState.value = _uiState.value.copy(
                    subscriptionStatus = newStatus,
                    remainingQuestions = subscriptionManager.getRemainingQuestions(userId)
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    subscriptionStatus = SubscriptionStatus.NOT_SUBSCRIBED,
                    remainingQuestions = 0,
                    error = "User not logged in"
                )
            }
        }
    }

data class HealthAdvisorUiState(
    val healthAdvice: String? = null,
    val isLoading: Boolean = false,
    val pastQuestions: List<Pair<String, String>> = emptyList(),
    val subscriptionStatus: SubscriptionStatus = SubscriptionStatus.NOT_SUBSCRIBED,
    val remainingQuestions: Int = 0,
    val error: String? = null
)}