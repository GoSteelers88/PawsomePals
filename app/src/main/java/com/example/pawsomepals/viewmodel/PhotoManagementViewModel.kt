package com.example.pawsomepals.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pawsomepals.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoManagementViewModel @Inject constructor(
    private val photoRepository: PhotoRepository
) : ViewModel() {

    private val _photoState = MutableStateFlow<PhotoState>(PhotoState.Idle)
    val photoState: StateFlow<PhotoState> = _photoState

    fun uploadPhoto(uri: Uri, isUserPhoto: Boolean) {
        viewModelScope.launch {
            _photoState.value = PhotoState.Loading
            try {
                val url = photoRepository.uploadPhoto(uri, isUserPhoto)
                _photoState.value = PhotoState.Success("Photo uploaded successfully")
                // You might want to update the user or dog profile with the new photo URL here
            } catch (e: Exception) {
                _photoState.value = PhotoState.Error("Failed to upload photo: ${e.message}")
            }
        }
    }

    fun deletePhoto(photoUrl: String, isUserPhoto: Boolean) {
        viewModelScope.launch {
            _photoState.value = PhotoState.Loading
            try {
                photoRepository.deletePhoto(photoUrl, isUserPhoto)
                _photoState.value = PhotoState.Success("Photo deleted successfully")
                // You might want to update the user or dog profile to remove the deleted photo URL here
            } catch (e: Exception) {
                _photoState.value = PhotoState.Error("Failed to delete photo: ${e.message}")
            }
        }
    }

    fun resetState() {
        _photoState.value = PhotoState.Idle
    }

    sealed class PhotoState {
        object Idle : PhotoState()
        object Loading : PhotoState()
        data class Success(val message: String) : PhotoState()
        data class Error(val message: String) : PhotoState()
    }
}