package io.pawsomepals.app.ui.state

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.pawsomepals.app.utils.ImageHandler

class PhotoState(
    private val imageHandler: ImageHandler,
    private val onPhotoSelected: suspend (Uri) -> Unit
) {
    // Public read-only states
    private val _tempPhotoUri = mutableStateOf<Uri?>(null)
    val tempPhotoUri: Uri? get() = _tempPhotoUri.value

    private val _error = mutableStateOf<String?>(null)
    val error: String? get() = _error.value

    private val _isProcessing = mutableStateOf(false)
    val isProcessing: Boolean get() = _isProcessing.value

    suspend fun handlePhotoResult(uri: Uri?) {
        if (uri == null) return

        try {
            _isProcessing.value = true
            _error.value = null
            val processedUri = imageHandler.processImage(uri)
            onPhotoSelected(processedUri)
        } catch (e: Exception) {
            _error.value = "Failed to process photo: ${e.message}"
        } finally {
            _isProcessing.value = false
            _tempPhotoUri.value = null
        }
    }

    fun updateTempPhotoUri(uri: Uri?) {
        _tempPhotoUri.value = uri
    }

    fun clearError() {
        _error.value = null
    }

    fun cleanup() {
        _tempPhotoUri.value = null
        _error.value = null
        _isProcessing.value = false
    }
}

@Composable
fun rememberPhotoState(
    imageHandler: ImageHandler,
    onPhotoSelected: suspend (Uri) -> Unit
): PhotoState {
    return remember(imageHandler, onPhotoSelected) {
        PhotoState(imageHandler, onPhotoSelected)
    }
}

// Extension function to create a LoadingState
sealed class PhotoLoadingState {
    object Idle : PhotoLoadingState()
    object Loading : PhotoLoadingState()
    data class Error(val message: String) : PhotoLoadingState()
    data class Success(val uri: Uri) : PhotoLoadingState()
}

@Composable
fun rememberPhotoLoadingState(): MutableState<PhotoLoadingState> {
    return remember { mutableStateOf(PhotoLoadingState.Idle) }
}