package com.example.pawsomepals.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.DataManager
import com.example.pawsomepals.data.model.Dog
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
    private val auth: FirebaseAuth,
    private val dogProfileViewModel: DogProfileViewModel

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

                // Generate or use existing dogId
                val finalDogId = dogId?.takeIf { it.isNotBlank() && it != "null" }
                    ?: generateDogId(userId)

                Log.d("QuestionnaireViewModel", "Saving responses for dogId: $finalDogId")

                // Save to repository
                val questionnaireResponse = QuestionnaireResponse(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    dogId = finalDogId,
                    responses = responses
                )
                questionRepository.saveQuestionnaireResponse(questionnaireResponse)

                // Create initial dog profile with the same dogId
                if (dogId == null || dogId == "null" || dogId.isBlank()) {
                    createInitialDogProfile(userId, finalDogId, responses)

                    // Set this dog as current in DogProfileViewModel
                    dogProfileViewModel.setCurrentDog(finalDogId)
                }

                // Save responses to DataManager with the same dogId
                dataManager.saveQuestionnaireResponses(userId, finalDogId, responses)
                _questionnaireResponses.value = responses
                _completionStatus.value = true

                Log.d("QuestionnaireViewModel", "Successfully saved all data for dogId: $finalDogId")
            } catch (e: Exception) {
                Log.e("QuestionnaireViewModel", "Error saving questionnaire", e)
                _error.value = "Failed to save questionnaire: ${e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }
    private fun generateDogId(userId: String): String {
        val timestamp = System.currentTimeMillis()
        val random = (1000..9999).random() // 4-digit random number
        return "DOG_${userId.take(6)}_${timestamp}_$random"
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
    private suspend fun createInitialDogProfile(userId: String, dogId: String, responses: Map<String, String>) {
        try {
            val dogProfile = Dog(
                id = dogId,
                ownerId = userId,
                name = responses["dogName"] ?: "Unnamed Dog",
                breed = responses["breed"] ?: "Unknown",
                age = responses["age"]?.toIntOrNull() ?: 0,
                gender = responses["gender"] ?: "Unknown",
                size = responses["size"] ?: "",
                energyLevel = responses["energyLevel"] ?: "Medium",
                friendliness = responses["friendliness"] ?: "Medium",
                profilePictureUrl = null,  // Will be set later when user adds photo
                isSpayedNeutered = responses["isSpayedNeutered"],
                friendlyWithDogs = responses["friendlyWithDogs"],
                friendlyWithChildren = responses["friendlyWithChildren"],
                specialNeeds = responses["specialNeeds"],
                favoriteToy = responses["favoriteToy"],
                preferredActivities = responses["preferredActivities"],
                walkFrequency = responses["walkFrequency"],
                favoriteTreat = responses["favoriteTreat"],
                trainingCertifications = responses["trainingCertifications"],
                trainability = responses["trainability"],
                friendlyWithStrangers = responses["friendlyWithStrangers"],
                exerciseNeeds = responses["exerciseNeeds"],
                groomingNeeds = responses["groomingNeeds"],
                weight = responses["weight"]?.toDoubleOrNull(),
                photoUrls = List(6) { null },  // Initialize with 6 empty photo slots
                latitude = null,  // Location will be set later
                longitude = null,
                achievements = emptyList()  // Initialize with no achievements
            )

            // Log the profile creation attempt
            Log.d("QuestionnaireViewModel", "Creating dog profile: " +
                    "id=$dogId, name=${dogProfile.name}, breed=${dogProfile.breed}")

            // Save the profile
            dataManager.createOrUpdateDogProfile(dogProfile)

            Log.d("QuestionnaireViewModel", "Successfully created initial dog profile with ID: $dogId")

        } catch (e: Exception) {
            Log.e("QuestionnaireViewModel", "Error creating initial dog profile: ${e.message}")
            Log.e("QuestionnaireViewModel", "Stack trace: ${e.stackTraceToString()}")
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