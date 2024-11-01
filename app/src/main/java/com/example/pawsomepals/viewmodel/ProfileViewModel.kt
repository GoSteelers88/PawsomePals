package com.example.pawsomepals.viewmodel

import android.app.Application
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.FileProvider
import com.example.pawsomepals.utils.CameraUtils
import com.example.pawsomepals.utils.ImageHandler
import com.example.pawsomepals.utils.NetworkUtils
import dagger.hilt.android.internal.Contexts.getApplication
import kotlinx.coroutines.delay

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val application: Application,  // Add this
    private val userRepository: UserRepository,
    private val locationService: LocationService,
    private val storage: FirebaseStorage,
    private val dataManager: DataManager,
    private val savedStateHandle: SavedStateHandle,
    private val imageHandler: ImageHandler, // Replace cameraUtils with imageHandler
    private val networkUtils: NetworkUtils  // Add this



) : ViewModel() {

    // Companion object with constants
    companion object {
        private const val PROFILE_PICTURES_DIR = "profile_pictures"
        private const val DOG_PHOTOS_DIR = "dog_photos"
        private const val TEMP_PHOTOS_DIR = "temp_photos"
        private const val TEMP_FILE_MAX_AGE = 24 * 60 * 60 * 1000 // 24 hours in milliseconds

        enum class FileType {
            PROFILE,
            DOG,
            TEMPORARY
        }
    }

    private val firestore = Firebase.firestore
    private val auth = Firebase.auth

    // StateFlow declarations
    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile.asStateFlow()

    private val _dogProfile = MutableStateFlow<Dog?>(null)
    val dogProfile: StateFlow<Dog?> = _dogProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float>(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    private val _userDogs = MutableStateFlow<List<Dog>>(emptyList())
    val userDogs: StateFlow<List<Dog>> = _userDogs.asStateFlow()

    private val _currentDog = MutableStateFlow<Dog?>(null)
    val currentDog: StateFlow<Dog?> = _currentDog.asStateFlow()

    // Add this to your StateFlow declarations section
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


    fun getOutputFileUri(isProfile: Boolean = false): Uri {
        return try {
            imageHandler.createImageFile(isProfile).second
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Failed to create image file", e)
            throw e
        }
    }



    fun setCurrentDog(dogId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val dog = _userDogs.value.find { it.id == dogId }
                _currentDog.value = dog

                // Load questionnaire responses for the current dog
                dog?.let {
                    loadQuestionnaireResponses(it.ownerId, it.id)
                }

                Log.d("ProfileViewModel", "Current dog set to: ${dog?.name}")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error setting current dog: ${e.message}")
                _error.value = "Failed to set current dog: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun updateUserProfilePicture(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Process image using imageHandler
                val processedUri = imageHandler.processImage(uri, isProfile = true)

                val user = auth.currentUser ?: throw IllegalStateException("User not authenticated")
                val storageRef = storage.reference
                    .child("profile_pictures")
                    .child(user.uid)
                    .child("profile_${System.currentTimeMillis()}.jpg")

                val uploadTask = storageRef.putFile(processedUri)

                uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                    _uploadProgress.value = progress.toFloat() / 100f
                }.await()

                val downloadUrl = storageRef.downloadUrl.await().toString()

                _userProfile.value?.let { currentUser ->
                    val updatedUser = currentUser.copy(profilePictureUrl = downloadUrl)
                    dataManager.updateUserProfile(updatedUser)
                    _userProfile.value = updatedUser // Make sure this is updating
                }

            } catch (e: Exception) {
                _error.value = "Failed to update profile picture: ${e.message}"
            } finally {
                _isLoading.value = false
                _uploadProgress.value = 0f
                imageHandler.cleanupTempFiles()
            }
        }
    }
    fun cleanupTempFiles() {
        viewModelScope.launch {
            imageHandler.cleanupTempFiles()
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
                    Log.d(
                        "ProfileViewModel",
                        "User profile snapshot received: ${user?.username}"
                    )
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


    fun loadDogProfile(dogId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val dog = getDogById(dogId)
                _dogProfile.value = dog

                // Also load questionnaire responses if available
                dog?.let {
                    loadQuestionnaireResponses(dog.ownerId, dogId)
                }

                Log.d("ProfileViewModel", "Dog profile loaded: ${dog?.name}")
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading dog profile: ${e.message}")
                _error.value = "Failed to load dog profile: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun getDogById(dogId: String): Dog? {
        return try {
            val snapshot = firestore.collection("dogs")
                .document(dogId)
                .get()
                .await()

            if (snapshot.exists()) {
                snapshot.toObject(Dog::class.java)?.also {
                    Log.d("ProfileViewModel", "Retrieved dog: ${it.name}")
                }
            } else {
                Log.d("ProfileViewModel", "No dog found with ID: $dogId")
                null
            }
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error fetching dog by ID: ${e.message}")
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
                    friendlyWithDogs = dogData["friendlyWithDogs"]
                        ?: currentDog.friendlyWithDogs,
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

    suspend fun updateDogProfilePicture(index: Int, uri: Uri) {
        try {
            Log.d("ProfileViewModel", "Starting dog profile picture update. Index: $index, URI: $uri")
            _isLoading.value = true

            // Check network connectivity first
            if (!networkUtils.isNetworkAvailable()) {
                _error.value = "No internet connection. Please check your connection and try again."
                return
            }

            val processedUri = imageHandler.processImage(uri, isProfile = false)
            Log.d("ProfileViewModel", "Image processed. New URI: $processedUri")

            val user = auth.currentUser ?: throw IllegalStateException("User not authenticated")
            val dogId = _currentDog.value?.id ?: throw IllegalStateException("No dog selected")

            // Retry logic for Firebase operations
            var attempts = 0
            val maxAttempts = 3
            var lastException: Exception? = null

            while (attempts < maxAttempts) {
                try {
                    val storageRef = storage.reference
                        .child("dog_photos")
                        .child(user.uid)
                        .child(dogId)
                        .child("profile_${System.currentTimeMillis()}.jpg")

                    val uploadTask = storageRef.putFile(processedUri)

                    uploadTask.addOnProgressListener { taskSnapshot ->
                        val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                        _uploadProgress.value = progress.toFloat() / 100f
                    }.await()

                    val downloadUrl = storageRef.downloadUrl.await().toString()
                    Log.d("ProfileViewModel", "Download URL received: $downloadUrl")

                    _currentDog.value?.let { currentDog ->
                        val updatedPhotoUrls = currentDog.photoUrls.toMutableList()
                        if (updatedPhotoUrls.isEmpty()) {
                            updatedPhotoUrls.add(downloadUrl)
                        } else if (index == 0) {
                            updatedPhotoUrls[0] = downloadUrl
                        } else if (index < updatedPhotoUrls.size) {
                            updatedPhotoUrls[index] = downloadUrl
                        } else {
                            updatedPhotoUrls.add(downloadUrl)
                        }

                        val updatedDog = currentDog.copy(
                            photoUrls = updatedPhotoUrls,
                            profilePictureUrl = downloadUrl // Set the first photo as profile picture
                        )

                        dataManager.createOrUpdateDogProfile(updatedDog)
                        _currentDog.value = updatedDog

                        // Update userDogs list
                        val updatedDogs = _userDogs.value.map {
                            if (it.id == updatedDog.id) updatedDog else it
                        }
                        _userDogs.value = updatedDogs
                    }

                    // If we get here, the operation was successful
                    break
                } catch (e: Exception) {
                    lastException = e
                    attempts++
                    if (attempts == maxAttempts) {
                        throw e
                    }
                    delay(1000L * attempts) // Exponential backoff
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileViewModel", "Error updating dog profile picture", e)
            _error.value = "Failed to update profile picture: ${e.message}"
        } finally {
            _isLoading.value = false
            _uploadProgress.value = 0f
            imageHandler.cleanupTempFiles()
        }
    }
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                application.getDir(TEMP_PHOTOS_DIR, Context.MODE_PRIVATE)?.deleteRecursively()
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error cleaning up in onCleared: ${e.message}")
            }
        }
    }
}

