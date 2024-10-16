package com.example.pawsomepals.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.data.model.User
import com.example.pawsomepals.data.repository.UserRepository
import com.example.pawsomepals.service.LocationService
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val locationService: LocationService
) : ViewModel() {
    private val _userProfile = MutableStateFlow<User?>(null)
    val userProfile: StateFlow<User?> = _userProfile

    private val _dogProfile = MutableStateFlow<DogProfile?>(null)
    val dogProfile: StateFlow<DogProfile?> = _dogProfile

    private val storage = FirebaseStorage.getInstance()


    fun loadProfileById(userId: String) {
        viewModelScope.launch {
            try {
                val user = userRepository.getUserById(userId)
                _userProfile.value = user
                user?.let {
                    _dogProfile.value = userRepository.getDogProfileByOwnerId(it.id)
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading profile by ID: ${e.message}")
            }
        }
    }

    fun loadProfiles() {
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                _userProfile.value = user
                user?.let {
                    _dogProfile.value = userRepository.getDogProfileByOwnerId(it.id)
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading profiles: ${e.message}")
            }
        }
    }

    fun createOrUpdateDogProfile(dogData: Map<String, String>) {
        viewModelScope.launch {
            try {
                val userId = _userProfile.value?.id ?: return@launch
                val existingDog = _dogProfile.value
                val currentLocation = locationService.getCurrentLocation()

                val updatedDog = existingDog?.copy(
                    name = dogData["name"] ?: existingDog.name,
                    breed = dogData["breed"] ?: existingDog.breed,
                    age = dogData["age"]?.toIntOrNull() ?: existingDog.age,
                    gender = dogData["gender"] ?: existingDog.gender,
                    size = dogData["size"] ?: existingDog.size,
                    energyLevel = dogData["energyLevel"] ?: existingDog.energyLevel,
                    friendliness = dogData["friendliness"] ?: existingDog.friendliness,
                    latitude = currentLocation?.latitude ?: existingDog.latitude,
                    longitude = currentLocation?.longitude ?: existingDog.longitude
                ) ?: DogProfile(
                    id = userRepository.generateDogId(),
                    ownerId = userId,
                    name = dogData["name"] ?: "",
                    breed = dogData["breed"] ?: "",
                    age = dogData["age"]?.toIntOrNull() ?: 0,
                    gender = dogData["gender"] ?: "",
                    size = dogData["size"] ?: "",
                    energyLevel = dogData["energyLevel"] ?: "",
                    friendliness = dogData["friendliness"] ?: "",
                    isSpayedNeutered = null,
                    friendlyWithDogs = null,
                    friendlyWithChildren = null,
                    specialNeeds = null,
                    favoriteToy = null,
                    preferredActivities = null,
                    walkFrequency = null,
                    favoriteTreat = null,
                    trainingCertifications = null,
                    bio = null,
                    profilePictureUrl = null,
                    latitude = currentLocation?.latitude,
                    longitude = currentLocation?.longitude
                )

                userRepository.createOrUpdateDogProfile(updatedDog)
                _dogProfile.value = updatedDog
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error creating/updating dog profile: ${e.message}")
            }
        }
    }

    fun updateUserProfile(userData: Map<String, String>) {
        viewModelScope.launch {
            try {
                _userProfile.value?.let { currentUser ->
                    val updatedUser = currentUser.copy(
                        username = userData["username"] ?: currentUser.username,
                        email = userData["email"] ?: currentUser.email,
                        firstName = userData["firstName"] ?: currentUser.firstName,
                        lastName = userData["lastName"] ?: currentUser.lastName,
                        bio = userData["bio"] ?: currentUser.bio
                    )
                    userRepository.updateUser(updatedUser)
                    _userProfile.value = updatedUser
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating user profile: ${e.message}")
            }
        }
    }

    fun updateDogProfile(updatedDog: DogProfile) {
        viewModelScope.launch {
            try {
                userRepository.updateDogProfile(updatedDog)
                _dogProfile.value = updatedDog
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating dog profile: ${e.message}")
            }
        }
    }

    fun updateDogProfilePicture(uri: Uri) {
        viewModelScope.launch {
            try {
                val imageRef = storage.reference.child("dog_images/${UUID.randomUUID()}")
                val uploadTask = imageRef.putFile(uri).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                _dogProfile.value?.let { currentDog ->
                    val updatedDog = currentDog.copy(profilePictureUrl = downloadUrl)
                    userRepository.updateDogProfile(updatedDog)
                    _dogProfile.value = updatedDog
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating dog profile picture: ${e.message}")
            }
        }
    }

    fun updateUserProfilePicture(uri: Uri) {
        viewModelScope.launch {
            try {
                val imageRef = storage.reference.child("user_images/${UUID.randomUUID()}")
                val uploadTask = imageRef.putFile(uri).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                _userProfile.value?.let { currentUser ->
                    val updatedUser = currentUser.copy(profilePictureUrl = downloadUrl)
                    userRepository.updateUser(updatedUser)
                    _userProfile.value = updatedUser
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error updating user profile picture: ${e.message}")
            }
        }
    }
    fun getOutputFileUri(context: Context): Uri {
        val imagePath = File(context.filesDir, "my_images")
        val newFile = File(imagePath, "default_image.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", newFile)
    }


    class Factory(
        private val userRepository: UserRepository,
        private val locationService: LocationService
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ProfileViewModel(userRepository, locationService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}