package com.example.pawsomepals.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.model.DogProfile
import com.example.pawsomepals.data.repository.DogProfileRepository
import com.example.pawsomepals.data.repository.PhotoRepository
import com.example.pawsomepals.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@HiltViewModel
class DogProfileViewModel @Inject constructor(
    private val dogProfileRepository: DogProfileRepository,
    private val userRepository: UserRepository,
    private val photoRepository: PhotoRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _dogProfile = MutableStateFlow<DogProfile?>(null)
    val dogProfile: StateFlow<DogProfile?> = _dogProfile.asStateFlow()

    private val _userProfile = MutableStateFlow<com.google.firebase.firestore.auth.User?>(null)
    val userProfile: StateFlow<com.google.firebase.firestore.auth.User?> = _userProfile.asStateFlow()

    private val _dogProfileState = MutableStateFlow<DogProfileState>(DogProfileState.Initial)
    val dogProfileState: StateFlow<DogProfileState> = _dogProfileState

    private val _currentDogProfile = MutableStateFlow<DogProfile?>(null)
    val currentDogProfile: StateFlow<DogProfile?> = _currentDogProfile

    fun createDogProfile(dogProfile: DogProfile) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                val createdProfile = dogProfileRepository.createDogProfile(dogProfile)
                _currentDogProfile.value = createdProfile
                _dogProfileState.value = DogProfileState.Success("Dog profile created successfully")
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Failed to create dog profile: ${e.message}")
            }
        }
    }

    fun updateDogProfilePicture(uri: Uri) {
        viewModelScope.launch {
            try {
                val photoUrl = photoRepository.uploadPhoto(uri, isUserPhoto = false)
                _dogProfile.value?.let { currentProfile ->
                    val updatedProfile = currentProfile.copy(profilePictureUrl = photoUrl)
                    dogProfileRepository.updateDogProfile(updatedProfile)
                    _dogProfile.value = updatedProfile
                }
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Failed to update profile picture: ${e.message}")
            }
        }
    }

    fun fetchDogProfile(dogId: String) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                dogProfileRepository.getDogProfile(dogId).collect { profile ->
                    _currentDogProfile.value = profile
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
                "Spayed/Neutered" -> currentProfile.copy(isSpayedNeutered = value.toBoolean())
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
                } catch (e: Exception) {
                    _dogProfileState.value = DogProfileState.Error("Failed to update dog profile: ${e.message}")
                }
            }
        }
    }
    fun deleteDogProfile(dogId: String) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                dogProfileRepository.deleteDogProfile(dogId)
                _currentDogProfile.value = null
                _dogProfileState.value = DogProfileState.Success("Dog profile deleted successfully")
            } catch (e: Exception) {
                _dogProfileState.value = DogProfileState.Error("Failed to delete dog profile: ${e.message}")
            }
        }
    }

    fun updateDogLocation(dogId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _dogProfileState.value = DogProfileState.Loading
            try {
                dogProfileRepository.updateDogLocation(dogId, latitude, longitude)
                _currentDogProfile.value = _currentDogProfile.value?.copy(latitude = latitude, longitude = longitude)
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
                userRepository.getUserProfile(userId).collect { userProfile ->
                    _userProfile.value = userProfile as? com.google.firebase.firestore.auth.User
                }
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