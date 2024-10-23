package com.example.pawsomepals.utils

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.saveable.rememberSaveable

class CameraState(
    private val context: Context,
    private val imageHandler: ImageHandler,
    val onPhotoTaken: (Uri) -> Unit
) {
    private var currentPhotoUri: Uri? = null

    fun getNewPhotoUri(): Uri {
        val (_, uri) = imageHandler.createImageFile()
        currentPhotoUri = uri
        return uri
    }

    fun handlePhotoTaken(success: Boolean) {
        if (success && currentPhotoUri != null) {
            onPhotoTaken(currentPhotoUri!!)
        }
    }
}

@Composable
fun rememberCameraState(
    context: Context,
    imageHandler: ImageHandler,
    onPhotoTaken: (Uri) -> Unit
): CameraState {
    return remember(context, imageHandler, onPhotoTaken) {
        CameraState(context, imageHandler, onPhotoTaken)
    }
}

class CameraPermissionState {
    var permissionGranted by mutableStateOf(false)
        private set

    fun handlePermissionResult(granted: Boolean) {
        permissionGranted = granted
    }
}

@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    return remember { CameraPermissionState() }
}

@Composable
fun rememberCameraLaunchers(
    cameraState: CameraState,
    permissionState: CameraPermissionState,
    onDenied: () -> Unit = {},
    onShowRationale: () -> Unit = {}
): Pair<ActivityResultLauncher<String>, ActivityResultLauncher<Uri>> {
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionState.handlePermissionResult(isGranted)
        when {
            isGranted -> { /* Permission granted, camera can be launched */ }
            else -> onDenied()
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        cameraState.handlePhotoTaken(success)
    }

    return Pair(permissionLauncher, cameraLauncher)
}

@Composable
fun rememberGalleryLauncher(onImageSelected: (Uri) -> Unit): ActivityResultLauncher<String> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onImageSelected(it) }
    }
}

//