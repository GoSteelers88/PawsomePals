package com.example.pawsomepals.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.repository.PhotoRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoManagementViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _photoState = MutableStateFlow<PhotoState>(PhotoState.Idle)
    val photoState: StateFlow<PhotoState> = _photoState

    private val _uploadProgress = MutableStateFlow<Float>(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress

    init {
        // Subscribe to upload progress
        viewModelScope.launch {
            photoRepository.uploadProgress.collect { progressMap ->
                // Take the average progress of all ongoing uploads
                val progress = if (progressMap.isNotEmpty()) {
                    progressMap.values.average().toFloat()
                } else {
                    0f
                }
                _uploadProgress.value = progress
            }
        }
    }

    fun uploadPhoto(uri: Uri, isUserPhoto: Boolean, customOwnerId: String? = null) {
        viewModelScope.launch {
            val ownerId = customOwnerId ?: auth.currentUser?.uid
            if (ownerId == null) {
                _photoState.value = PhotoState.Error("User not authenticated")
                return@launch
            }

            _photoState.value = PhotoState.Loading
            try {
                val url = photoRepository.uploadPhoto(uri, isUserPhoto, ownerId)
                _photoState.value = PhotoState.Success("Photo uploaded successfully")
            } catch (e: Exception) {
                _photoState.value = PhotoState.Error("Failed to upload photo: ${e.message}")
            }
        }
    }

    fun deletePhoto(photoUrl: String, isUserPhoto: Boolean, customOwnerId: String? = null) {
        viewModelScope.launch {
            val ownerId = customOwnerId ?: auth.currentUser?.uid
            if (ownerId == null) {
                _photoState.value = PhotoState.Error("User not authenticated")
                return@launch
            }

            _photoState.value = PhotoState.Loading
            try {
                photoRepository.deletePhoto(photoUrl, isUserPhoto, ownerId)
                _photoState.value = PhotoState.Success("Photo deleted successfully")
            } catch (e: Exception) {
                _photoState.value = PhotoState.Error("Failed to delete photo: ${e.message}")
            }
        }
    }

    fun resetState() {
        _photoState.value = PhotoState.Idle
        _uploadProgress.value = 0f
    }

    sealed class PhotoState {
        object Idle : PhotoState()
        object Loading : PhotoState()
        data class Success(val message: String) : PhotoState()
        data class Error(val message: String) : PhotoState()
    }
}