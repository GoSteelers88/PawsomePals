package com.example.pawsomepals.viewmodel

import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.DataManager
import com.example.pawsomepals.data.model.Dog
import com.example.pawsomepals.data.repository.DogProfileRepository
import com.example.pawsomepals.data.repository.PhotoRepository
import com.example.pawsomepals.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import com.example.pawsomepals.data.model.ResultWrapper
import com.example.pawsomepals.service.MatchingService
import kotlin.math.abs


@HiltViewModel
class DogProfileViewModel @Inject constructor(
    private val dogProfileRepository: DogProfileRepository,
    private val userRepository: UserRepository,
    private val photoRepository: PhotoRepository,
    private val firebaseAuth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val dataManager: DataManager,
    private val matchingService: MatchingService
) : ViewModel() {

    sealed class CompatibilityState {
        object Initial : CompatibilityState()
        object Loading : CompatibilityState()
        data class Compatible(val score: Double, val reasons: List<String>) : CompatibilityState()
        data class Incompatible(val reasons: List<String>) : CompatibilityState()
        data class Error(val message: String) : CompatibilityState()
    }

    sealed class DogProfileState {
        object Initial : DogProfileState()
        object Loading : DogProfileState()
        data class Success(val message: String) : DogProfileState()
        data class Error(val message: String) : DogProfileState()
    }

    // State Flows
    private val _compatibilityState = MutableStateFlow<CompatibilityState>(CompatibilityState.Initial)
    val compatibilityState: StateFlow<CompatibilityState> = _compatibilityState.asStateFlow()

    private val _currentDog = MutableStateFlow<Dog?>(null)
    val currentDog: StateFlow<Dog?> = _currentDog.asStateFlow()

    private val _userDogs = MutableStateFlow<List<Dog>>(emptyList())
    val userDogs: StateFlow<List<Dog>> = _userDogs.asStateFlow()

    private val _dogProfile = MutableStateFlow<Dog?>(null)
    val dogProfile: StateFlow<Dog?> = _dogProfile.asStateFlow()

    private val _userProfile = MutableStateFlow<FirebaseUser?>(null)
    val userProfile: StateFlow<FirebaseUser?> = _userProfile.asStateFlow()

    private val _dogProfileState = MutableStateFlow<DogProfileState>(DogProfileState.Initial)
    val dogProfileState: StateFlow<DogProfileState> = _dogProfileState.asStateFlow()

    // Helper Functions
    private fun getCompatibilityReasons(currentDog: Dog, otherDog: Dog): List<String> {
        val reasons = mutableListOf<String>()

        // Size compatibility
        if (currentDog.size == otherDog.size) {
            reasons.add("Similar size")
        }

        // Energy level
        if (currentDog.energyLevel == otherDog.energyLevel) {
            reasons.add("Matching energy levels")
        }

        // Age compatibility
        if (abs((currentDog.age ?: 0) - (otherDog.age ?: 0)) <= 2) {
            reasons.add("Close in age")
        }

        // Location compatibility
        if (currentDog.latitude != null && currentDog.longitude != null &&
            otherDog.latitude != null && otherDog.longitude != null) {
            val distance = calculateDistance(
                currentDog.latitude!!, currentDog.longitude!!,
                otherDog.latitude!!, otherDog.longitude!!
            )
            if (distance <= 10) { // Within 10km
                reasons.add("Nearby location")
            }
        }

        return reasons
    }
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371 // Earth's radius in kilometers
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }


    fun checkCompatibility(otherDogId: String) {
        viewModelScope.launch {
            _compatibilityState.value = CompatibilityState.Loading
            try {
                val currentDog =
                    currentDog.value ?: throw IllegalStateException("No current dog selected")
                dogProfileRepository.getDogProfile(otherDogId).collect { result ->
                    result.fold(
                        onSuccess = { otherDog ->
                            otherDog?.let { dog ->
                                val reasons = getCompatibilityReasons(currentDog, dog)
                                val score = matchingService.getCompatibilityScore(currentDog, dog)

                                if (score >= 0.7) {
                                    _compatibilityState.value =
                                        CompatibilityState.Compatible(score, reasons)
                                } else {
                                    _compatibilityState.value = CompatibilityState.Incompatible(
                                        listOf(
                                            "Different energy levels",
                                            "Age gap too large",
                                            "Size mismatch"
                                        )
                                            .filter { !reasons.contains(it) }
                                    )
                                }
                            }
                        },
                        onFailure = { e ->
                            _compatibilityState.value =
                                CompatibilityState.Error("Failed to check compatibility: ${e.message}")
                        }
                    )
                }
            } catch (e: Exception) {
                _compatibilityState.value =
                    CompatibilityState.Error("Error checking compatibility: ${e.message}")
            }
        }
    }


    fun createDogProfile(dog: Dog) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                val result = dogProfileRepository.createDogProfile(dog)
                result.fold(
                    onSuccess = { createdDog ->
                        _dogProfile.value = createdDog
                        _currentDog.value = createdDog
                        _dogProfileState.value =
                            DogProfileState.Success("Dog profile created successfully")
                    },
                    onFailure = { e ->
                        _dogProfileState.value =
                            DogProfileState.Error("Failed to create profile: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                _dogProfileState.value =
                    DogProfileState.Error("Error creating dog profile: ${e.message}")
            }
        }
    }


    fun setCurrentDog(dogId: String) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                Log.d("DogProfileViewModel", "Setting current dog with ID: $dogId")
                dogProfileRepository.getDogProfile(dogId).collect { result ->
                    result.fold(
                        onSuccess = { dog ->
                            _currentDog.value = dog
                            _dogProfile.value = dog
                            _dogProfileState.value =
                                DogProfileState.Success("Current dog set successfully: ${dog?.id}")
                        },
                        onFailure = { e ->
                            _dogProfileState.value =
                                DogProfileState.Error("Failed to set current dog: ${e.message}")
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("DogProfileViewModel", "Failed to set current dog: $dogId", e)
                _dogProfileState.value =
                    DogProfileState.Error("Failed to set current dog: ${e.message}")
            }
        }
    }

    fun loadUserDogs() {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                val userId = getCurrentUserId()
                Log.d("DogProfileViewModel", "Loading dogs for user: $userId")
                dogProfileRepository.getDogProfilesByOwner(userId).collect { result ->
                    result.fold(
                        onSuccess = { dogs ->
                            _userDogs.value = dogs
                            Log.d(
                                "DogProfileViewModel",
                                "Loaded ${dogs.size} dogs. IDs: ${dogs.map { it.id }}"
                            )
                            _dogProfileState.value =
                                DogProfileState.Success("User dogs loaded successfully")
                        },
                        onFailure = { e ->
                            Log.e("DogProfileViewModel", "Failed to load user dogs", e)
                            _dogProfileState.value =
                                DogProfileState.Error("Failed to load user dogs: ${e.message}")
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("DogProfileViewModel", "Failed to load user dogs", e)
                _dogProfileState.value =
                    DogProfileState.Error("Failed to load user dogs: ${e.message}")
            }
        }
    }


    fun fetchDogProfile(dogId: String) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                dogProfileRepository.getDogProfile(dogId).collect { result ->
                    result.fold(
                        onSuccess = { profile ->
                            _dogProfile.value = profile
                            _currentDog.value = profile
                            _dogProfileState.value =
                                DogProfileState.Success("Dog profile fetched successfully")
                        },
                        onFailure = { e ->
                            _dogProfileState.value =
                                DogProfileState.Error("Failed to fetch dog profile: ${e.message}")
                        }
                    )
                }
            } catch (e: Exception) {
                _dogProfileState.value =
                    DogProfileState.Error("Failed to fetch dog profile: ${e.message}")
            }
        }
    }

    fun searchDogProfiles(query: String) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                val result = dogProfileRepository.searchDogProfiles(query)
                result.fold(
                    onSuccess = { profiles ->
                        // Now 'profiles' is the List<Dog> directly, not wrapped in Result
                        val profileCount = profiles.size  // Now we can access size
                        _dogProfileState.value =
                            DogProfileState.Success("Found $profileCount matching dog profiles")
                    },
                    onFailure = { e ->
                        _dogProfileState.value =
                            DogProfileState.Error("Failed to search profiles: ${e.message}")
                    }
                )
            } catch (e: Exception) {
                _dogProfileState.value =
                    DogProfileState.Error("Failed to search dog profiles: ${e.message}")
            }
        }
    }


    fun updateDogProfile(field: String, value: String) {
        _dogProfile.value?.let { currentProfile ->
            val updatedProfile = when (field) {
                "Name" -> currentProfile.copy(name = value)
                "Breed" -> currentProfile.copy(breed = value)
                "Age" -> currentProfile.copy(age = value.toIntOrNull() ?: currentProfile.age)
                "Gender" -> currentProfile.copy(gender = value)
                "Size" -> currentProfile.copy(size = value)
                "Energy Level" -> currentProfile.copy(energyLevel = value)
                "Friendliness" -> currentProfile.copy(friendliness = value)
                "Spayed/Neutered" -> currentProfile.copy(isSpayedNeutered = value)
                "Friendly with dogs" -> currentProfile.copy(friendlyWithDogs = value)
                "Friendly with children" -> currentProfile.copy(friendlyWithChildren = value)
                "Special needs" -> currentProfile.copy(specialNeeds = value)
                "Favorite toy" -> currentProfile.copy(favoriteToy = value)
                "Preferred activities" -> currentProfile.copy(preferredActivities = value)
                "Walk frequency" -> currentProfile.copy(walkFrequency = value)
                "Favorite treat" -> currentProfile.copy(favoriteTreat = value)
                "Training certifications" -> currentProfile.copy(trainingCertifications = value)
                else -> currentProfile
            }
            viewModelScope.launch {
                try {
                    dogProfileRepository.updateDogProfile(updatedProfile)
                    _dogProfile.value = updatedProfile
                    _dogProfileState.value =
                        DogProfileState.Success("Dog profile updated successfully")
                } catch (e: Exception) {
                    _dogProfileState.value =
                        DogProfileState.Error("Failed to update dog profile: ${e.message}")
                }
            }
        }
    }

    fun deleteDogProfile(dogId: String) {
        viewModelScope.launch {
            try {
                dataManager.deleteDogProfile(dogId)
                // Additional logic after deleting profile
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun updateDogLocation(dogId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                dogProfileRepository.updateDogLocation(dogId, latitude, longitude)
                _dogProfile.value =
                    _dogProfile.value?.copy(latitude = latitude, longitude = longitude)
                _dogProfileState.value =
                    DogProfileState.Success("Dog location updated successfully")
            } catch (e: Exception) {
                _dogProfileState.value =
                    DogProfileState.Error("Failed to update dog location: ${e.message}")
            }
        }
    }

    fun getDogProfilesByOwner(ownerId: String) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                dogProfileRepository.getDogProfilesByOwner(ownerId).collect { result ->
                    result.fold(
                        onSuccess = { dogList ->
                            _dogProfileState.value =
                                DogProfileState.Success("Fetched ${dogList.size} dog profiles")
                        },
                        onFailure = { e ->
                            _dogProfileState.value =
                                DogProfileState.Error("Failed to fetch dog profiles: ${e.message}")
                        }
                    )
                }
            } catch (e: Exception) {
                _dogProfileState.value =
                    DogProfileState.Error("Failed to fetch dog profiles: ${e.message}")
            }
        }
    }

    fun getOutputFileUri(context: Context): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
        )
    }

    fun loadProfiles() {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserId()
                dogProfileRepository.getDogProfile(userId).collect { result ->
                    result.fold(
                        onSuccess = { dog ->
                            _dogProfile.value = dog  // This handles the type mismatch
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Error loading dog profile", e)
                        }
                    )
                }
                _userProfile.value = firebaseAuth.currentUser
                _dogProfileState.value = DogProfileState.Success("Profiles loaded successfully")
            } catch (e: Exception) {
                _dogProfileState.value =
                    DogProfileState.Error("Failed to load profiles: ${e.message}")
            }
        }
    }


    fun resetState() {
        _dogProfileState.value = DogProfileState.Initial
    }

    private fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid ?: throw IllegalStateException("No user logged in")
    }

    fun clearCurrentDog() {
        _currentDog.value = null
    }


}
