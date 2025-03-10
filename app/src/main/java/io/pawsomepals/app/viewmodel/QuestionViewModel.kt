package io.pawsomepals.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val dogProfileRepository: DogProfileRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dataManager: DataManager

) : ViewModel() {

    private val _questionnaireResponses = MutableStateFlow<Map<String, String>>(emptyMap())
    val questionnaireResponses: StateFlow<Map<String, String>> = _questionnaireResponses

    private val _isUpdatingStatus = MutableStateFlow(false)


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

    private fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
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
                val finalDogId = dogId?.takeIf { it.isNotBlank() && it != "null" }
                    ?: generateDogId(userId)

                // Save dog profile first
                val dog = createDogFromResponses(finalDogId, userId, responses)
                dogProfileRepository.createOrUpdateDogProfile(dog)

                // Set status only if this is a new profile
                if (dogId == null || dogId.isBlank() || dogId == "null") {
                    // Update Firestore
                    firestore.collection("users")
                        .document(userId)
                        .update("hasCompletedQuestionnaire", true)
                        .await()

                    // Update local user
                    userRepository.getUserById(userId)?.let { user ->
                        userRepository.updateUser(
                            user.copy(
                                hasCompletedQuestionnaire = true,
                                dogIds = user.dogIds + finalDogId
                            )
                        )
                    }

                    _completionStatus.value = true
                }
            } finally {
                _isSubmitting.value = false
            }
        }
    }


    private fun createDogFromResponses(
        dogId: String,
        userId: String,
        responses: Map<String, String>
    ): Dog {
        return Dog(
            id = dogId,
            ownerId = userId,
            name = responses["dog_name"] ?: "Unnamed Dog",
            breed = responses["dog_breed"] ?: "Unknown",
            age = parseAge(responses["dog_age"]),
            gender = responses["dog_gender"] ?: "Unknown",
            size = responses["dog_size"] ?: "",
            energyLevel = mapEnergyLevel(responses["dog_energy"]),
            friendliness = mapFriendliness(responses["dog_friendliness"]),
            profilePictureUrl = null,
            isSpayedNeutered = responses["isSpayedNeutered"]?.toLowerCase() == "true",
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
            latitude = null,
            longitude = null,
            profileComplete = false,
            photoUrls = List(6) { null },
            achievements = emptyList()
        )
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
        if (_isUpdatingStatus.value) return

        try {
            _isUpdatingStatus.value = true

            // Update Firestore first
            firestore.collection("users")
                .document(userId)
                .update("hasCompletedQuestionnaire", completed)
                .await()

            // Then update local
            userRepository.getUserById(userId)?.let { user ->
                val updatedUser = user.copy(hasCompletedQuestionnaire = completed)
                userRepository.updateUser(updatedUser)
                _completionStatus.value = completed
            }

            Log.d("QuestionnaireViewModel", "Successfully updated questionnaire status: $completed")
        } catch (e: Exception) {
            Log.e("QuestionnaireViewModel", "Error updating questionnaire status", e)
            _error.value = "Warning: Status update failed, but your responses were saved."
        } finally {
            _isUpdatingStatus.value = false
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
                    "dog_neutered" to (if (dog.isSpayedNeutered == true) "true" else "false"),
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
                ).filter { it.value.isNotBlank() }  // Changed filterValues to filter with isNotBlank check

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
}
