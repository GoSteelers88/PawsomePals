package io.pawsomepals.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val dogProfileRepository: DogProfileRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _questionnaireResponses = MutableStateFlow<Map<String, String>>(emptyMap())
    val questionnaireResponses: StateFlow<Map<String, String>> = _questionnaireResponses

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _completionStatus = MutableStateFlow(false)
    val completionStatus: StateFlow<Boolean> = _completionStatus

    private val _currentDog = MutableStateFlow<Dog?>(null)
    val currentDog: StateFlow<Dog?> = _currentDog


    private val _isAuthenticated = MutableStateFlow(false)
    // Helper functions to parse and map values
    private fun parseAge(ageRange: String?): Int {
        return when (ageRange) {
            "1-3 years" -> 2
            "4-7 years" -> 5
            "8+ years" -> 8
            else -> 0
        }
    }

    init {
        viewModelScope.launch {
            auth.addAuthStateListener { firebaseAuth ->
                _isAuthenticated.value = firebaseAuth.currentUser != null
            }
        }
    }

    private fun parseWeight(weight: String?): Double? {
        return weight?.toDoubleOrNull()
    }

    private fun mapEnergyLevel(level: String?): String {
        return when (level?.lowercase()) {
            "low" -> "Low"
            "high" -> "High"
            else -> "Medium"
        }
    }

    private fun mapFriendliness(level: String?): String {
        return when (level?.lowercase()) {
            "friendly" -> "Friendly"
            "shy" -> "Shy"
            else -> "Moderate"
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

                // Verify user exists in Firestore
                val userDoc = firestore.collection("users")
                    .document(userId)
                    .get()
                    .await()

                if (!userDoc.exists()) {
                    _error.value = "User profile not found. Please try again."
                    return@launch
                }

                // Generate new dogId or use existing one
                val finalDogId = dogId?.takeIf { it.isNotBlank() && it != "null" }
                    ?: generateDogId(userId)

                Log.d("QuestionnaireViewModel", "Processing dog profile: $finalDogId")

                withContext(Dispatchers.IO) {
                    try {
                        // Create or update dog profile
                        val dog = Dog(
                            id = finalDogId,
                            ownerId = userId,
                            name = responses["dog_name"] ?: "Unnamed Dog",
                            breed = responses["dog_breed"] ?: "Unknown",
                            age = parseAge(responses["dog_age"]),
                            gender = responses["dog_gender"] ?: "Unknown",
                            size = responses["dog_size"] ?: "",
                            energyLevel = mapEnergyLevel(responses["dog_energy"]),
                            friendliness = mapFriendliness(responses["dog_friendliness"]),
                            isSpayedNeutered = responses["dog_neutered"],
                            friendlyWithDogs = responses["dog_friendly_dogs"],
                            friendlyWithChildren = responses["dog_friendly_children"],
                            friendlyWithStrangers = responses["dog_friendly_strangers"],
                            specialNeeds = responses["dog_special_needs"],
                            favoriteToy = responses["dog_favorite_toy"],
                            preferredActivities = responses["dog_preferred_activities"],
                            walkFrequency = responses["dog_walk_frequency"],
                            favoriteTreat = responses["dog_favorite_treat"],
                            trainingCertifications = responses["dog_training_certifications"],
                            trainability = responses["dog_trainability"],
                            exerciseNeeds = responses["dog_exercise_needs"],
                            groomingNeeds = responses["dog_grooming_needs"],
                            weight = parseWeight(responses["dog_weight"]),
                            photoUrls = List(6) { null }
                        )



// Add logging to debug the responses and created dog
                        Log.d("QuestionnaireViewModel", "Questionnaire Responses: $responses")
                        Log.d("QuestionnaireViewModel", "Created Dog Object: $dog")

                        // Save dog profile
                        dogProfileRepository.createOrUpdateDogProfile(dog)

                        // If this is a new dog, update user's dog list and questionnaire status
                        if (dogId == null || dogId.isBlank() || dogId == "null") {
                            // Update user's dog IDs list
                            val currentUser = userRepository.getUserById(userId)
                            currentUser?.let { user ->
                                val updatedUser = user.copy(
                                    hasCompletedQuestionnaire = true,
                                    dogIds = user.dogIds + finalDogId
                                )
                                userRepository.updateUser(updatedUser)
                            }

                            // Set as current dog in UI
                            withContext(Dispatchers.Main) {
                                setCurrentDog(finalDogId)  // Use our own setCurrentDog method
                            }
                        }

                        withContext(Dispatchers.Main) {
                            _questionnaireResponses.value = responses
                            _completionStatus.value = true
                        }

                        Log.d("QuestionnaireViewModel", "Successfully saved dog profile: $finalDogId")

                    } catch (e: Exception) {
                        Log.e("QuestionnaireViewModel", "Error saving dog profile", e)
                        throw e
                    }
                }
            } catch (e: Exception) {
                Log.e("QuestionnaireViewModel", "Error in questionnaire processing", e)
                _error.value = when {
                    e.message?.contains("permission-denied", ignoreCase = true) == true ->
                        "Permission denied. Please try again."
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Network error. Please check your connection."
                    else -> "Failed to save questionnaire: ${e.message}"
                }
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    private suspend fun setCurrentDog(dogId: String) {
        try {
            dogProfileRepository.getDogProfile(dogId).collect { result ->
                result.fold(
                    onSuccess = { dog ->
                        _currentDog.value = dog
                        Log.d("QuestionnaireViewModel", "Successfully set current dog: ${dog?.id}")
                    },
                    onFailure = { error ->
                        Log.e("QuestionnaireViewModel", "Error setting current dog", error)
                        _error.value = "Failed to set current dog: ${error.message}"
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("QuestionnaireViewModel", "Error setting current dog", e)
            _error.value = "Failed to set current dog: ${e.message}"
        }
    }


    private fun generateDogId(userId: String): String {
        val timestamp = System.currentTimeMillis()
        val random = (1000..9999).random()
        return "DOG_${userId.take(6)}_${timestamp}_$random"
    }

    private suspend fun updateUserQuestionnaireStatus(userId: String, completed: Boolean) {
        try {
            firestore.collection("users")
                .document(userId)
                .update("hasCompletedQuestionnaire", completed)
                .await()

            userRepository.getUserById(userId)?.let {
                val updatedUser = it.copy(hasCompletedQuestionnaire = completed)
                userRepository.updateUser(updatedUser)
            }

            _completionStatus.value = completed
            Log.d("QuestionnaireViewModel", "Successfully updated questionnaire status: $completed")
        } catch (e: Exception) {
            Log.e("QuestionnaireViewModel", "Error updating questionnaire status", e)
            _error.value = "Warning: Status update failed, but your responses were saved."
        }
    }
    // In QuestionnaireViewModel.kt
    fun loadExistingDogProfile(dogId: String) {
        viewModelScope.launch {
            try {
                _isSubmitting.value = true
                _error.value = null

                // Fetch dog profile
                val dog = dogProfileRepository.getDogProfileById(dogId)
                    ?.getOrNull()
                    ?: throw IllegalStateException("Dog profile not found")

                // Convert dog profile to questionnaire answers
                val responses = mapOf(
                    "dogName" to (dog.name ?: ""),
                    "breed" to (dog.breed ?: ""),
                    "age" to (dog.age?.toString() ?: ""),
                    "gender" to (dog.gender ?: ""),
                    "size" to (dog.size ?: ""),
                    "energyLevel" to (dog.energyLevel ?: "Medium"),
                    "friendliness" to (dog.friendliness ?: "Medium"),
                    "isSpayedNeutered" to (dog.isSpayedNeutered ?: ""),
                    "friendlyWithDogs" to (dog.friendlyWithDogs ?: ""),
                    "friendlyWithChildren" to (dog.friendlyWithChildren ?: ""),
                    "specialNeeds" to (dog.specialNeeds ?: ""),
                    "favoriteToy" to (dog.favoriteToy ?: ""),
                    "preferredActivities" to (dog.preferredActivities ?: ""),
                    "walkFrequency" to (dog.walkFrequency ?: ""),
                    "favoriteTreat" to (dog.favoriteTreat ?: ""),
                    "trainingCertifications" to (dog.trainingCertifications ?: ""),
                    "trainability" to (dog.trainability ?: ""),
                    "friendlyWithStrangers" to (dog.friendlyWithStrangers ?: ""),
                    "exerciseNeeds" to (dog.exerciseNeeds ?: ""),
                    "groomingNeeds" to (dog.groomingNeeds ?: ""),
                    "weight" to (dog.weight?.toString() ?: "")
                ).filterValues { it.isNotBlank() }

                _questionnaireResponses.value = responses
                Log.d("QuestionnaireViewModel", "Loaded existing dog profile: $dogId")

            } catch (e: Exception) {
                Log.e("QuestionnaireViewModel", "Error loading dog profile", e)
                _error.value = when {
                    e.message?.contains("not found", ignoreCase = true) == true ->
                        "Dog profile not found. Please try again."
                    e.message?.contains("permission", ignoreCase = true) == true ->
                        "You don't have permission to view this profile."
                    else -> "Failed to load dog profile: ${e.message}"
                }
            } finally {
                _isSubmitting.value = false
            }
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
}