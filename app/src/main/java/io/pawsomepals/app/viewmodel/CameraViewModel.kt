package io.pawsomepals.app.viewmodel

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.pawsomepals.app.data.repository.PhotoRepository
import io.pawsomepals.app.utils.CameraManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraManager: CameraManager,
    private val photoRepository: PhotoRepository
) : ViewModel() {

    sealed class PhotoResult {
        data class Success(val uri: Uri) : PhotoResult()
        data class Error(val message: String) : PhotoResult()
    }



    private val _photoResult = MutableStateFlow<PhotoResult?>(null)
    val photoResult = _photoResult.asStateFlow()
    val uploadProgress = photoRepository.uploadProgress

    fun initializeCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            cameraManager.initializeCamera(lifecycleOwner, previewView)
        }
    }

    fun capturePhoto() {
        viewModelScope.launch {
            try {
                val uri = cameraManager.capturePhoto()
                uri?.let {
                    val compressedUri = photoRepository.compressImage(it, 1024)
                    _photoResult.value = PhotoResult.Success(compressedUri)
                }
            } catch(e: Exception) {
                _photoResult.value = PhotoResult.Error(e.message ?: "Capture failed")
            }
        }
    }

    fun uploadPhoto(uri: Uri, isUserPhoto: Boolean, ownerId: String, index: Int = 0) =
        viewModelScope.launch {
            photoRepository.uploadPhoto(uri, isUserPhoto, ownerId, index)
        }

    override fun onCleared() {
        super.onCleared()
        cameraManager.cleanup()
        viewModelScope.launch {
            photoRepository.cleanupTempFiles()
        }
    }
}