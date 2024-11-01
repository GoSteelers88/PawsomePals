package com.example.pawsomepals.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pawsomepals.utils.ImageHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class DogPhotoHandler @Inject constructor(
    private val imageHandler: ImageHandler
) {
    private val _photoLoadingState = MutableStateFlow<PhotoLoadingState>(PhotoLoadingState.Idle)
    val photoLoadingState: StateFlow<PhotoLoadingState> = _photoLoadingState.asStateFlow()

    private val _selectedPhotoIndex = MutableStateFlow<Int?>(null)
    val selectedPhotoIndex: StateFlow<Int?> = _selectedPhotoIndex.asStateFlow()

    var previewPhotoUri by mutableStateOf<Uri?>(null)
        private set

    sealed class PhotoLoadingState {
        object Idle : PhotoLoadingState()
        data class Loading(val index: Int) : PhotoLoadingState()
        data class Error(val message: String) : PhotoLoadingState()
        object Success : PhotoLoadingState()
    }

    suspend fun handleNewPhoto(uri: Uri, index: Int) {
        try {
            _photoLoadingState.value = PhotoLoadingState.Loading(index)
            val processedUri = imageHandler.processImage(uri)
            previewPhotoUri = processedUri
            _selectedPhotoIndex.value = index
            _photoLoadingState.value = PhotoLoadingState.Success
        } catch (e: Exception) {
            _photoLoadingState.value = PhotoLoadingState.Error(e.message ?: "Failed to process photo")
        }
    }

    fun setPreviewPhoto(uri: Uri?) {
        previewPhotoUri = uri
    }

    fun clearPreviewPhoto() {
        previewPhotoUri = null
        _selectedPhotoIndex.value = null
        _photoLoadingState.value = PhotoLoadingState.Idle
    }
}