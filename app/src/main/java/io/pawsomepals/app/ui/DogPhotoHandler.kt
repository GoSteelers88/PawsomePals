package io.pawsomepals.app.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.pawsomepals.app.data.repository.PhotoRepository
import io.pawsomepals.app.utils.CameraManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class DogPhotoHandler @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val cameraManager: CameraManager
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

    suspend fun handleNewPhoto(uri: Uri, dogId: String, index: Int) {
        try {
            _photoLoadingState.value = PhotoLoadingState.Loading(index)

            // Upload photo through repository
            val downloadUrl = photoRepository.uploadDogPhoto(
                dogId = dogId,
                photoUri = uri,
                photoIndex = index,
                onProgress = { progress ->
                    // Handle upload progress if needed
                }
            )

            // Set preview and update state
            previewPhotoUri = uri
            _selectedPhotoIndex.value = index
            _photoLoadingState.value = PhotoLoadingState.Success

        } catch (e: Exception) {
            _photoLoadingState.value = PhotoLoadingState.Error(e.message ?: "Failed to process photo")
        }
    }
    suspend fun takeNewPhoto(dogId: String, index: Int): Boolean {
        return try {
            val photoUri = cameraManager.capturePhoto()
            if (photoUri != null) {
                handleNewPhoto(photoUri, dogId, index)
                true
            } else {
                _photoLoadingState.value = PhotoLoadingState.Error("Failed to capture photo")
                false
            }
        } catch (e: Exception) {
            _photoLoadingState.value = PhotoLoadingState.Error(e.message ?: "Failed to capture photo")
            false
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
    fun cleanup() {
        cameraManager.cleanup()
        clearPreviewPhoto()
    }
}