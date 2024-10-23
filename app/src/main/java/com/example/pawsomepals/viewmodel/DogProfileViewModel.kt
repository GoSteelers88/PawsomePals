package com.example.pawsomepals.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
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

@HiltViewModel
class DogProfileViewModel @Inject constructor(
    private val dogProfileRepository: DogProfileRepository,
    private val userRepository: UserRepository,
    private val photoRepository: PhotoRepository,
    private val firebaseAuth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val dataManager: DataManager
) : ViewModel() {

    private val _dogProfile = MutableStateFlow<Dog?>(null)
    val dogProfile: StateFlow<Dog?> = _dogProfile.asStateFlow()

    private val _userProfile = MutableStateFlow<FirebaseUser?>(null)
    val userProfile: StateFlow<FirebaseUser?> = _userProfile.asStateFlow()

    private val _dogProfileState = MutableStateFlow<DogProfileState>(DogProfileState.Initial)
    val dogProfileState: StateFlow<DogProfileState> = _dogProfileState


    fun createDogProfile(dog: Dog) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                val createdProfile = dogProfileRepository.createDogProfile(dog)
                _dogProfile.value = createdProfile
                _dogProfileState.value = DogProfileState.Success("Dog profile created successfully")
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Failed to create dog profile: ${e.message}")
            }
        }
    }


    fun updateDogProfilePicture(index: Int, uri: Uri) {
        viewModelScope.launch {
            try {
                val imageRef = storage.reference.child("dog_images/${UUID.randomUUID()}")
                val uploadTask = imageRef.putFile(uri).await()
                val downloadUrl = uploadTask.storage.downloadUrl.await().toString()

                _dogProfile.value?.let { currentDog ->
                    val updatedPhotoUrls = currentDog.photoUrls.toMutableList()
                    if (index < updatedPhotoUrls.size) {
                        updatedPhotoUrls[index] = downloadUrl
                    } else {
                        updatedPhotoUrls.add(downloadUrl)
                    }
                    val updatedDog = currentDog.copy(photoUrls = updatedPhotoUrls)
                    dogProfileRepository.updateDogProfile(updatedDog)
                    _dogProfile.value = updatedDog
                }
                _dogProfileState.value = DogProfileState.Success("Dog profile picture updated successfully")
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Error updating dog profile picture: ${e.message}")
            }
        }
    }

    fun fetchDogProfile(dogId: String) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                dogProfileRepository.getDogProfile(dogId).collect { profile ->
                    _dogProfile.value = profile
                    _dogProfileState.value = DogProfileState.Success("Dog profile fetched successfully")
                }
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Failed to fetch dog profile: ${e.message}")
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
                    _dogProfileState.value = DogProfileState.Success("Dog profile updated successfully")
                } catch (e: Exception) {
                    _dogProfileState.value = DogProfileState.Error("Failed to update dog profile: ${e.message}")
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
                _dogProfile.value = _dogProfile.value?.copy(latitude = latitude, longitude = longitude)
                _dogProfileState.value = DogProfileState.Success("Dog location updated successfully")
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Failed to update dog location: ${e.message}")
            }
        }
    }

    fun getDogProfilesByOwner(ownerId: String) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                val profiles = dogProfileRepository.getDogProfilesByOwner(ownerId)
                _dogProfileState.value = DogProfileState.Success("Fetched ${profiles.size} dog profiles")
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Failed to fetch dog profiles: ${e.message}")
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
                dogProfileRepository.getDogProfile(userId).collect { dogProfile ->
                    _dogProfile.value = dogProfile
                }
                _userProfile.value = firebaseAuth.currentUser
                _dogProfileState.value = DogProfileState.Success("Profiles loaded successfully")
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Failed to load profiles: ${e.message}")
            }
        }
    }

    fun searchDogProfiles(query: String) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                val profiles = dogProfileRepository.searchDogProfiles(query)
                _dogProfileState.value = DogProfileState.Success("Found ${profiles.size} matching dog profiles")
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Failed to search dog profiles: ${e.message}")
            }
        }
    }

    fun resetState() {
        _dogProfileState.value = DogProfileState.Initial
    }

    private fun getCurrentUserId(): String {
        return firebaseAuth.currentUser?.uid ?: throw IllegalStateException("No user logged in")
    }

    sealed class DogProfileState {
        object Initial : DogProfileState()
        object Loading : DogProfileState()
        data class Success(val message: String) : DogProfileState()
        data class Error(val message: String) : DogProfileState()
    }
}