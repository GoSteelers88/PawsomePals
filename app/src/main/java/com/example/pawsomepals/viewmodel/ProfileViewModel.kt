package com.example.pawsomepals.viewmodel

import android.content.Context
import androidx.lifecycle.viewModelScope  // Add this import
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.DataManager
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.model.User
import com.example.pawsomepals.data.repository.UserRepository
import com.example.pawsomepals.service.LocationService
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val locationService: LocationService,
    private val storage: FirebaseStorage,
    private val dataManager: DataManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val firestore = Firebase.firestore
    private val auth = Firebase.auth



    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile

    private val _dogProfile = MutableStateFlow<Dog?>(null)
    val dogProfile: StateFlow<Dog?> = _dogProfile

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _uploadProgress = MutableStateFlow<Float>(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _userDogs = MutableStateFlow<List<Dog>>(emptyList())
    val userDogs: StateFlow<List<Dog>> = _userDogs

    private val _questionnaireResponses =
        MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val questionnaireResponses: StateFlow<Map<String, Map<String, String>>> =
        _questionnaireResponses.asStateFlow()


    fun loadProfileById(userId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                dataManager.observeUserProfile(userId).collect { user ->
                    _userProfile.value = user
                }
                dataManager.observeUserDogs(userId).collect { dogs ->
                    _userDogs.value = dogs
                    // Fetch questionnaire responses for each dog
                    val allResponses = mutableMapOf<String, Map<String, String>>()
                    dogs.forEach { dog ->
                        val responses = dataManager.getQuestionnaireResponses(userId, dog.id)
                        if (responses != null) {
                            allResponses[dog.id] = responses
                        }
                    }
                    _questionnaireResponses.value = allResponses
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading profiles: ${e.message}")
                _error.value = "Failed to load profiles: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    private fun setupUserProfileListener(userId: String) {
        firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("ProfileViewModel", "Listen failed for user profile", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    Log.d("ProfileViewModel", "User profile snapshot received: ${user?.username}")
                    _userProfile.value = user
                } else {
                    Log.w("ProfileViewModel", "User profile snapshot is null or doesn't exist")
                }
            }
    }




    fun loadQuestionnaireResponses(userId: String, dogId: String) {
        viewModelScope.launch {
            try {
                val responses = dataManager.getQuestionnaireResponses(userId, dogId)
                val currentResponses = _questionnaireResponses.value.toMutableMap()
                if (responses != null) {
                    currentResponses[dogId] = responses
                    Log.d("ProfileViewModel", "Loaded responses: $responses")
                } else {
                    currentResponses.remove(dogId)
                }
                _questionnaireResponses.value = currentResponses
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading questionnaire responses", e)
            }
        }
    }


    fun observeQuestionnaireResponses(userId: String, dogId: String) {
        viewModelScope.launch {
            dataManager.observeQuestionnaireResponses(userId, dogId)
                .catch { e ->
                    Log.e("ProfileViewModel", "Error observing questionnaire responses", e)
                    emit(emptyMap())
                }
                .collect { responses ->
                    val currentResponses = _questionnaireResponses.value.toMutableMap()
                    currentResponses[dogId] = responses
                    _questionnaireResponses.value = currentResponses
                }
        }
    }


    fun createOrUpdateDogProfile(dogData: Map<String, String>) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val dogId = dogData["id"] ?: dataManager.generateDogId()
                val updatedDog = Dog(
                    id = dogId,
                    ownerId = _userProfile.value?.id ?: "",
                    name = dogData["name"] ?: "",
                    breed = dogData["breed"] ?: "",
                    age = dogData["age"]?.toIntOrNull() ?: 0,
                    gender = dogData["gender"] ?: "",
                    size = dogData["size"] ?: "",
                    energyLevel = dogData["energyLevel"] ?: "",
                    friendliness = dogData["friendliness"] ?: "",
                    // Initialize other fields as necessary
                )

                dataManager.createOrUpdateDogProfile(updatedDog)
                // The observer will update _userDogs
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating dog profile: ${e.message}")
                _error.value = "Failed to update dog profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }



    fun fetchUserDogs() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid
                Log.d("ProfileViewModel", "Fetching dogs for user: $userId")
                if (userId != null) {
                    val dogs = dataManager.getUserDogs(userId)
                    Log.d("ProfileViewModel", "Fetched dogs: ${dogs.size}")
                    _userDogs.value = dogs
                } else {
                    Log.e("ProfileViewModel", "No authenticated user found")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error fetching user dogs: ${e.message}")
            }
        }
    }

    fun updateUserProfile(user: User) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                withContext(Dispatchers.IO) {
                    dataManager.updateUserProfile(user)
                }
                _userProfile.value = user
                Log.d("ProfileViewModel", "User profile updated successfully: ${user.id}")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating user profile", e)
                _error.value = "Failed to update profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

    fun updateUserProfilePicture(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val user = auth.currentUser
                if (user == null) {
                    _error.value = "User not authenticated"
                    return@launch
                }

                Log.d("ProfileViewModel", "Starting profile picture update")
                val downloadUrl = dataManager.updateUserProfilePicture(uri, user.uid)

                _userProfile.value?.let { currentUser ->
                    val updatedUser = currentUser.copy(profilePictureUrl = downloadUrl)
                    _userProfile.value = updatedUser
                    withContext(Dispatchers.IO) {
                        dataManager.updateUserProfile(updatedUser)
                    }
                    Log.d("ProfileViewModel", "Profile picture updated successfully")
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating profile picture", e)
                _error.value = "Failed to update profile picture: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun loadDogProfile(dogId: String) {
        viewModelScope.launch {
            try {
                val dog = getDogById(dogId)
                _dogProfile.value = dog
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading dog profile: ${e.message}")
            }
        }
    }

    suspend fun getDogById(dogId: String): Dog? {
        return try {
            firestore.collection("dogs").document(dogId).get().await().toObject(Dog::class.java)
        } catch (e: Exception) {
            Log.e("DataManager", "Error fetching dog by ID", e)
            null
        }
    }

    fun refreshProfiles() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                dataManager.syncDogProfile(userId)
                _userProfile.value = dataManager.getLatestUserProfile(userId)
                _dogProfile.value = dataManager.getLatestDogProfile(userId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateDogProfile(dogId: String, dogData: Map<String, String>) {
        viewModelScope.launch {
            try {
                val currentDog = _userDogs.value.find { it.id == dogId } ?: return@launch

                val updatedDog = currentDog.copy(
                    name = dogData["name"] ?: currentDog.name,
                    breed = dogData["breed"] ?: currentDog.breed,
                    age = dogData["age"]?.toIntOrNull() ?: currentDog.age,
                    gender = dogData["gender"] ?: currentDog.gender,
                    size = dogData["size"] ?: currentDog.size,
                    energyLevel = dogData["energyLevel"] ?: currentDog.energyLevel,
                    friendliness = dogData["friendliness"] ?: currentDog.friendliness,
                    isSpayedNeutered = dogData["isSpayedNeutered"],  // Changed this line
                    friendlyWithDogs = dogData["friendlyWithDogs"] ?: currentDog.friendlyWithDogs,
                    friendlyWithChildren = dogData["friendlyWithChildren"]
                        ?: currentDog.friendlyWithChildren,
                    friendlyWithStrangers = dogData["friendlyWithStrangers"]
                        ?: currentDog.friendlyWithStrangers,
                    specialNeeds = dogData["specialNeeds"] ?: currentDog.specialNeeds,
                    favoriteToy = dogData["favoriteToy"] ?: currentDog.favoriteToy,
                    preferredActivities = dogData["preferredActivities"]
                        ?: currentDog.preferredActivities,
                    walkFrequency = dogData["walkFrequency"] ?: currentDog.walkFrequency,
                    favoriteTreat = dogData["favoriteTreat"] ?: currentDog.favoriteTreat,
                    trainingCertifications = dogData["trainingCertifications"]
                        ?: currentDog.trainingCertifications,
                    trainability = dogData["trainability"] ?: currentDog.trainability,
                    exerciseNeeds = dogData["exerciseNeeds"] ?: currentDog.exerciseNeeds,
                    groomingNeeds = dogData["groomingNeeds"] ?: currentDog.groomingNeeds,
                    weight = dogData["weight"]?.toDoubleOrNull() ?: currentDog.weight,
                    photoUrls = dogData["photoUrls"]?.split(",") ?: currentDog.photoUrls,
                    latitude = dogData["latitude"]?.toDoubleOrNull() ?: currentDog.latitude,
                    longitude = dogData["longitude"]?.toDoubleOrNull() ?: currentDog.longitude
                )

                dataManager.createOrUpdateDogProfile(updatedDog)
                // The observer will update _userDogs
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating dog profile: ${e.message}")
                _error.value = "Failed to update dog profile: ${e.message}"
            }
        }
    }

    fun deleteDogProfile(dogId: String) {
        viewModelScope.launch {
            try {
                dataManager.deleteDogProfile(dogId)
                // The observer will update _userDogs
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error deleting dog profile: ${e.message}")
            }
        }
    }

    private fun setupDogProfileListener(userId: String) {
        Log.d("ProfileViewModel", "Setting up dog profile listener for userId: $userId")
        firestore.collection("dogs")
            .whereEqualTo("ownerId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("ProfileViewModel", "Listen failed for dog profile", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val dog = snapshot.documents.firstOrNull()?.toObject(Dog::class.java)
                    Log.d("ProfileViewModel", "Dog profile snapshot received: ${dog?.name}")
                    _dogProfile.value = dog
                } else {
                    Log.w("ProfileViewModel", "Dog profile snapshot is null or empty")
                    _dogProfile.value = null  // Set to null if no dog profile found
                }
            }
    }

    fun updateDogProfilePicture(index: Int, uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _dogProfile.value?.let { currentDog ->
                    val downloadUrl = dataManager.updateDogProfilePicture(index, uri, currentDog.id)

                    // Update the local dog profile with the new image URL
                    val updatedPhotoUrls = currentDog.photoUrls.toMutableList()
                    if (index < updatedPhotoUrls.size) {
                        updatedPhotoUrls[index] = downloadUrl
                    } else {
                        updatedPhotoUrls.add(downloadUrl)
                    }

                    val updatedDog = currentDog.copy(photoUrls = updatedPhotoUrls)
                    dataManager.createOrUpdateDogProfile(updatedDog)
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating dog profile picture: ${e.message}")
                _error.value = "Failed to update dog profile picture: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }




    fun getOutputFileUri(context: Context): Uri {
        return dataManager.getOutputFileUri(context)
    }
}
