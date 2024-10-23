package com.example.pawsomepals.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.DataManager
import com.example.pawsomepals.data.model.QuestionnaireResponse
import com.example.pawsomepals.data.repository.QuestionRepository
import com.example.pawsomepals.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.UUID

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val questionRepository: QuestionRepository,
    private val dataManager: DataManager,
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _questionnaireResponses = MutableStateFlow<Map<String, String>>(emptyMap())
    val questionnaireResponses: StateFlow<Map<String, String>> = _questionnaireResponses

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _completionStatus = MutableStateFlow(false)
    val completionStatus: StateFlow<Boolean> = _completionStatus

    init {
        viewModelScope.launch {
            // Check if there's an authenticated user
            auth.currentUser?.let { user ->
                checkQuestionnaireStatus(user.uid)
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun setErrorMessage(message: String?) {
        _error.value = message
    }

    fun saveQuestionnaireResponses(userId: String, dogId: String?, responses: Map<String, String>) {
        viewModelScope.launch {
            try {
                _isSubmitting.value = true
                _error.value = null

                Log.d("QuestionnaireViewModel", "Saving responses for userId: $userId, dogId: $dogId")

                // Save questionnaire responses
                val questionnaireResponse = QuestionnaireResponse(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    dogId = dogId ?: "",
                    responses = responses
                )
                questionRepository.saveQuestionnaireResponse(questionnaireResponse)

                // Update user completion status for initial questionnaire
                if (dogId == null || dogId == "null" || dogId.isBlank()) {
                    Log.d("QuestionnaireViewModel", "Updating user questionnaire status")
                    updateUserQuestionnaireStatus(userId, true)
                    _completionStatus.value = true
                }

                // Update dog profile if needed
                if (!dogId.isNullOrBlank() && dogId != "null") {
                    dataManager.saveQuestionnaireResponses(userId, dogId, responses)
                }

                _questionnaireResponses.value = responses

                Log.d("QuestionnaireViewModel", "Successfully saved responses")

            } catch (e: Exception) {
                Log.e("QuestionnaireViewModel", "Error saving questionnaire", e)
                _error.value = "Failed to save questionnaire: ${e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }
    private suspend fun updateUserQuestionnaireStatus(userId: String, completed: Boolean) {
        try {
            // Update in Firestore
            val user = userRepository.getUserById(userId)
            user?.let {
                val updatedUser = it.copy(hasCompletedQuestionnaire = completed)
                userRepository.updateUser(updatedUser)

                // Also update the local completion status
                _completionStatus.value = completed
            }
        } catch (e: Exception) {
            Log.e("QuestionnaireViewModel", "Error updating questionnaire status", e)
            throw e
        }
    }
    fun checkQuestionnaireStatus(userId: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getUserById(userId)
                _completionStatus.value = user?.hasCompletedQuestionnaire ?: false
            } catch (e: Exception) {
                Log.e("QuestionnaireViewModel", "Error checking questionnaire status", e)
            }
        }
    }
    fun loadQuestionnaireResponses(userId: String, dogId: String) {
        viewModelScope.launch {
            try {
                val responses = questionRepository.getQuestionnaireResponse(userId, dogId)?.responses ?: emptyMap()
                _questionnaireResponses.value = responses
            } catch (e: Exception) {
                _error.value = "Failed to load responses: ${e.message}"
            }
        }
    }

    suspend fun loadExistingAnswers(userId: String, dogId: String): Map<String, String> {
        return try {
            // Try to load from DataManager first (as it includes the merged data)
            dataManager.getQuestionnaireResponses(userId, dogId)
                ?: questionRepository.getQuestionnaireResponse(userId, dogId)?.responses
                ?: emptyMap()
        } catch (e: Exception) {
            _error.value = "Failed to load existing answers: ${e.message}"
            emptyMap()
        }
    }
}