package com.example.pawsomepals.utils

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class CameraPermissionState {
    var permissionGranted by mutableStateOf(false)
        private set

    var shouldShowRationale by mutableStateOf(false)
        private set

    fun handlePermissionResult(granted: Boolean, shouldShowRationale: Boolean = false) {
        permissionGranted = granted
        this.shouldShowRationale = shouldShowRationale
    }

    companion object {
        fun create(context: Context): CameraPermissionState {
            return CameraPermissionState().apply {
                handlePermissionResult(
                    granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
        }
    }
}

class CameraState(
    private val context: Context,
    private val imageHandler: ImageHandler,
    private val onPhotoTaken: suspend (Uri) -> Unit,
    private val isProfile: Boolean = false
) {
    private var currentPhotoUri: Uri? = null
    private var photoFile: File? = null

    suspend fun preparePhotoCapture(): Uri {
        val (file, uri) = imageHandler.createImageFile(isProfile)
        photoFile = file
        currentPhotoUri = uri
        return uri
    }

    suspend fun handlePhotoTaken(success: Boolean) {
        if (success && currentPhotoUri != null) {
            try {
                val processedUri = imageHandler.processImage(currentPhotoUri!!, isProfile)
                onPhotoTaken(processedUri)
            } catch (e: Exception) {
                Log.e("CameraState", "Error processing photo", e)
                throw e
            } finally {
                photoFile?.delete()
            }
        }
    }

    fun cleanup() {
        photoFile?.delete()
        currentPhotoUri = null
    }
}

@Composable
fun rememberCameraState(
    context: Context,
    imageHandler: ImageHandler,
    onPhotoTaken: suspend (Uri) -> Unit,
    isProfile: Boolean = false
): CameraState {
    val cameraState = remember(context, imageHandler, onPhotoTaken, isProfile) {
        CameraState(context, imageHandler, onPhotoTaken, isProfile)
    }

    DisposableEffect(cameraState) {
        onDispose {
            cameraState.cleanup()
        }
    }

    return cameraState
}

@Composable
fun rememberCameraPermissionState(context: Context): CameraPermissionState {
    return remember(context) {
        CameraPermissionState.create(context)
    }
}

@Composable
fun rememberCameraLaunchers(
    cameraState: CameraState,
    permissionState: CameraPermissionState,
    scope: CoroutineScope,
    onDenied: () -> Unit = {},
    onError: (String) -> Unit = {}
): Pair<ActivityResultLauncher<String>, ActivityResultLauncher<Uri>> {
    val context = LocalContext.current

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val shouldShowRationale = !isGranted && ActivityCompat.shouldShowRequestPermissionRationale(
            context as androidx.activity.ComponentActivity,
            Manifest.permission.CAMERA
        )
        permissionState.handlePermissionResult(isGranted, shouldShowRationale)
        if (!isGranted) {
            onDenied()
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        scope.launch {
            try {
                cameraState.handlePhotoTaken(success)
            } catch (e: Exception) {
                onError("Failed to process photo: ${e.message}")
            }
        }
    }

    return Pair(permissionLauncher, cameraLauncher)
}

@Composable
fun rememberGalleryLauncher(
    scope: CoroutineScope,
    imageHandler: ImageHandler,
    onImageSelected: suspend (Uri) -> Unit,
    isProfile: Boolean = false
): ActivityResultLauncher<String> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val processedUri = imageHandler.processImage(it, isProfile)
                    onImageSelected(processedUri)
                } catch (e: Exception) {
                    Log.e("GalleryLauncher", "Error processing image", e)
                }
            }
        }
    }
}