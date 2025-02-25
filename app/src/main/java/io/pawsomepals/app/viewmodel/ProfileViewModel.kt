package io.pawsomepals.app.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.auth.AuthStateManager
import io.pawsomepals.app.data.DataManager
import io.pawsomepals.app.data.model.Dog
import io.pawsomepals.app.data.model.User
import io.pawsomepals.app.data.repository.DogProfileRepository
import io.pawsomepals.app.data.repository.PhotoRepository
import io.pawsomepals.app.data.repository.UserRepository
import io.pawsomepals.app.service.location.LocationService
import io.pawsomepals.app.utils.CameraManager
import io.pawsomepals.app.utils.NetworkUtils
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
    private val application: Application,  // Add this
    private val userRepository: UserRepository,
    private val locationService: LocationService,
    private val storage: FirebaseStorage,
    private val dataManager: DataManager,
    private val savedStateHandle: SavedStateHandle,
    private val networkUtils: NetworkUtils,
    private val cameraManager: CameraManager, // Add CameraManager
    private val photoRepository: PhotoRepository ,
    private val dogProfileRepository: DogProfileRepository,
    private val firebaseAuth: FirebaseAuth,
    private val authStateManager: AuthStateManager // Add this
// Add this line






) : ViewModel() {
    // Add state type definition
    sealed class DogProfileState {
        object Initial : DogProfileState()
        object Loading : DogProfileState()
        data class Success(val message: String) : DogProfileState()
        data class Error(val message: String) : DogProfileState()
    }

    // Add state flow
    private val _dogProfileState = MutableStateFlow<DogProfileState>(DogProfileState.Initial)
    val dogProfileState: StateFlow<DogProfileState> = _dogProfileState.asStateFlow()

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
    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _cameraState = MutableStateFlow<CameraUIState>(CameraUIState.Initial)
    val cameraState: StateFlow<CameraUIState> = _cameraState.asStateFlow()

    sealed class CameraUIState {
        object Initial : CameraUIState()
        object Preview : CameraUIState()
        data class Error(val message: String) : CameraUIState()
        data class PhotoTaken(val uri: Uri) : CameraUIState()
    }

    // Add this to your StateFlow declarations section
    private val _questionnaireResponses =
        MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val questionnaireResponses: StateFlow<Map<String, Map<String, String>>> =
        _questionnaireResponses.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val currentUser = firebaseAuth.currentUser
                if (currentUser != null) {
                    setupUserProfileListener(currentUser.uid)
                    loadProfileById(currentUser.uid)
                }

                // Single auth state listener
                firebaseAuth.addAuthStateListener { auth ->
                    viewModelScope.launch {
                        val user = auth.currentUser
                        if (user != null) {
                            setupUserProfileListener(user.uid)
                            loadProfileById(user.uid)
                        } else {
                            _userProfile.value = null
                            _dogProfile.value = null
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to initialize: ${e.message}"
            }
        }
    }
    fun loadProfileById(userId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // First try to get immediate profile data
                val immediateProfile = withContext(Dispatchers.IO) {
                    userRepository.getUserById(userId)
                }
                _userProfile.value = immediateProfile

                // Then set up observers for real-time updates
                dataManager.observeUserProfile(userId).collect { user ->
                    _userProfile.value = user
                    _isLoading.value = false  // Move this inside collect
                }

                // Load dogs after user profile
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
                _isLoading.value = false  // Make sure to set loading to false on error
            }
        }
    }


    fun getOutputFileUri(isProfile: Boolean = false): Uri {
        return photoRepository.getOutputFileUri(application)  // Use application instead of context
    }

    fun setCurrentDog(dogId: String) {
        viewModelScope.launch {
            try {
                _dogProfileState.value = DogProfileState.Loading
                // First check the local list
                val localDog = _userDogs.value.find { it.id == dogId }

                if (localDog != null) {
                    _currentDog.value = localDog
                    _dogProfile.value = localDog
                    _dogProfileState.value = DogProfileState.Success("Current dog set successfully")
                    Log.d("DogProfileViewModel", "Set current dog from local list: ${localDog.id}")
                    return@launch
                }

                // If not in local list, fetch from repository
                dogProfileRepository.getDogProfile(dogId).collect { result ->
                    result.fold(
                        onSuccess = { dog ->
                            _currentDog.value = dog
                            _dogProfile.value = dog
                            _dogProfileState.value = DogProfileState.Success("Current dog set successfully")
                            Log.d("DogProfileViewModel", "Successfully set current dog: ${dog?.id}")
                        },
                        onFailure = { error ->
                            Log.e("DogProfileViewModel", "Error setting current dog", error)
                            _dogProfileState.value = DogProfileState.Error("Failed to set current dog: ${error.message}")
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("DogProfileViewModel", "Error setting current dog", e)
                _dogProfileState.value = DogProfileState.Error("Failed to set current dog: ${e.message}")
            }
        }
    }
    fun checkUsernameAvailability(username: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val isAvailable = userRepository.isUsernameAvailable(username)
                if (!isAvailable) {
                    _error.value = "Username is already taken"
                }
            } catch (e: Exception) {
                _error.value = "Failed to check username availability: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCurrentUserProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val userId = auth.currentUser?.uid ?: run {
                    _error.value = "No authenticated user found"
                    return@launch
                }

                loadProfileById(userId)

                // Set up continuous profile updates
                setupUserProfileListener(userId)

                // Load dogs after profile
                dataManager.observeUserDogs(userId).collect { dogs ->
                    _userDogs.value = dogs
                    dogs.forEach { dog ->
                        loadQuestionnaireResponses(userId, dog.id)
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load profile: ${e.message}"
                Log.e("ProfileViewModel", "Error loading profile", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun capturePhoto() {
        viewModelScope.launch {
            try {
                if (_userProfile.value == null) {
                    _cameraState.value = CameraUIState.Error("Please wait for profile to load")
                    return@launch
                }

                val photoUri = cameraManager.capturePhoto()
                photoUri?.let {
                    _cameraState.value = CameraUIState.PhotoTaken(it)
                    updateUserProfilePicture(it)
                }
            } catch (e: Exception) {
                _cameraState.value = CameraUIState.Error(e.message ?: "Failed to capture photo")
            }
        }
    }


    fun updateUserProfilePicture(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val authUser = auth.currentUser ?: throw IllegalStateException("User not authenticated")

                if (_userProfile.value == null) {
                    val userProfile = withContext(Dispatchers.IO) {
                        userRepository.getUserById(authUser.uid)
                    } ?: throw IllegalStateException("No user profile loaded")
                    _userProfile.value = userProfile
                }

                // Process the image using PhotoRepository
                val processedUri = withContext(Dispatchers.IO) {
                    photoRepository.compressImage(uri, 1024) // Use appropriate max dimension
                }

                val timestamp = System.currentTimeMillis()
                val storageRef = storage.reference
                    .child(PROFILE_PICTURES_DIR)
                    .child(authUser.uid)
                    .child("profile_${timestamp}.jpg")

                // Upload with progress tracking
                var uploadTask = storageRef.putFile(processedUri)
                uploadTask = uploadTask.addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                    _uploadProgress.value = progress.toFloat() / 100f
                } as UploadTask

                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val currentUser = _userProfile.value ?: throw IllegalStateException("No user profile loaded")
                val updatedUser = currentUser.copy(profilePictureUrl = downloadUrl)

                withContext(Dispatchers.IO) {
                    dataManager.updateUserProfile(updatedUser)
                }

                _userProfile.value = updatedUser
                Log.d("ProfileViewModel", "Profile picture updated successfully: $downloadUrl")

            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating profile picture", e)
                _error.value = when {
                    e is IllegalStateException -> e.message
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "Network error. Please check your connection."
                    e.message?.contains("permission", ignoreCase = true) == true ->
                        "Permission denied. Please try again."
                    else -> "Failed to update profile picture: ${e.message}"
                }
            } finally {
                _isLoading.value = false
                _uploadProgress.value = 0f
                photoRepository.cleanupTempFiles() // Use photoRepository instead of imageHandler
            }
        }
    }

    fun cleanupTempFiles() {
        viewModelScope.launch {
            photoRepository.cleanupTempFiles()
        }
    }


    private fun setupUserProfileListener(userId: String) {
        viewModelScope.launch {
            try {
                dataManager.observeUserProfile(userId).collect { user ->
                    _userProfile.value = user
                    if (user != null) {
                        loadProfileById(user.id)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error setting up profile listener", e)
                _error.value = "Failed to load profile: ${e.message}"
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

                // Check if username is changing
                val currentUser = _userProfile.value
                if (currentUser?.username != user.username) {
                    // Check availability only if username is changing
                    if (!userRepository.isUsernameAvailable(user.username)) {
                        _error.value = "Username is already taken"
                        _isLoading.value = false
                        return@launch
                    }
                }

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
    fun isValidUsername(username: String): Boolean {
        // Username requirements:
        // 1. 3-30 characters
        // 2. Alphanumeric and underscores only
        // 3. No spaces
        val usernamePattern = "^[a-zA-Z0-9_]{3,30}$"
        return username.matches(usernamePattern.toRegex())
    }

    fun updateUsername(newUsername: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                if (!isValidUsername(newUsername)) {
                    _error.value = "Invalid username format. Use 3-30 characters, letters, numbers, and underscores only."
                    return@launch
                }

                val currentUser = _userProfile.value ?: throw IllegalStateException("No user profile loaded")

                if (currentUser.username == newUsername) {
                    return@launch // No change needed
                }

                if (!userRepository.isUsernameAvailable(newUsername)) {
                    _error.value = "Username is already taken"
                    return@launch
                }

                // Update the username through AuthStateManager first
                authStateManager.updateUsername(newUsername)

                // Update local user profile
                val updatedUser = currentUser.copy(username = newUsername)
                withContext(Dispatchers.IO) {
                    dataManager.updateUserProfile(updatedUser)
                }
                _userProfile.value = updatedUser
                _username.value = newUsername

            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating username", e)
                _error.value = "Failed to update username: ${e.message}"
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
                    isSpayedNeutered = dogData["isSpayedNeutered"]?.toBooleanStrictOrNull() ?: currentDog.isSpayedNeutered, // Fix here
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

    fun setError(message: String?) {
        viewModelScope.launch {
            _error.value = message
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
    }
